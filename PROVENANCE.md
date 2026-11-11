# 来源与贡献边界

本仓库从团队项目 [wt-06/poetry-platform](https://github.com/wt-06/poetry-platform) 提取小书童语音陪伴模块，由苗怡飞（myf8626-debug）维护，开发使用 AI 辅助。不是整个诗词平台的个人原创版本。

可核对的个人提交：
- [627b74c](https://github.com/wt-06/poetry-platform/commit/627b74c8899ea85e75f5506df6d2fc138c6274b4)：语音对话、生命周期处理、测试。
- [fb8900d](https://github.com/wt-06/poetry-platform/commit/fb8900d)：队友部署配置。
- [a9099f1](https://github.com/wt-06/poetry-platform/commit/a9099f1)：官方模型 API 配置。
- [27aac45](https://github.com/wt-06/poetry-platform/commit/27aac4556112ca38a4e29191e37f928ace2127a5)：角色拖动、动作交互。

提取源为本地 main（a9099f1）与已实现的交互工作区修改。新仓库采用全新历史，避免传播原有配置和用户数据。逐文件提取时指纹见 extraction-manifest.json；为独立运行新加最小页面、Vite 配置、Spring Boot 入口、环境变量配置和请求头适配。原平台的账号系统、游戏、内容资源、数据库和其他团队成员功能均不打包。

R / AssistantChatResponse / AssistantSource 等接口类型沿用团队代码，保留 com.shici 包名。原平台没有根目录开源许可证，不将团队代码重新声明为 MIT 或声称拥有全部版权。

`tools/voice-service/app` 来自 [travisvn/openai-edge-tts](https://github.com/travisvn/openai-edge-tts)，保留其 GPL-3.0 LICENSE 与上游 README；本地识别适配见 local/。模型推理由 sherpa-onnx 提供，SenseVoice 模型另行下载。该子目录许可不代表其他团队代码统一使用同一许可。再次分发需按各来源处理。

`docs/upstream-verification` 是原集成环境的历史验收，截图属于团队平台背景，不是本次独立入口的当前界面。新版本验证单独记录在 docs/verification.md。
