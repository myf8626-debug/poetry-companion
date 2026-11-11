import test from 'node:test';
import assert from 'node:assert/strict';
import { encodeSpeechWav, SpeechSegmenter } from '../src/utils/companionAudio.js';

test('WAV resamples hardware-rate PCM to bounded mono 16kHz without losing sample order', async () => {
  const samples = new Float32Array(48000).fill(0.5);
  const blob = encodeSpeechWav([samples.slice(0, 17000), samples.slice(17000)], 48000);
  const view = new DataView(await blob.arrayBuffer());
  assert.equal(view.getUint32(24, true), 16000);
  assert.equal(view.getUint16(22, true), 1);
  assert.equal(view.getUint16(34, true), 16);
  assert.equal(view.getUint32(40, true), 32000);
  assert.equal(view.byteLength, 32044);
  assert.equal(view.getInt16(44, true), 16383);
  assert.equal(view.getInt16(view.byteLength - 2, true), 16383);
});

test('silence and short clicks do not submit a recognition request', () => {
  const segments = [];
  const vad = new SpeechSegmenter(16000, blob => segments.push(blob));
  for (let i = 0; i < 500; i++) vad.push(new Float32Array(1024));
  vad.push(new Float32Array(1024).fill(0.2));
  for (let i = 0; i < 30; i++) vad.push(new Float32Array(1024));
  assert.equal(segments.length, 0);
});

test('one utterance submits once after trailing silence, then accepts another', () => {
  const segments = [];
  const vad = new SpeechSegmenter(16000, blob => segments.push(blob));
  for (let turn = 0; turn < 2; turn++) {
    for (let i = 0; i < 12; i++) vad.push(new Float32Array(1024).fill(0.1));
    for (let i = 0; i < 30; i++) vad.push(new Float32Array(1024));
  }
  assert.equal(segments.length, 2);
  assert.ok(segments[0].size < 100000);
});

test('a long utterance is capped below the server 30-second limit', () => {
  const segments = [];
  const vad = new SpeechSegmenter(16000, blob => segments.push(blob));
  for (let i = 0; i < 400; i++) vad.push(new Float32Array(1024).fill(0.1));
  assert.equal(segments.length, 1);
  assert.ok(segments[0].size <= 810000);
});
