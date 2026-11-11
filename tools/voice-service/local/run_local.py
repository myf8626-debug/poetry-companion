"""Loopback-only Windows launcher. Secrets live outside the web workspace."""
import os
from pathlib import Path
import sys

from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parents[1]
STATE = Path.home() / ".config" / "poetry-companion" / "voice"
load_dotenv(STATE / "service.env", override=True)
if not os.environ.get("API_KEY"):
    raise SystemExit("Local voice configuration is missing. Run tools/voice-service/setup.ps1 on this computer first.")
MODEL_DIR = STATE / "models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09"
if not all((MODEL_DIR / name).is_file() for name in ["model.int8.onnx", "tokens.txt"]):
    raise SystemExit("Local SenseVoice model is missing. Run tools/voice-service/setup.ps1 on this computer first.")
os.environ.update({
    "REQUIRE_API_KEY": "True",
    "DEFAULT_VOICE": "zh-CN-XiaoxiaoNeural",
    "DEFAULT_SPEED": "0.85",
    "EDGE_TTS_PITCH": "-8Hz",
    "EDGE_TTS_VOLUME": "-5%",
    "DEFAULT_LANGUAGE": "zh-CN",
    "DEFAULT_RESPONSE_FORMAT": "mp3",
    "DETAILED_ERROR_LOGGING": "False",
    "EXPAND_API": "False",
    "PORT": "5050",
})
sys.path.insert(0, str(ROOT / "app"))
from server import app
from flask import jsonify, request
from waitress import serve

app.config["MAX_CONTENT_LENGTH"] = 1_000_000
from asr import install_asr
asr_status = install_asr(app)

@app.before_request
def validate_speech():
    if request.path not in {"/v1/audio/speech", "/audio/speech"}:
        return None
    payload = request.get_json(silent=True)
    if not isinstance(payload, dict):
        return jsonify(error="Expected a JSON object"), 400
    text = payload.get("input")
    if not isinstance(text, str) or not text.strip() or len(text) > 4096:
        return jsonify(error="input must contain 1-4096 characters"), 400
    speed = payload.get("speed", 0.85)
    if isinstance(speed, bool) or not isinstance(speed, (int, float)) or not 0.25 <= speed <= 2.0:
        return jsonify(error="speed must be between 0.25 and 2.0"), 400
    if payload.get("response_format", "mp3") not in {"mp3", "wav", "flac", "opus"}:
        return jsonify(error="Supported formats: mp3, wav, flac, opus"), 400
    if payload.get("stream_format", "audio") not in {"audio", "sse"}:
        return jsonify(error="stream_format must be audio or sse"), 400
    if payload.get("stream_format") == "sse" and payload.get("response_format", "mp3") != "mp3":
        return jsonify(error="SSE requires mp3 format"), 400
    voice = payload.get("voice", "zh-CN-XiaoxiaoNeural")
    if not isinstance(voice, str) or not voice.strip() or len(voice) > 100:
        return jsonify(error="Invalid voice"), 400

@app.get("/health")
def health():
    return jsonify(status="ok", provider="edge-tts", asr=asr_status,
                   voice="zh-CN-XiaoxiaoNeural", speed=0.85)

if __name__ == "__main__":
    serve(app, host="127.0.0.1", port=5050, threads=4, max_request_body_size=1_000_000)
