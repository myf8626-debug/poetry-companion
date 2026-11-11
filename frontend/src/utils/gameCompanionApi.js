import { getAuthHeaders } from './request.js';

async function companionRequest(path, options = {}) {
  const response = await fetch(`/api/game/companion${path}`, {
    credentials: 'include', ...options,
    headers: getAuthHeaders(options.headers || {}),
  });
  if (response.ok && response.headers.get('content-type')?.startsWith('audio/')) return response.blob();
  const result = await response.json();
  if (!response.ok || result.code !== 200) throw new Error(result.message || '小书童暂时无法连接');
  return result;
}

export function getGameCompanionStatus(options = {}) {
  return companionRequest('/status', options);
}

// history contains previous user/assistant messages, without the current message.
// Pass { signal } in options to stop waiting when the panel closes or the page changes.
export async function chatWithGameCompanion(message, history = [], options = {}) {
  const result = await companionRequest('/chat', {
    method: 'POST', body: JSON.stringify({ message, history: history.slice(-20) }), ...options,
  });
  if (result.code !== 200 || !result.data?.success) {
    throw new Error(result.data?.errorMsg || result.message || '小书童暂时无法回答');
  }
  return result.data;
}

export async function getCompanionVoiceStatus(options = {}) {
  return (await companionRequest('/voice/status', options)).data;
}

export async function transcribeCompanionAudio(wav, options = {}) {
  return (await companionRequest('/voice/transcribe', {
    method: 'POST', body: wav, ...options, headers: { 'Content-Type': 'audio/wav' },
  })).data;
}

export function speakCompanionText(text, options = {}) {
  return companionRequest('/voice/speech', {
    method: 'POST', body: JSON.stringify({ text }), ...options,
  });
}
