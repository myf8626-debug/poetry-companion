"""Local CPU SenseVoice recognition. Audio is decoded in memory, never saved."""
import io
from pathlib import Path
import re
from threading import Lock
import time
import wave

import numpy as np
import sherpa_onnx
from flask import jsonify, request
from utils import require_api_key

MODEL_DIR = Path.home() / ".config/poetry-companion/voice/models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09"

def install_asr(app):
    recognizer = sherpa_onnx.OfflineRecognizer.from_sense_voice(
        model=str(MODEL_DIR / "model.int8.onnx"),
        tokens=str(MODEL_DIR / "tokens.txt"),
        num_threads=2, use_itn=True, language="zh", provider="cpu",
    )
    lock = Lock()

    @app.post("/v1/audio/transcriptions")
    @require_api_key
    def transcribe():
        if request.mimetype != "audio/wav":
            return jsonify(error="Send mono 16-bit 16kHz WAV as audio/wav"), 415
        raw = request.get_data()
        if not raw or len(raw) > 1_000_000:
            return jsonify(error="Audio must be smaller than 1 MB"), 413
        try:
            with wave.open(io.BytesIO(raw), "rb") as audio:
                if (audio.getnchannels(), audio.getsampwidth(), audio.getframerate()) != (1, 2, 16000):
                    raise ValueError("Expected mono 16-bit 16kHz WAV")
                duration = audio.getnframes() / 16000
                if not 0.25 <= duration <= 30:
                    raise ValueError("Audio duration must be 0.25-30 seconds")
                frames = audio.readframes(audio.getnframes())
                if len(frames) != audio.getnframes() * 2:
                    raise ValueError("Truncated WAV")
        except (wave.Error, EOFError, ValueError):
            return jsonify(error="Invalid WAV: expected 0.25-30s mono 16-bit 16kHz audio"), 400
        if not lock.acquire(blocking=False):
            return jsonify(error="Speech recognizer is busy; retry shortly"), 429
        try:
            samples = np.frombuffer(frames, dtype="<i2").astype(np.float32) / 32768
            if float(np.sqrt(np.mean(samples ** 2))) < 0.003:
                return jsonify(text="", duration=duration, model="SenseVoiceSmall-int8")
            started = time.monotonic()
            stream = recognizer.create_stream()
            stream.accept_waveform(16000, samples)
            recognizer.decode_stream(stream)
            text = re.sub(r"<\|[^|]+\|>", "", stream.result.text).strip()
            return jsonify(text=text, duration=duration, model="SenseVoiceSmall-int8",
                           recognition_ms=round((time.monotonic() - started) * 1000))
        finally:
            lock.release()

    return {"model": "SenseVoiceSmall-int8", "ready": True, "local": True}
