"""Create synthetic speech fixtures for tests. Requires the running voice service."""
from pathlib import Path
import subprocess
from dotenv import dotenv_values
import requests

output = Path(__file__).resolve().parents[3] / "artifacts/game-companion"
output.mkdir(parents=True, exist_ok=True)
token = dotenv_values(Path.home() / ".config/edge-tts-proxy/service.env")["API_KEY"]
client = requests.Session()
client.trust_env = False
questions = ["我叫小林，请记住我的名字。", "我叫什么名字？请简短回答。", "请读一句李白的诗，不超过二十字。"]
for index, text in enumerate(questions, 1):
    response = client.post("http://127.0.0.1:5050/v1/audio/speech", headers={"Authorization": f"Bearer {token}"},
                           json={"input": text}, timeout=60)
    response.raise_for_status()
    mp3 = output / f"input-{index}.mp3"
    mp3.write_bytes(response.content)
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(mp3),
                    "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le", "-af", "adelay=500,apad=pad_dur=2",
                    str(output / f"input-{index}.wav")], check=True)
response = client.post("http://127.0.0.1:5050/v1/audio/speech", headers={"Authorization": f"Bearer {token}"},
                       json={"input": "你叫小林。"}, timeout=60)
response.raise_for_status()
(output / "answer-2.mp3").write_bytes(response.content)
print("Synthetic Chinese speech fixtures prepared in artifacts/game-companion.")
