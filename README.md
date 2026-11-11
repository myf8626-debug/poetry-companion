# 小书童 · 诗词语音陪伴

从团队诗词平台提取的个人贡献模块：让用户通过中文语音或文字与小书童对话，并处理停止回答、录音释放和角色交互。

**这是团队项目中的模块提取，不是整个平台的独立原创。** 原平台：[wt-06/poetry-platform](https://github.com/wt-06/poetry-platform)。本人负责语音对话与角色交互，开发使用 AI 辅助；提交与第三方来源见 [PROVENANCE.md](PROVENANCE.md)。

## 技术与流程

Vue 3 / Vite → Spring Boot 3 / Java 17 → 本地 SenseVoice（sherpa-onnx）识别、DeepSeek 回答、Edge TTS 朗读。SVG 角色支持拖动、动作切换和基于音频能量的嘴部动画。本模块不使用数据库，也没有带入原平台的账号、游戏和内容资源。

## 先跑界面与测试（不需要密钥）

依赖：Node.js 20+、Java 17+、Maven 3.8+。从仓库根目录执行：

```powershell
npm --prefix frontend ci
npm --prefix frontend test
npm --prefix frontend run build
mvn -f backend/pom.xml test package
```

分别在两个终端启动：

```powershell
# 终端 1：后台，仅监听本机 8086
java -jar backend/target/poetry-companion-0.1.0.jar

# 终端 2：前端，仅监听本机
npm --prefix frontend run dev
```

打开 Vite 输出的本机地址（通常是 `http://127.0.0.1:5173`）。可以打开小书童、拖动和切换动作；没有配置模型或语音服务时会显示真实错误，不会生成模拟答案。无数据库启动与这些界面交互已验证。

## 配置文字对话

在启动后台的终端设置环境变量 `DEEPSEEK_API_KEY`，不要写进源码。`DEEPSEEK_MODEL` 默认为 `deepseek-chat`，可按账号可用模型调整；`DEEPSEEK_URL` 默认为官方 chat/completions 地址。重启后台后才会读取新配置。

如果使用下方安装程序，可通过隐藏输入保存模型凭据，再执行 `start-backend.ps1` 加载用户目录里的配置。不要在工单、截图或聊天里发送真实密钥。

## 配置完整语音链路（Windows）

另外需要 Python 3.13+、ffmpeg（加入 PATH）、网络及可用模型额度。运行：

```powershell
# 建立虚拟环境、隐藏输入模型密钥、下载识别模型
powershell -ExecutionPolicy Bypass -File tools/voice-service/setup.ps1

# 终端 1：识别与朗读服务，监听本机 5050
powershell -ExecutionPolicy Bypass -File tools/voice-service/start-local.ps1

# 终端 2：读取私有配置后启动已构建的后台
powershell -ExecutionPolicy Bypass -File start-backend.ps1

# 终端 3：前端
npm --prefix frontend run dev
```

浏览器访问本机页面，允许麦克风权限，打开小书童并点击“开始语音”。录音在本机识别；识别后的文字发往模型服务，朗读文本发往 Edge 服务。语音服务和模型凭据保存在用户目录 `.config/poetry-companion/` 下，不在仓库中。

如果后台已从环境变量运行，可将 `VOICE_SERVICE_TOKEN` 设置为语音服务私有 `service.env` 中的同一令牌。不要同时启动两个后台占用 8086。

## 关键目录

- `frontend/src/components/game/`：对话面板与 SVG 小书童。
- `frontend/src/composables/`：语音状态、请求取消、麦克风生命周期。
- `backend/src/`：对话及语音转发接口、输入校验与测试。
- `tools/voice-service/`：带上游许可的 TTS 服务及本地识别适配。
- `docs/`：当前提取版验证、原集成环境历史验收。

## 已知限制

- 本机开发演示，未实现公网鉴权、限流与部署加固；不要直接暴露到公网。
- 半双工，朗读时暂停收音；不支持同时开口打断或流式音频播放。
- 没有接入实时关卡与分数，也没有检索证据链；引用准确性不能仅靠提示词保证。
- 本次提取版未重新进行付费模型、完整 ASR/TTS 或物理麦克风测试。原团队集成版的历史验证单独保存在 `docs/upstream-verification/`，不能等同于新版本完整复验。
- 不包含原平台的数据库、用户数据、密钥、模型权重、录音与旧 Git 历史。

## 来源与许可

个人贡献和团队代码边界见 [PROVENANCE.md](PROVENANCE.md)。`tools/voice-service` 保留其 GPL-3.0 许可；团队平台没有根目录许可证，本仓库不擅自将全部代码重新声明为 MIT。模型权重需自行从原提供者下载并遵守对应许可。
