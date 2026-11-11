"""Real upstream smoke checks; writes playable, non-sensitive samples."""
import base64
import json
from pathlib import Path
import subprocess
import time

from dotenv import dotenv_values
import requests

OUTPUT = Path(__file__).resolve().parents[3] / "artifacts/edge-tts-demo"
OUTPUT.mkdir(parents=True, exist_ok=True)
secret = dotenv_values(Path.home() / ".config/edge-tts-proxy/service.env")["API_KEY"]
client = requests.Session()
client.trust_env = False
url = "http://127.0.0.1:5050/v1/audio/speech"
headers = {"Authorization": f"Bearer {secret}"}
report = {"provider": "Microsoft Edge online TTS", "samples": []}

def probe(path):
    proc = subprocess.run(["ffprobe", "-v", "error", "-show_entries",
                           "format=duration:stream=codec_name,sample_rate,channels",
                           "-of", "json", str(path)], capture_output=True, text=True, check=True)
    metadata = json.loads(proc.stdout)
    assert float(metadata["format"]["duration"]) > 0.5
    return metadata

def main():
    assert client.post(url, json={"input": "测试"}, timeout=10).status_code == 401
    assert client.post(url, headers=headers, json={"input": ""}, timeout=10).status_code == 400
    assert client.post(url, headers=headers, json={"input": "测试", "speed": 9}, timeout=10).status_code == 400
    report["local_validation"] = "unauthorized=401; empty input=400; invalid speed=400"
    text = "你好呀，我是陪你游园的小书童。我们一起读诗、猜谜，慢慢来就好。床前明月光，疑是地上霜。"
    for name, voice in [("xiaoxiao", "zh-CN-XiaoxiaoNeural"), ("yunxi", "zh-CN-YunxiNeural")]:
        start = time.perf_counter()
        response = client.post(url, headers=headers, json={"model": "tts-1", "input": text,
                               "voice": voice, "response_format": "mp3", "speed": 1.0}, timeout=90)
        if response.status_code != 200:
            raise RuntimeError(f"{name}: HTTP {response.status_code}: {response.text[:400]}")
        assert response.headers.get("Content-Type", "").startswith("audio/mpeg")
        path = OUTPUT / f"{name}.mp3"
        path.write_bytes(response.content)
        item = {"voice": voice, "file": str(path), "bytes": len(response.content),
                "elapsed_seconds": round(time.perf_counter() - start, 3), "metadata": probe(path)}
        report["samples"].append(item)
        print(json.dumps(item, ensure_ascii=False), flush=True)
    start = time.perf_counter()
    chunks, first, done = [], None, False
    with client.post(url, headers=headers, json={"model": "tts-1", "input": text,
                     "voice": "zh-CN-YunxiaNeural", "stream_format": "sse"}, stream=True, timeout=90) as response:
        response.raise_for_status()
        for line in response.iter_lines(chunk_size=1):
            if not line.startswith(b"data: "):
                continue
            event = json.loads(line[6:])
            if event["type"] == "error":
                raise RuntimeError(f"SSE failed: {event['error']}")
            if event["type"] == "speech.audio.delta":
                if first is None:
                    first = round(time.perf_counter() - start, 3)
                chunks.append(base64.b64decode(event["audio"]))
            elif event["type"] == "speech.audio.done":
                done = True
    assert done and chunks
    path = OUTPUT / "yunxia-stream.mp3"
    path.write_bytes(b"".join(chunks))
    report["streaming"] = {"voice": "zh-CN-YunxiaNeural", "file": str(path),
                           "first_audio_seconds": first, "chunks": len(chunks),
                           "elapsed_seconds": round(time.perf_counter() - start, 3),
                           "completed": done, "metadata": probe(path)}
    (OUTPUT / "verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report["streaming"], ensure_ascii=False), flush=True)

if __name__ == "__main__":
    main()
