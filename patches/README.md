# Patches

All patches target **ScholarRead 1.9.17 (build 944159)**, package `com.scholaread.cn`.
Apply them inside the apktool tree (`apktool d -r -f -o work original.apk`), from `work/`:

```sh
git apply -p1 ../patches/smali/AIChatDialogFragment.smali.patch
git apply -p1 ../patches/smali/c.smali.patch
git apply -p1 ../patches/smali/ChatStudySessionView.smali.patch
git apply -p1 ../patches/smali/SettingsActivity.smali.patch
git apply -p1 ../patches/translate.js.patch
cp ../patches/smali/AiCfgHook.smali smali_classes2/com/scholaread/aicfg/AiCfgHook.smali
```

`AiCfgHook.smali` is a brand-new class written by this project (settings-page
entry row); the four `.patch` files are minimal diffs against the original
smali, and `translate.js.patch` replaces the native bridge call in
`assets/pdf-static-res/js/translate.js` with a direct fetch to your endpoint.

| Patch | Hook | Purpose |
|---|---|---|
| `c.smali` | `aichat.c` (API layer) | replace official AI chat call with `AiChatBridge.chat()`; response logging hooks |
| `AIChatDialogFragment.smali` | chat dialog view | `AiCfgPanel.attachChat()` → 🤖 model chip in the input bar |
| `ChatStudySessionView.smali` | research-tab WebView | `AiAgentProbe.wrap()` → JS shim + fake agent WebSocket + native fetch bridge |
| `SettingsActivity.smali` | settings page | `AiCfgHook.attach()` → "自定义 AI 接口" entry row |
| `translate.js.patch` | PDF translation JS | direct OpenAI-compatible fetch, in-page 🤖 config button |

If a patch fails to apply on another app version, re-locate the hook: each
patch has 1–3 tiny hunks; search for the surrounding method signatures
(`onHandleTranslateRequest`, `createAIChat`, `setWebViewClient`, the settings
list builder) with apktool + grep.
