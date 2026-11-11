// Encode browser PCM as the local recognizer's mono, 16-bit, 16kHz WAV format.
export function encodeSpeechWav(chunks, sourceRate) {
  const length = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
  const samples = new Float32Array(length);
  let offset = 0;
  for (const chunk of chunks) { samples.set(chunk, offset); offset += chunk.length; }
  const rate = 16000;
  const count = Math.floor(length * rate / sourceRate);
  const buffer = new ArrayBuffer(44 + count * 2);
  const view = new DataView(buffer);
  const write = (start, value) => [...value].forEach((letter, i) => view.setUint8(start + i, letter.charCodeAt(0)));
  write(0, 'RIFF'); view.setUint32(4, 36 + count * 2, true); write(8, 'WAVE');
  write(12, 'fmt '); view.setUint32(16, 16, true); view.setUint16(20, 1, true);
  view.setUint16(22, 1, true); view.setUint32(24, rate, true); view.setUint32(28, rate * 2, true);
  view.setUint16(32, 2, true); view.setUint16(34, 16, true); write(36, 'data'); view.setUint32(40, count * 2, true);
  const ratio = sourceRate / rate;
  for (let i = 0; i < count; i++) {
    const start = Math.floor(i * ratio);
    const end = Math.max(start + 1, Math.min(length, Math.floor((i + 1) * ratio)));
    let sum = 0;
    for (let j = start; j < end; j++) sum += samples[j];
    const value = Math.max(-1, Math.min(1, sum / (end - start)));
    view.setInt16(44 + i * 2, value * (value < 0 ? 32768 : 32767), true);
  }
  return new Blob([buffer], { type: 'audio/wav' });
}

export class SpeechSegmenter {
  constructor(sampleRate, onSegment) {
    this.sampleRate = sampleRate;
    this.onSegment = onSegment;
    this.reset();
  }
  reset() { this.chunks = []; this.pre = []; this.length = 0; this.quiet = 0; this.voiced = 0; this.started = false; }
  push(samples) {
    const rms = Math.sqrt(samples.reduce((sum, value) => sum + value * value, 0) / samples.length);
    const voice = rms > 0.015;
    if (!this.started) {
      this.pre.push(samples);
      if (this.pre.length * samples.length > this.sampleRate * 0.25) this.pre.shift();
      if (!voice) return rms;
      this.started = true;
      this.chunks = [...this.pre];
      this.length = this.chunks.reduce((sum, chunk) => sum + chunk.length, 0);
    } else {
      this.chunks.push(samples);
      this.length += samples.length;
    }
    this.quiet = voice ? 0 : this.quiet + samples.length;
    if (voice) this.voiced += samples.length;
    if (this.quiet > this.sampleRate * 1.1 || this.length >= this.sampleRate * 25) {
      const chunks = this.chunks;
      const enough = this.voiced > this.sampleRate * 0.25;
      this.reset();
      if (enough) this.onSegment(encodeSpeechWav(chunks, this.sampleRate));
    }
    return rms;
  }
}
