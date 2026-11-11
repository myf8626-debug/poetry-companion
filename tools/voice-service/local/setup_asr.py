"""Download the model from sherpa-onnx's official release; keep it outside the web root."""
from pathlib import Path
import tarfile
import urllib.request

STATE = Path.home() / ".config/poetry-companion/voice"
NAME = "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09"
URL = f"https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/{NAME}.tar.bz2"
STATE.mkdir(parents=True, exist_ok=True)
model_dir = STATE / "models" / NAME
if (model_dir / "model.int8.onnx").is_file() and (model_dir / "tokens.txt").is_file():
    print("Local SenseVoice model is already installed.")
    raise SystemExit(0)
archive = STATE / "sensevoice-int8.tar.bz2"
if not archive.exists():
    print("Downloading SenseVoice int8 (~158 MB) from sherpa-onnx releases...", flush=True)
    temporary = archive.with_suffix(".download")
    urllib.request.urlretrieve(URL, temporary)
    temporary.replace(archive)
with tarfile.open(archive, "r:bz2") as bundle:
    bundle.extractall(STATE / "models", filter="data")
assert (STATE / "models" / NAME / "model.int8.onnx").is_file()
assert (STATE / "models" / NAME / "tokens.txt").is_file()
print("Local SenseVoice model installed.")
