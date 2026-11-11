<template>
  <Teleport to="body">
    <aside class="game-companion" :class="{ expanded: opened, dragging }" :style="positionStyle" aria-label="小书童语音陪伴">
      <Transition name="companion-panel">
        <section v-if="opened" id="game-companion-panel" ref="panel" class="companion-panel" :style="panelStyle" aria-label="和小书童对话">
          <header>
            <div><h2>小书童</h2><p>陪你游园，也陪你读诗</p></div>
            <button class="icon-button" type="button" aria-label="关闭对话" @click="closePanel"><svg viewBox="0 0 24 24"><path d="m6 6 12 12M6 18 18 6" /></svg></button>
          </header>
          <div class="companion-state" role="status" aria-live="polite"><span :class="{ live: micActive }"></span>{{ stateLabel }}</div>
          <div class="companion-actions" role="group" aria-label="小书童互动动作">
            <button v-for="action in avatarActions" :key="action.name" type="button" :aria-pressed="avatarAction.name === action.name" @click="playAction(action.name)">{{ action.label }}</button>
          </div>
          <p class="action-feedback" role="status" aria-live="polite">{{ actionFeedback }}</p>
          <div ref="conversation" class="companion-messages" role="log" aria-label="对话记录" aria-live="polite" aria-relevant="additions">
            <div v-if="!messages.length" class="companion-welcome"><p>想听一首诗，还是了解游戏玩法？</p><p>点“开始语音”，说完一句后，我会回答你。</p></div>
            <article v-for="(message, index) in messages" :key="index" :class="message.role">
              <span class="speaker">{{ message.role === 'user' ? '你' : '小书童' }}</span><p>{{ message.content }}</p>
            </article>
          </div>
          <p v-if="error" class="companion-error" role="alert">{{ error }}</p>
          <p v-else-if="hint" class="companion-hint">{{ hint }}</p>
          <div v-if="microphoneHelp && error" class="microphone-help">
            <p>在浏览器的网站权限中，将此页面的“麦克风”设为允许，再重试。</p>
            <p>内置预览没有授权入口时，请在系统 Edge 或 Chrome 中打开：</p>
            <div class="microphone-address"><input :value="pageAddress" readonly aria-label="语音对话页面地址" @focus="$event.target.select()" /><button type="button" @click="copyAddress">{{ copyLabel }}</button></div>
          </div>
          <div class="voice-controls">
            <button v-if="!micActive" class="primary-button" type="button" :disabled="busy || phase === 'starting'" @click="beginVoice">
              <svg viewBox="0 0 24 24"><rect x="9" y="3" width="6" height="12" rx="3" /><path d="M5 11a7 7 0 0 0 14 0M12 18v3M8 21h8" /></svg>{{ phase === 'starting' ? '正在开启…' : '开始语音' }}
            </button>
            <button v-else class="primary-button" type="button" @click="end">结束语音</button>
            <button class="secondary-button" type="button" :disabled="!busy" @click="stopResponse">停止回答</button>
          </div>
          <label class="continuous-choice"><input v-model="continuous" type="checkbox" />连续对话<span>回答后继续听你说</span></label>
          <form class="companion-input" @submit.prevent="submitText">
            <input v-model="draft" aria-label="给小书童发消息" maxlength="2000" placeholder="也可以输入文字…" :disabled="busy || phase === 'starting'" />
            <button type="submit" :disabled="!draft.trim() || busy || phase === 'starting'">发送</button>
          </form>
          <p class="companion-privacy">语音在本机识别；回答时暂停收音，关闭后停止录音。</p>
        </section>
      </Transition>
      <button ref="launcher" class="companion-launcher" type="button" :aria-expanded="opened" aria-controls="game-companion-panel" aria-describedby="companion-move-help" :aria-label="opened ? '收起小书童' : '打开小书童语音对话'" title="按住拖动，点击聊天；方向键移动，Home 归位" @click="activateLauncher" @pointerdown="startDrag" @pointermove="moveDrag" @pointerup="finishDrag" @pointercancel="finishDrag" @lostpointercapture="finishDrag" @keydown="moveWithKeyboard" @dragstart.prevent>
        <span v-if="!opened" class="launcher-note">拖动我 · 点击聊诗词</span>
        <span class="listening-ring" :style="{ opacity: phase === 'listening' ? 0.2 + level * .6 : 0 }"></span>
        <ScholarAvatar :state="phase" :mouth-open="mouthOpen" :engaged="opened" :action="avatarAction" @action-end="finishAction" />
        <span class="launcher-label">{{ opened ? '收起' : '小书童' }}</span>
      </button>
      <span id="companion-move-help" class="move-help">按住拖动小书童，点击打开对话。聚焦后可用方向键移动，Home 键回到右下角。</span>
    </aside>
  </Teleport>
</template>

<script setup>
import { computed, inject, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import ScholarAvatar from './ScholarAvatar.vue';
import { useGameCompanion } from '@/composables/useGameCompanion.js';

const route = useRoute();
const opened = ref(false);
const draft = ref('');
const conversation = ref(null);
const launcher = ref(null);
const panel = ref(null);
const position = ref(null);
const dragging = ref(false);
const panelStyle = ref({});
const avatarActions = [
  { name: 'wave', label: '挥手', feedback: '你好呀，来陪你读诗啦。' },
  { name: 'nod', label: '点头', feedback: '嗯嗯，我在认真听呢。' },
  { name: 'shake', label: '摇头', feedback: '轻轻摇摇头，再想一想。' },
  { name: 'happy', label: '开心', feedback: '和你一起读诗，真开心。' },
  { name: 'shy', label: '害羞', feedback: '被你发现，我有一点害羞。' },
];
const avatarAction = ref({ name: '', id: 0 });
const actionFeedback = computed(() => avatarActions.find(action => action.name === avatarAction.value.name)?.feedback || '点一个动作，看看我的回应');
function playAction(name) { avatarAction.value = { name, id: avatarAction.value.id + 1 }; }
function finishAction(id) { if (avatarAction.value.id === id) avatarAction.value = { name: '', id }; }
const positionStyle = computed(() => position.value ? { left: `${position.value.x}px`, top: `${position.value.y}px`, right: 'auto', bottom: 'auto' } : {});
let drag = null;
let suppressClickUntil = 0;
let resizeObserver;

function placeAt(x, y) {
  const rect = launcher.value?.getBoundingClientRect();
  if (!rect) return;
  position.value = {
    x: Math.max(8, Math.min(x, window.innerWidth - rect.width - 8)),
    y: Math.max(8, Math.min(y, window.innerHeight - rect.height - 8)),
  };
  nextTick(placePanel);
}
function placePanel() {
  if (!panel.value || !launcher.value) return;
  const anchor = launcher.value.getBoundingClientRect();
  const box = panel.value.getBoundingClientRect();
  const mobile = window.innerWidth <= 600;
  let x = anchor.left - box.width - 8;
  if (x < 12) x = anchor.right + 8;
  if (mobile) x = anchor.right - box.width;
  const y = mobile ? anchor.top - box.height - 8 : anchor.bottom - box.height;
  panelStyle.value = {
    left: `${Math.max(12, Math.min(x, window.innerWidth - box.width - 12))}px`,
    top: `${Math.max(12, Math.min(y, window.innerHeight - box.height - 12))}px`,
  };
}
function keepInView() {
  if (position.value) placeAt(position.value.x, position.value.y);
  else placePanel();
}
function startDrag(event) {
  if (!event.isPrimary || event.button !== 0 || drag) return;
  const box = launcher.value.getBoundingClientRect();
  drag = { id: event.pointerId, startX: event.clientX, startY: event.clientY, x: box.left, y: box.top, moved: false };
  launcher.value.setPointerCapture(event.pointerId);
}
function moveDrag(event) {
  if (!drag || event.pointerId !== drag.id) return;
  const dx = event.clientX - drag.startX;
  const dy = event.clientY - drag.startY;
  if (!drag.moved && Math.hypot(dx, dy) < 6) return;
  drag.moved = true;
  dragging.value = true;
  event.preventDefault();
  placeAt(drag.x + dx, drag.y + dy);
}
function finishDrag(event) {
  if (!drag || (event?.pointerId != null && event.pointerId !== drag.id)) return;
  const { id, moved } = drag;
  drag = null;
  dragging.value = false;
  if (moved) suppressClickUntil = performance.now() + 400;
  if (launcher.value?.hasPointerCapture(id)) launcher.value.releasePointerCapture(id);
}
function activateLauncher(event) {
  if (event.detail > 0 && performance.now() < suppressClickUntil) return;
  togglePanel();
}
function moveWithKeyboard(event) {
  if (event.key === 'Home') {
    event.preventDefault(); position.value = null; nextTick(placePanel); return;
  }
  const direction = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }[event.key];
  if (!direction) return;
  event.preventDefault();
  const box = launcher.value.getBoundingClientRect();
  const step = event.shiftKey ? 8 : 24;
  placeAt(box.left + direction[0] * step, box.top + direction[1] * step);
}
const music = inject('musicControl', null);
const { phase, messages, error, hint, microphoneHelp, level, mouthOpen, micActive, continuous, startMic, sendText, stopResponse, end } = useGameCompanion();
const pageAddress = computed(() => `${window.location.origin}${route.fullPath}`);
const copyLabel = ref('复制地址');
async function copyAddress() {
  try { await navigator.clipboard.writeText(pageAddress.value); copyLabel.value = '已复制'; }
  catch { copyLabel.value = '请选中复制'; }
}
const busy = computed(() => ['recognizing', 'thinking', 'preparing', 'speaking'].includes(phase.value));
const stateLabel = computed(() => ({ idle: '等你开口', starting: '正在开启麦克风', listening: '正在听，说完停顿片刻即可', recognizing: '正在辨认你说的话', thinking: '让我想一想', preparing: '准备为你朗读', speaking: '正在朗读' }[phase.value]));
function closePanel() { end(); opened.value = false; finishAction(avatarAction.value.id); nextTick(() => launcher.value?.focus()); }
function togglePanel() { if (opened.value) closePanel(); else opened.value = true; }
async function beginVoice() { music?.pause(); await startMic(); }
async function submitText() {
  if (busy.value || !draft.value.trim()) return;
  const text = draft.value; draft.value = '';
  music?.pause();
  await sendText(text);
}
watch(() => messages.value.length, async () => { await nextTick(); if (conversation.value) conversation.value.scrollTop = conversation.value.scrollHeight; });
watch(() => route.fullPath, () => { end(); opened.value = false; });
watch(panel, (element, previous) => {
  if (previous) resizeObserver?.unobserve(previous);
  if (element) { resizeObserver?.observe(element); nextTick(keepInView); }
  else nextTick(keepInView);
});
function pageHidden() { if (document.hidden) { finishDrag(); end(); } }
function escape(event) { if (event.key === 'Escape' && opened.value) closePanel(); }
onMounted(() => {
  resizeObserver = new ResizeObserver(keepInView);
  resizeObserver.observe(launcher.value);
  window.addEventListener('resize', keepInView);
  document.addEventListener('visibilitychange', pageHidden);
  window.addEventListener('pagehide', end);
  document.addEventListener('keydown', escape);
});
onUnmounted(() => {
  finishDrag();
  resizeObserver?.disconnect();
  window.removeEventListener('resize', keepInView);
  document.removeEventListener('visibilitychange', pageHidden);
  window.removeEventListener('pagehide', end);
  document.removeEventListener('keydown', escape);
});
</script>

<style scoped>
.game-companion { --sage: #8B9D83; --moss: #606C38; --sand: #E8DCC7; --oat: #D4B895; --clay: #B08B6E; --ink: #413C2C; position: fixed; right: 22px; bottom: 16px; z-index: 1400; display: flex; align-items: flex-end; gap: 8px; color: var(--ink); font-family: 'Noto Serif SC','Songti SC','SimSun',serif; pointer-events: none; }
.game-companion button,.game-companion input { font: inherit; }
.game-companion button { cursor: pointer; transition: background .3s ease, transform .3s ease; }
.game-companion button:disabled { cursor: default; opacity: .45; }
.game-companion button:focus-visible,.game-companion input:focus-visible { outline: 3px solid var(--sage); outline-offset: 3px; }
.companion-panel { width: 350px; max-height: calc(100dvh - 48px); display: flex; flex-direction: column; pointer-events: auto; border: 1px solid var(--clay); border-radius: 24px; padding: 22px 20px 14px; background-color: var(--sand); background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 160 160' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.8' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Cpath fill='%23606C38' opacity='.025' filter='url(%23n)' d='M0 0h160v160H0z'/%3E%3C/svg%3E"); box-shadow: 0 12px 40px #413c2c20; }
header { display: flex; align-items: flex-start; justify-content: space-between; }
h2 { font-size: 24px; margin: 0; font-weight: 600; letter-spacing: 3px; } header p { font-size: 12px; margin: 7px 0 0; color: #74664E; }
.icon-button { width: 30px; height: 30px; background: transparent; border: 0; padding: 5px; color: var(--ink); }
svg { width: 19px; height: 19px; fill: none; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; }
.companion-state { display: flex; gap: 8px; align-items: center; padding: 15px 0 12px; font-size: 12px; color: var(--moss); border-bottom: 1px solid #B08B6E50; }
.companion-state>span { width: 6px; height: 6px; border-radius: 50%; background: var(--clay); }.companion-state>span.live { background: var(--moss); }
.companion-messages { min-height: 130px; max-height: min(340px,38dvh); overflow-y: auto; overscroll-behavior: contain; padding: 12px 1px; scrollbar-width: thin; scrollbar-color: var(--clay) transparent; }
.companion-welcome { color: #74664E; padding: 8px 0; font-size: 13px; line-height: 1.8; }.companion-welcome p { margin: 0 0 8px; }
article { margin: 0 0 13px; padding: 11px 13px; border-radius: 16px; background: #8B9D8320; }.speaker { font-size: 10px; color: var(--moss); } article p { margin: 4px 0 0; font-size: 14px; line-height: 1.85; white-space: pre-wrap; overflow-wrap: anywhere; } article.user { background: #B08B6E20; margin-left: 24px; }
.companion-error,.companion-hint { font-size: 12px; line-height: 1.6; margin: 0 0 10px; }.companion-error { color: #934B30; }.companion-hint { color: var(--moss); }
.microphone-help { flex-shrink: 0; padding: 10px 12px; border: 1px solid #B08B6E80; border-radius: 16px; margin-bottom: 8px; font-size: 11px; line-height: 1.65; background: #D4B89540; }.microphone-help p { margin: 0 0 6px; }.microphone-address { display: flex; gap: 5px; }.microphone-address input { flex: 1; min-width: 0; border: 0; padding: 5px 0; color: var(--ink); background: transparent; font-family: sans-serif; font-size: 10px; }.microphone-address button { flex-shrink: 0; background: transparent; border: 1px solid var(--clay); color: var(--moss); border-radius: 12px; padding: 3px 6px; font-size: 10px; }
.voice-controls { display: flex; gap: 10px; padding-top: 10px; }.voice-controls button { min-height: 42px; padding: 9px 12px; border-radius: 16px; font-size: 13px; display: flex; align-items: center; justify-content: center; gap: 7px; }
.primary-button { flex: 1; background: var(--moss); color: var(--sand); border: 1px solid var(--moss); }.secondary-button { background: transparent; color: var(--ink); border: 1px solid var(--clay); }
.continuous-choice { margin: 13px 0; display: flex; align-items: center; gap: 5px; font-size: 12px; }.continuous-choice input { accent-color: var(--moss); }.continuous-choice span { color: #74664E; font-size: 10px; margin-left: auto; }
.companion-input { display: flex; border: 1px solid var(--clay); border-radius: 16px; min-height: 42px; overflow: hidden; }.companion-input input { min-width: 0; width: 100%; padding: 10px; background: transparent; border: 0; color: var(--ink); font-size: 13px; }.companion-input button { border: 0; border-left: 1px solid #B08B6E50; background: transparent; color: var(--moss); padding: 0 13px; white-space: nowrap; font-size: 12px; }
.companion-privacy { margin: 10px 0 0; font-size: 10px; line-height: 1.6; color: #74664E; }
.companion-launcher { position: relative; border: 0; padding: 0; background: transparent; pointer-events: auto; display: flex; flex-direction: column; align-items: center; color: var(--ink); }.launcher-label { background: var(--sand); border: 1px solid var(--clay); padding: 5px 16px; margin-top: -9px; border-radius: 16px; font-size: 12px; }.launcher-note { background: var(--sand); border: 1px solid var(--clay); border-radius: 16px; padding: 8px 12px; font-size: 12px; margin-bottom: -3px; }.listening-ring { position: absolute; inset: 25% 3% 10%; border-radius: 50%; background: var(--sage); transition: opacity .3s ease; z-index: -1; }
.companion-panel-enter-active,.companion-panel-leave-active { transition: opacity .3s ease, transform .3s ease; }.companion-panel-enter-from,.companion-panel-leave-to { opacity: 0; transform: translateY(12px); }
@media (max-width: 600px) { .game-companion { right: 10px; bottom: max(12px, env(safe-area-inset-bottom)); }.companion-panel { width: min(350px,calc(100vw - 26px)); max-height: calc(100dvh - 114px); overflow-y: auto; margin-bottom: 78px; padding: 18px 16px 12px; }.expanded .companion-launcher { position: absolute; right: 0; bottom: 0; }.expanded .companion-launcher :deep(.scholar) { width: 64px; height: 80px; }.expanded .launcher-label { display: none; }.companion-launcher :deep(.scholar) { width: 94px; height: 120px; } }
.companion-panel { position: fixed; margin: 0; }
.game-companion .companion-launcher { cursor: grab; touch-action: none; user-select: none; -webkit-user-select: none; }
.game-companion.dragging .companion-launcher { cursor: grabbing; }
.companion-actions { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 6px; padding-top: 12px; }
.companion-actions button { min-height: 44px; border: 1px solid var(--clay); border-radius: 14px; color: var(--moss); background: transparent; font-size: 12px; padding: 6px 2px; }
.companion-actions button:hover { background: #8B9D8320; }
.companion-actions button[aria-pressed="true"] { color: var(--sand); background: var(--moss); border-color: var(--moss); }
.action-feedback { margin: 7px 0 0; min-height: 18px; color: #74664E; font-size: 11px; line-height: 1.6; }
.move-help { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
@media (max-width: 600px) { .expanded .companion-launcher { position: relative; right: auto; bottom: auto; } }
@media (prefers-reduced-motion: reduce) { .game-companion * { transition: none !important; } }
</style>
