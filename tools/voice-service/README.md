# 本地识别与语音合成服务

`app/` 基于 travisvn/openai-edge-tts，原 README 见 UPSTREAM_README.md，GPL-3.0 许可保留在 LICENSE。`local/` 为本地 SenseVoice 识别和 loopback 启动适配。

从本仓库根目录运行 `powershell -ExecutionPolicy Bypass -File tools/voice-service/setup.ps1` 安装；随后运行同目录的 `start-local.ps1`。前置 Python 3.13+、ffmpeg、网络。凭据与模型保存在用户目录 `.config/poetry-companion/voice/`，不会写入源码。

后台配置由根目录 `start-backend.ps1` 读取 `.config/poetry-companion/backend.properties`。识别在本机运行；Edge TTS 需要网络。详细启动、限制及本轮验证范围见根目录 README.md。

本次提取修改：私有配置隔离到 poetry-companion，删除旧代理模式入口，默认密钥清空。未重写上游 TTS 实现。
