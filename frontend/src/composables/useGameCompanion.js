import { ref, onUnmounted } from 'vue';
import { chatWithGameCompanion, getCompanionVoiceStatus, speakCompanionText, transcribeCompanionAudio } from '@/utils/gameCompanionApi.js';
import { SpeechSegmenter } from '@/utils/companionAudio.js';

export function useGameCompanion() {
  const phase = ref('idle');
  const messages = ref([]);
  const error = ref('');
  const microphoneHelp = ref(false);
  const hint = ref('');
  const micActive = ref(false);
  const continuous = ref(true);
  const level = ref(0);
  const mouthOpen = ref(0);
  let speechAnalyser, mouthFrame = 0;
  let context, stream, input, recorder, silentGain, segmenter, output, playbackDone;
  let requestController, requestTimer, generation = 0, micStarting = false;

  async function audioContext() {
    const Context = window.AudioContext || window.webkitAudioContext;
    if (!Context) throw new Error('当前浏览器不支持音频，请使用新版 Edge 或 Chrome');
    if (!context || context.state === 'closed') context = new Context();
    if (context.state === 'suspended') await context.resume();
    return context;
  }

  function listening() {
    phase.value = micActive.value ? 'listening' : 'idle';
    segmenter?.reset();
    stream?.getAudioTracks().forEach(track => { track.enabled = micActive.value; });
    level.value = 0;
  }

  function muteMic() {
    stream?.getAudioTracks().forEach(track => { track.enabled = false; });
    segmenter?.reset();
    level.value = 0;
  }

  function releaseMic() {
    micActive.value = false;
    muteMic();
    if (recorder) recorder.port.onmessage = null;
    recorder?.disconnect(); input?.disconnect(); silentGain?.disconnect();
    stream?.getTracks().forEach(track => track.stop());
    stream = input = recorder = silentGain = segmenter = null;
  }

  function cancelTurn() {
    generation++;
    clearTimeout(requestTimer);
    requestController?.abort();
    requestController = null;
    if (output) { output.onended = null; try { output.stop(); } catch { /* Already ended. */ } output.disconnect(); output = null; }
    playbackDone?.(); playbackDone = null;
    stopMouthMeter();
  }

  function stopMouthMeter() {
    cancelAnimationFrame(mouthFrame); mouthFrame = 0;
    speechAnalyser?.disconnect(); speechAnalyser = null; mouthOpen.value = 0;
  }

  function startMouthMeter(ctx, source) {
    stopMouthMeter();
    speechAnalyser = ctx.createAnalyser(); speechAnalyser.fftSize = 512;
    source.connect(speechAnalyser); speechAnalyser.connect(ctx.destination);
    const samples = new Float32Array(speechAnalyser.fftSize);
    function tick() {
      if (!speechAnalyser || phase.value !== 'speaking') return;
      speechAnalyser.getFloatTimeDomainData(samples);
      let energy = 0;
      for (const sample of samples) energy += sample * sample;
      const target = Math.min(1, Math.max(0, (Math.sqrt(energy / samples.length) - .004) * 12));
      mouthOpen.value += (target - mouthOpen.value) * .45;
      mouthFrame = requestAnimationFrame(tick);
    }
    mouthFrame = requestAnimationFrame(tick);
  }

  function stopResponse() {
    cancelTurn();
    listening();
  }

  function end() {
    cancelTurn(); releaseMic();
    phase.value = 'idle';
    const previous = context;
    context = null;
    previous?.close().catch(() => {});
  }

  function newRequest() {
    requestController = new AbortController();
    requestTimer = setTimeout(() => requestController?.abort(), 80_000);
    return { signal: requestController.signal };
  }

  async function playReply(text, token, options) {
    phase.value = 'preparing';
    const blob = await speakCompanionText(text, options);
    if (token !== generation) return;
    const ctx = await audioContext();
    const buffer = await ctx.decodeAudioData(await blob.arrayBuffer());
    if (token !== generation) return;
    phase.value = 'speaking';
    await new Promise(resolve => {
      playbackDone = resolve;
      output = ctx.createBufferSource();
      output.buffer = buffer;
      startMouthMeter(ctx, output);
      output.onended = () => { output?.disconnect(); output = null; playbackDone = null; stopMouthMeter(); resolve(); };
      output.start();
    });
  }

  async function runTurn(text, wav) {
    cancelTurn();
    const token = generation;
    error.value = ''; hint.value = ''; muteMic();
    const options = newRequest();
    let hasReply = false;
    try {
      if (wav) {
        phase.value = 'recognizing';
        text = (await transcribeCompanionAudio(wav, options)).text.trim();
        if (token !== generation) return;
        if (!text) { hint.value = '没听清，请再说一句。'; return; }
      }
      if (text.length > 2000) throw new Error('这句话有些长，请分成两句再说');
      const history = messages.value.filter(item => item.role === 'user' || item.role === 'assistant').slice(-20);
      messages.value.push({ role: 'user', content: text });
      phase.value = 'thinking';
      const result = await chatWithGameCompanion(text, history, options);
      if (token !== generation) return;
      const reply = result.reply.slice(0, 2000);
      messages.value.push({ role: 'assistant', content: reply });
      if (messages.value.length > 40) messages.value.splice(0, messages.value.length - 40);
      hasReply = true;
      await playReply(reply, token, options);
      if (!continuous.value && micActive.value && token === generation) releaseMic();
    } catch (exception) {
      if (token === generation) {
        error.value = exception.name === 'AbortError' ? '等待超时，请重试。' : hasReply
          ? '朗读暂时不可用，回答已保留。可以继续输入文字。'
          : exception.message || '连接失败，请重试或输入文字。';
        releaseMic();
      }
    } finally {
      if (token === generation) {
        clearTimeout(requestTimer); requestController = null;
        listening();
      }
    }
  }

  async function sendText(text) {
    if (!text.trim()) return;
    const token = generation;
    try { await audioContext(); } catch (exception) { error.value = exception.message; return; }
    if (token !== generation) return;
    await runTurn(text.trim());
  }

  async function startMic() {
    if (micStarting || micActive.value) return;
    micStarting = true;
    error.value = ''; hint.value = ''; microphoneHelp.value = false;
    cancelTurn();
    const token = generation;
    phase.value = 'starting';
    try {
      if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) throw new Error('麦克风需要 localhost 或 HTTPS，请使用新版 Edge 或 Chrome');
      const policy = document.permissionsPolicy || document.featurePolicy;
      if (policy?.allowsFeature && !policy.allowsFeature('microphone')) {
        const blocked = new Error('当前嵌入页面未获准使用麦克风，请在系统浏览器中打开页面。');
        blocked.name = 'MicrophonePolicyError';
        throw blocked;
      }
      const ctx = await audioContext();
      const voiceStatus = await getCompanionVoiceStatus();
      if (!voiceStatus.asrReady) throw new Error('语音识别服务尚未就绪，请先使用文字');
      if (token !== generation) return;
      const acquired = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true, channelCount: 1 } });
      if (token !== generation) { acquired.getTracks().forEach(track => track.stop()); return; }
      stream = acquired;
      await ctx.audioWorklet.addModule(`${import.meta.env.BASE_URL}worklets/companion-recorder.js`);
      if (token !== generation) return;
      input = ctx.createMediaStreamSource(stream);
      recorder = new AudioWorkletNode(ctx, 'companion-recorder');
      silentGain = ctx.createGain(); silentGain.gain.value = 0;
      segmenter = new SpeechSegmenter(ctx.sampleRate, wav => {
        if (phase.value === 'listening') void runTurn('', wav);
      });
      recorder.port.onmessage = event => {
        if (phase.value === 'listening') level.value = Math.min(1, segmenter.push(event.data) * 12);
      };
      input.connect(recorder); recorder.connect(silentGain); silentGain.connect(ctx.destination);
      micActive.value = true;
      stream.getTracks().forEach(track => track.addEventListener('ended', () => {
        if (micActive.value) { end(); error.value = '麦克风已断开，请重新开始对话。'; }
      }));
      listening();
    } catch (exception) {
      if (token === generation) {
        releaseMic(); phase.value = 'idle';
        microphoneHelp.value = ['NotAllowedError', 'SecurityError', 'MicrophonePolicyError'].includes(exception.name)
          || !navigator.mediaDevices?.getUserMedia || !window.isSecureContext;
        error.value = exception.name === 'NotAllowedError' ? '麦克风权限未开启，当前浏览器阻止了访问。请按下方说明设置。'
          : exception.name === 'NotReadableError' ? '麦克风无法读取，请检查设备连接或其他程序是否正在占用。'
          : exception.name === 'NotFoundError' ? '没有找到麦克风，请接入后重试。'
          : exception.message || '麦克风无法启动，请先输入文字。';
      }
    } finally { micStarting = false; }
  }

  onUnmounted(end);
  return { phase, messages, error, hint, microphoneHelp, level, mouthOpen, micActive, continuous, startMic, sendText, stopResponse, end };
}
