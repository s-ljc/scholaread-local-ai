# ScholarRead Local AI（非官方补丁 / unofficial mod）

> **免责声明**：本项目是**非官方**开源补丁，与 ScholarRead 官方**无任何隶属、赞助或背书关系**。"ScholarRead" 名称与 logo 均为其所有者商标，本仓库仅作**指示性使用**（nominative use，说明补丁适用对象）。仓库只含自研代码与 diff 补丁，**不含官方 APK / dex / 资源 / 反编译代码**；请自行构建、**勿分发构建产物 APK**。
>
> **长期方向**：净室独立客户端 **PaperLocal**（纯客户端、不连官方服务器、不依赖任何官方代码）——本补丁版是通往它的过渡形态与协议资料库。

让 [ScholarRead](https://www.scholaread.cn/)（安卓 PDF 文献阅读器）里的所有 AI 功能——**文档对话、研究页 Agent、页面翻译**——改接你自己部署的模型（vLLM / Ollama / LM Studio / llama.cpp 等任何 OpenAI 兼容接口）。

官方 AI 按额度收费，本项目把它整个换成你自己的 API：一个 `baseUrl` + `apiKey` + `model` 就全部接管，App 内可视化配置、多接口管理、一键切换模型。

## 功能

| 功能 | 接管方式 | 需要电脑中继？ |
|---|---|---|
| 文档 AI 对话（阅读器 AI Chat 标签） | 原生替换官方 chat 接口，携带 App 的文档背景上下文 + 本地滚动 12 轮历史 | ❌ 原生 HTTP 直连 |
| 研究页 Agent（Research 标签） | 模拟官方 Agent WebSocket 协议（run.started / message_delta / run.completed…），流式输出、用户气泡、多轮上下文 | ❌ 原生桥直连（JS→native HTTP） |
| 页面翻译 | 替换 translate.js 的桥调用，优先走 `__aiTranslate` 原生桥直发你的接口 | ❌ 原生桥直连（中继仅作兜底） |
| 配置管理 | 设置页新增「自定义 AI 接口」行：增删改接口、拉取模型列表（同时测连接）、设为使用中；各页面右下角 🤖 chip 快速切模型 | — |

**手机和电脑在同一 Wi-Fi 时，直接填局域网地址（如 `http://192.168.1.10:8000/v1`）即可，不需要 ngrok、不需要电脑常驻任何进程。** 公网访问（手机用移动网络时）推荐 ngrok / cloudflare tunnel 把接口暴露成 https。三大 AI 功能全部走原生 HTTP（无 CORS 限制），**拔掉 USB 线照常工作**；`scripts/relay.mjs` 只是翻译的兜底通道，正常情况下不需要运行。

## 已知限制

- 官方 Agent 的**文献库工具调用**（如 "Check my library"）被本地模型替代，不会真的查询你的文献库；官方系统提示词与官方上下文管理同样被替换（内置中文系统提示词 + 滚动 12 轮）。
- 研究页对话历史不写入官方服务器（本地内存上下文，杀进程即清）；文档对话历史仍走官方存储。
- 界面里的 "credits" 数字是装饰性的。
- 补丁基于 **ScholarRead 1.9.17（build 944159）**，其他版本需重新定位补丁点。

## 构建

前置：JDK 17+、apktool 2.10+、Android build-tools（`d8.jar`）+ `android.jar`(API 34)、uber-apk-signer、Python 3、Git for Windows，以及你自己的原版 APK。

1. 编辑 [scripts/build_all.ps1](scripts/build_all.ps1) 顶部的路径变量（APK、各 jar、JDK）。
2. 运行：
   ```powershell
   .\scripts\build_all.ps1
   ```
3. 产物：`out\repacked-debug-signed.apk`。

只改 Java 源码（`src/`）想快速重编时，可用 [scripts/build_dex.ps1](scripts/build_dex.ps1)（对已打过补丁的 APK 重新生成 classes5.dex 并签名）。

## 安装

调试签名与官方签名不同，**首次安装需先卸载官方版**（本地登录与缓存会丢，重新登录即可；之后 `adb install -r` 升级不丢数据）。

```powershell
adb install -r out\repacked-debug-signed.apk
```

## App 内配置 AI

1. 打开 ScholarRead → **设置** → **自定义 AI 接口（本地 / 自定义 API）**。
2. 「+ 添加接口」：填名称、baseUrl（例 `http://192.168.1.10:8000/v1` 或 `https://xxx.ngrok-free.dev/v1`）、apiKey（本地服务可留空）、model。
3. 点「获取模型列表（同时测试连接）」验证连通并下拉选模型 → 保存 → 使用中。
4. 各页面右下角的 🤖 chip：文档对话/研究页**短按**弹出模型切换；翻译页 🤖 按钮可直接改 baseUrl/apiKey/model（存 cookie，重启不丢）。

接口要求：OpenAI 兼容 `POST {baseUrl}/chat/completions`（支持 `messages`、`stream:false` 即可）。若你的服务需要额外参数（如 `chat_template_kwargs`），在 [src/com/scholaread/aicfg/AiAgentProbe.java](src/com/scholaread/aicfg/AiAgentProbe.java) 的 `callAI` 请求体里加。

## 工作原理（简述）

- 新增 `classes5.dex`（本仓库 `src/` 编译产物），ART 原生加载，无需改 manifest。
- 5 处 smali 补丁挂接原生入口（chat 替换、chat 视图挂载、研究页 WebView 接管、设置页入口、阅读器 WebView 挂翻译桥），外加 1 个纯新增 smali 类；`assets/.../translate.js` 一处 JS 补丁。见 [patches/README.md](patches/README.md)。
- 研究页：注入 JS shim 伪造官方 Agent WebSocket 协议；AI 请求经 `__aiLocal.aiFetchAsync` 原生桥直发你的接口（无 CORS 限制）。协议细节见 [docs/PROTOCOL.md](docs/PROTOCOL.md)。

## 目录

```
src/        自研 Java（编成 classes5.dex）
stubs/      编译用官方类签名 stub（非官方代码）
patches/    smali / translate.js 补丁（git apply 格式）+ 新增 smali 类
scripts/    构建管线 + 可选 CORS 中继 (Node)
docs/       Agent WebSocket 协议分析
```

## 版权说明

- 本仓库只包含**自研代码与补丁**，不含 ScholarRead 的 APK、dex、资源或反编译全量代码；补丁文件按 mod 社区惯例以 diff 形式分发，请对**自己下载的原版 APK** 应用。
- 逆向分析出于互操作目的；ScholarRead 用户协议对使用者的约束（禁止再分发等）依然适用——**请自己构建 APK，不要分发构建产物**。
- 本项目与 ScholarRead 官方无关。若官方提出移除要求，照办即删。

## 许可

自研代码与脚本以 [MIT](LICENSE) 许可发布。
