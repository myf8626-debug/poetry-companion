<template>
  <svg ref="avatar" class="scholar" :class="[state, currentAction && `action-${currentAction}`, { greeting, 'reduce-motion': reducedMotion }]" :data-action="currentAction" viewBox="0 0 150 190" role="img" aria-label="会转头、眨眼和做互动动作的抱书小书童" @pointerenter="lookAtPointer" @pointerdown="greet">
    <ellipse cx="75" cy="180" rx="44" ry="6" fill="#606C38" opacity=".14" />
    <g :key="actionRun" class="scholar-body">
      <path d="M43 103Q75 88 108 104L123 163Q115 177 75 178Q35 177 27 163Z" fill="#8B9D83" stroke="#606C38" stroke-width="2" />
      <path d="M56 102L75 126L94 102L86 165H65Z" fill="#E8DCC7" />
      <path d="M53 107L75 126L97 107M75 127V165" fill="none" stroke="#606C38" stroke-width="2" />
      <g class="scholar-sleeves"><path d="M44 119Q26 122 25 147L49 156L59 134" fill="#8B9D83" stroke="#606C38" stroke-width="2" /><path v-if="currentAction !== 'wave'" d="M106 119Q124 124 125 147L101 156L92 134" fill="#8B9D83" stroke="#606C38" stroke-width="2" /></g>
      <g class="scholar-gesture">
      <g class="scholar-head" :transform="headTransform" :data-gaze-x="gaze.x.toFixed(2)" :data-gaze-y="gaze.y.toFixed(2)">
      <path d="M45 67Q40 28 75 25Q115 27 108 73L111 102L92 98H55L39 103Z" fill="#483C32" />
      <path d="M56 33Q43 18 55 12Q67 7 73 26M93 32Q108 20 98 13Q85 7 78 25" fill="#483C32" />
      <path d="M52 27L63 29M88 28L100 25" stroke="#B08B6E" stroke-width="5" stroke-linecap="round" />
      <ellipse cx="75" cy="70" rx="31" ry="35" fill="#E8C9AA" />
      <path d="M44 63Q46 31 75 33Q107 33 107 63Q84 58 72 43Q65 60 44 63" fill="#483C32" />
      <path class="eyebrows" :d="greeting ? 'M53 63Q59 59 65 63M85 63Q91 59 97 63' : 'M53 65Q59 62 65 65M85 65Q91 62 97 65'" fill="none" stroke="#483C32" stroke-width="1.5" stroke-linecap="round" />
      <g v-if="!['happy', 'shy'].includes(currentAction)" class="eyes" :class="{ blinking }">
        <ellipse cx="59" cy="73" rx="6.2" ry="5" fill="#E8DCC7" /><ellipse cx="91" cy="73" rx="6.2" ry="5" fill="#E8DCC7" />
        <g class="pupils" :transform="`translate(${gaze.x * 2.2} ${gaze.y * 1.4})`" fill="#483C32">
          <ellipse cx="59" cy="73" rx="2.8" ry="4" /><ellipse cx="91" cy="73" rx="2.8" ry="4" />
          <circle cx="58" cy="71.5" r=".9" fill="#E8DCC7" /><circle cx="90" cy="71.5" r=".9" fill="#E8DCC7" />
        </g>
        <path d="M53 70Q59 66 65 70M85 70Q91 66 97 70" fill="none" stroke="#483C32" stroke-width="1.8" stroke-linecap="round" />
      </g>
      <path v-else d="M53 74Q59 66 65 74M85 74Q91 66 97 74" fill="none" stroke="#483C32" stroke-width="2" stroke-linecap="round" />
      <g :opacity="currentAction === 'shy' ? .65 : .26"><ellipse cx="54" cy="81" rx="6" ry="3" fill="#C66B3D" /><ellipse cx="96" cy="81" rx="6" ry="3" fill="#C66B3D" /></g>
      <path v-show="mouthAmount < .06" class="mouth-rest" :d="greeting || currentAction === 'happy' ? 'M68 85Q75 94 82 85' : 'M70 86Q75 90 80 86'" fill="none" stroke="#A5614C" stroke-width="1.8" stroke-linecap="round" />
      <ellipse v-show="mouthAmount >= .06" class="mouth-speaking" cx="75" cy="87" :rx="2.5 + mouthAmount * 2.5" :ry="1 + mouthAmount * 4" fill="#784739" stroke="#A5614C" stroke-width="1.1" :data-openness="mouthAmount.toFixed(3)" />
      <path v-if="greeting" d="M107 66Q112 60 115 65Q118 70 110 75Q103 71 107 66" fill="#C66B3D" opacity=".7" />
      </g>
      </g>
      <path d="M44 126L73 132L104 123L105 158L75 168L44 159Z" fill="#D4B895" stroke="#606C38" stroke-width="2" />
      <path d="M74 133V165M49 133L68 137M49 139L68 143M81 136L99 131M81 142L99 137M81 148L99 143" stroke="#B08B6E" stroke-width="1.8" fill="none" />
      <ellipse cx="43" cy="148" rx="7" ry="10" fill="#E8C9AA" /><ellipse v-if="currentAction !== 'wave'" cx="106" cy="145" rx="7" ry="10" fill="#E8C9AA" />
      <g v-if="currentAction === 'wave'" class="waving-arm">
        <path d="M103 119Q117 123 123 105L128 90L114 85L103 105" fill="#8B9D83" stroke="#606C38" stroke-width="2" />
        <path d="M115 88L111 80Q110 77 113 77L117 81L115 70Q115 67 118 69L121 78L121 68Q122 65 124 69L125 79L128 72Q131 70 130 75L128 86Q124 94 115 88" fill="#E8C9AA" stroke="#B08B6E" stroke-width="1.2" stroke-linejoin="round" />
      </g>
      <g v-if="currentAction === 'happy'" class="happy-sparkles" fill="#B08B6E"><path d="m27 62 2-6 2 6 6 2-6 2-2 6-2-6-6-2Zm95-19 2-6 2 6 6 2-6 2-2 6-2-6-6-2Z" /></g>
    </g>
  </svg>
</template>
<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
const props = defineProps({ state: { type: String, default: 'idle' }, mouthOpen: { type: Number, default: 0 }, engaged: Boolean, action: { type: Object, default: () => ({ name: '', id: 0 }) } });
const emit = defineEmits(['action-end']);
const avatar = ref(null);
const gaze = reactive({ x: 0, y: 0 });
const blinking = ref(false);
const greeting = ref(false);
const reducedMotion = ref(false);
const currentAction = ref('');
const actionRun = ref(0);
const actionDurations = { wave: 1800, nod: 1500, shake: 1500, happy: 1800, shy: 2000 };
let actionTimer;
function finishAction() {
  clearTimeout(actionTimer);
  currentAction.value = '';
  emit('action-end', actionRun.value);
}
watch(() => props.action, action => {
  clearTimeout(actionTimer);
  if (!actionDurations[action.name]) { currentAction.value = ''; return; }
  greeting.value = false;
  clearTimeout(greetTimer);
  actionRun.value = action.id;
  currentAction.value = action.name;
  actionTimer = setTimeout(finishAction, actionDurations[action.name]);
});
const mouthAmount = computed(() => props.state === 'speaking' ? Math.min(1, Math.max(0, props.mouthOpen || 0)) : 0);
const headTransform = computed(() => {
  const tilt = ['thinking', 'recognizing', 'preparing'].includes(props.state) ? -5 : props.state === 'listening' ? 3 : 0;
  const nod = greeting.value && !reducedMotion.value ? 2 : 0;
  return `translate(${gaze.x * 2.5} ${gaze.y * 1.8 + nod}) rotate(${gaze.x * 7 + tilt} 75 103)`;
});
let target = { x: 0, y: 0 }, frame = 0, blinkTimer, blinkEndTimer, greetTimer, motionQuery;
function smoothLook() {
  frame = 0;
  if (reducedMotion.value || document.hidden) return;
  gaze.x += (target.x - gaze.x) * .12;
  gaze.y += (target.y - gaze.y) * .12;
  if (Math.abs(target.x - gaze.x) + Math.abs(target.y - gaze.y) > .005) frame = requestAnimationFrame(smoothLook);
}
function lookAtPointer(event) {
  if (reducedMotion.value || event.pointerType === 'touch' || !avatar.value) return;
  const box = avatar.value.getBoundingClientRect();
  target = { x: Math.max(-1, Math.min(1, (event.clientX - box.x - box.width / 2) / Math.max(140, box.width * 2))),
    y: Math.max(-1, Math.min(1, (event.clientY - box.y - box.height * .4) / Math.max(140, box.height * 2))) };
  if (!frame) frame = requestAnimationFrame(smoothLook);
}
function restLook() {
  target = { x: 0, y: 0 };
  if (!frame && !reducedMotion.value) frame = requestAnimationFrame(smoothLook);
}
function scheduleBlink() {
  clearTimeout(blinkTimer); clearTimeout(blinkEndTimer);
  if (reducedMotion.value || document.hidden) return;
  blinkTimer = setTimeout(() => {
    blinking.value = true;
    blinkEndTimer = setTimeout(() => { blinking.value = false; scheduleBlink(); }, 150);
  }, 2400 + Math.random() * 2800);
}
function greet() {
  greeting.value = true;
  clearTimeout(greetTimer);
  greetTimer = setTimeout(() => { greeting.value = false; }, 950);
}
function updateMotion() {
  reducedMotion.value = motionQuery.matches;
  cancelAnimationFrame(frame); frame = 0; gaze.x = 0; gaze.y = 0;
  blinking.value = false; scheduleBlink();
}
function visibilityChanged() {
  cancelAnimationFrame(frame); frame = 0; blinking.value = false;
  if (document.hidden) { clearTimeout(blinkTimer); clearTimeout(blinkEndTimer); finishAction(); }
  else { restLook(); scheduleBlink(); }
}
watch(() => props.engaged, value => { if (value) greet(); });
onMounted(() => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)'); updateMotion();
  motionQuery.addEventListener('change', updateMotion);
  window.addEventListener('pointermove', lookAtPointer, { passive: true });
  document.addEventListener('pointerleave', restLook);
  document.addEventListener('visibilitychange', visibilityChanged);
});
onUnmounted(() => {
  cancelAnimationFrame(frame); clearTimeout(blinkTimer); clearTimeout(blinkEndTimer); clearTimeout(greetTimer); clearTimeout(actionTimer);
  motionQuery?.removeEventListener('change', updateMotion);
  window.removeEventListener('pointermove', lookAtPointer);
  document.removeEventListener('pointerleave', restLook);
  document.removeEventListener('visibilitychange', visibilityChanged);
});
</script>
<style scoped>
.scholar { width: 144px; height: 183px; overflow: visible; }
.scholar-body { transform-origin: 75px 175px; animation: scholar-breathe 4s ease-in-out infinite; }
.listening .scholar-body { animation: scholar-listen 2s ease-in-out infinite; }
.scholar-head { transition: transform .08s linear; }
.eyes { transform-box: fill-box; transform-origin: center; transition: transform .08s ease; }
.eyes.blinking { transform: scaleY(.05); }
.scholar-sleeves { transform-origin: 75px 110px; animation: sleeve-sway 6s ease-in-out infinite; }
.greeting .scholar-body { animation: greeting-nod .7s ease-in-out; }
.scholar-gesture { transform-origin: 75px 103px; }
.action-nod .scholar-gesture { animation: action-nod .75s ease-in-out 2; }
.action-shake .scholar-gesture { animation: action-shake .75s ease-in-out 2; }
.action-happy .scholar-body { animation: action-hop .6s ease-in-out 3; }
.action-shy .scholar-gesture { animation: action-shy 2s ease-in-out; }
.waving-arm { transform-origin: 106px 119px; animation: action-wave .45s ease-in-out 4; }
.happy-sparkles { animation: action-sparkle .6s ease-in-out 3; }
.reduce-motion .scholar-body,.reduce-motion .scholar-sleeves { animation: none; }
.reduce-motion .scholar-gesture,.reduce-motion .waving-arm,.reduce-motion .happy-sparkles { animation: none; }
.reduce-motion .scholar-head,.reduce-motion .eyes { transition: none; }
@keyframes scholar-breathe { 50% { transform: translateY(-3px); } }
@keyframes scholar-listen { 50% { transform: rotate(3deg); } }
@keyframes sleeve-sway { 50% { transform: rotate(.8deg); } }
@keyframes greeting-nod { 35% { transform: translateY(2px) rotate(-2deg); } 70% { transform: translateY(-2px) rotate(1deg); } }
@keyframes action-nod { 40% { transform: translateY(5px) scaleY(.94); } 75% { transform: translateY(-1px); } }
@keyframes action-shake { 25% { transform: translateX(-4px) rotate(-9deg); } 75% { transform: translateX(4px) rotate(9deg); } }
@keyframes action-hop { 45% { transform: translateY(-9px) rotate(-2deg); } 75% { transform: translateY(1px) scaleY(.98); } }
@keyframes action-shy { 20%,80% { transform: translateY(3px) rotate(-10deg); } }
@keyframes action-wave { 25% { transform: rotate(-12deg); } 75% { transform: rotate(15deg); } }
@keyframes action-sparkle { 50% { opacity: .25; } }
@media (prefers-reduced-motion: reduce) { .scholar-body,.scholar-sleeves,.scholar-gesture,.waving-arm,.happy-sparkles { animation: none !important; }.scholar-head,.eyes { transition: none; } }
</style>
