# Research-tab Agent protocol (reverse-engineered)

The Research tab (`ChatStudySessionView`) embeds a WebView loading a local Vue
SPA: `file:///data/user/0/com.scholaread.cn/files/agent-assets/v1.0.7/index.html?study_id=...`.
The page reads `{base_url, token, device_id, account_state, ...}` via
`window.android.getHostConfig()`.

## Send flow

1. `POST {base}/agents/studies/{study_id}/sessions` — create session
2. `POST {base}/agents/ws_ticket` `{study_id, session_id}` → `{ws_ticket, routing_key}`
3. `new WebSocket({base}/agents/ws?study_id&session_id&ws_ticket&routing_key&language)`

## WebSocket event protocol

Client → server: `client.capabilities`, `run.start {payload.prompt}`, `run.cancel`.

Server → client (happy path):

```
server.hello {session_status:"ready"}
run.started  {run_id}
run.step_started {run_id, step_index}
run.reasoning_delta / run.message_delta {id, delta, run_id, step_index}   (many)
run.step_finished
run.summary_started
run.summary_completed
run.completed {messages:[{role:"assistant", parts:[{type:"text", text}]}]}
```

Failure: `run.failed {error:{message}}`.

There is **no user-message echo event**: after `run.completed` the page
re-renders the thread from the event's `messages` array, so a fake server must
inject the user message into `run.completed.messages` or the user bubble
disappears.

## Four traps (all hit during bring-up)

1. **SSE prefix** — the chat API parser (`aichat/c.smali`) requires lines to
   start with `data:␣` (colon **plus space**; obfuscated constant decoded from
   alternating XOR 0x4F/0x1F). Non-matching lines are silently skipped →
   "message sent but no reply".
2. **Native WS proxy** — the page bundle's `uae()` replaces `window.WebSocket`
   at module-eval time with a bridge-backed class
   (`window.webkit.messageHandlers.agentWebSocketProxy.postMessage`, events
   delivered to `window.__agentNativeWebSocketDispatch`). Setting
   `window.__AGENT_NATIVE_WS_PATCHED__=true` before module eval makes it skip
   the override, so our shim's replacement survives.
3. **Injection timing** — the bundle references globals at module-eval time;
   `onPageFinished` injection is too late. The shim must be injected into
   `<head>` by rewriting `index.html` in `shouldInterceptRequest` (no CSP).
4. **Fake WS prototype** — `WS.prototype = nativeWebSocket.prototype` makes
   `this.binaryType='blob'` hit the native brand-check setter →
   `TypeError: Illegal invocation`. Use a fresh empty object as prototype.

## Takeover design (`AiAgentProbe`)

- `shouldInterceptRequest`: fake `POST /agents/ws_ticket` (free accounts exhaust
  the 150 monthly credits and the official ticket flow fails outright; a forged
  ticket localizes everything), `OPTIONS /agents/*` → 204, agent-assets HTML →
  file read + shim prepended.
- SHIM: replaces `window.WebSocket`; `/agents/ws` URLs get a fake socket
  (open → `server.hello`; `run.start` → call the user's AI → chunk the answer
  into `reasoning_delta`/`message_delta` → `run.completed`), other URLs pass
  through to the real socket. Per-session history keeps the last 12 turns; the
  user message is echoed via `run.completed.messages`.
- AI transport: `__aiLocal.aiFetchAsync(id, url, body, auth)` — native
  `HttpURLConnection` on a worker thread, result delivered back via
  `evaluateJavascript(window.__aiFetchDone(id, env))`. No CORS, no localhost
  relay. (A relay fallback path remains for debugging.) The PDF reader WebView
  gets the same transport as `__aiTranslate.aiFetchAsync`
  (`AiTranslateBridge`, shared `AiAgentProbe.doFetch`), which translate.js
  prefers over browser fetch — translation is relay-free too.
- Model switching: `addJavascriptInterface(Bridge, "__aiLocal")`
  (`profiles` / `active` / `switchTo`, shared SharedPreferences + cookie with
  the native chat panel).
- Diagnostics: JS `dump()` POSTs to the optional relay's `/dump/<name>`
  (fetch/XHR quirks on `file://`, console doesn't reach logcat); native side
  logs every HTTP request + `onReceivedHttpError`.

## Chat quota

`AIChatDialogFragment`'s quota flag (`R:Z`) is written in three places
(constructor, `sda`, `nfa` history-load paths); all are patched to 0 in
`AIChatDialogFragment.smali.patch` to bypass the "credits exhausted" gate.
