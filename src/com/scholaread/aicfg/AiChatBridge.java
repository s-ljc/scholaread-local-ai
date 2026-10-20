package com.scholaread.aicfg;

import com.scholaread.model.api.AIChatRequestBody;

import io.reactivex.rxjava3.core.Observable;

import okhttp3.MediaType;
import okhttp3.ResponseBody;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Replaces ApiServer.createAIChat: routes the app's native AI chat to the
 * user's own OpenAI-compatible endpoint (config read from the shared cookie).
 * Returns an Observable&lt;ResponseBody&gt; whose body is fake SSE in the exact
 * format com.scholaread.aichat.c.fga parses (data:{...} lines, finish_reason ends).
 */
public class AiChatBridge {

    static final String COOKIE_HOST = "https://com.scholaread.pdf2html";
    static final String COOKIE_NAME = "scholaread_local_ai_cfg";

    // rolling per-document history: reading_id -> deque of {role, content}
    static final Map<String, ArrayDeque<String[]>> HISTORY =
            new HashMap<String, ArrayDeque<String[]>>();
    static final int MAX_TURNS = 12;

    public static Observable<ResponseBody> chat(final AIChatRequestBody body) {
        return Observable.fromCallable(new java.util.concurrent.Callable<ResponseBody>() {
            public ResponseBody call() throws Exception {
                android.util.Log.e("AiCfgPanel", "bridge chat: start");
                try {
                    String sse = run(body);
                    android.util.Log.e("AiCfgPanel", "bridge chat: ok sse=" + sse.length());
                    return ResponseBody.create(MediaType.parse("text/event-stream"), sse);
                } catch (Exception e) {
                    android.util.Log.e("AiCfgPanel", "bridge chat: FAIL " + e);
                    throw e;
                }
            }
        });
    }

    static String run(AIChatRequestBody body) throws Exception {
        String baseUrl = AiCfgPanel.DEF_BASE;
        String model = AiCfgPanel.DEF_MODEL;
        String apiKey = AiCfgPanel.DEF_KEY;
        String relay = AiCfgPanel.DEF_RELAY;
        try {
            String cookie = android.webkit.CookieManager.getInstance().getCookie(COOKIE_HOST);
            if (cookie != null) {
                int i = cookie.indexOf(COOKIE_NAME + "=");
                if (i >= 0) {
                    int s = i + COOKIE_NAME.length() + 1;
                    int e = cookie.indexOf(';', s);
                    String v = e < 0 ? cookie.substring(s) : cookie.substring(s, e);
                    JSONObject cfg = new JSONObject(java.net.URLDecoder.decode(v, "UTF-8"));
                    baseUrl = cfg.optString("baseUrl", baseUrl);
                    model = cfg.optString("model", model);
                    apiKey = cfg.optString("apiKey", apiKey);
                    relay = cfg.optString("relay", relay);
                }
            }
        } catch (Exception ignore) {
        }

        String key = body == null || body.reading_id == null ? "default" : body.reading_id;
        String user = body == null || body.content == null ? "" : body.content;

        JSONArray msgs = new JSONArray();
        JSONObject sys = new JSONObject();
        sys.put("role", "system");
        sys.put("content",
                "\u4f60\u662f Scholarad \u9605\u8bfb\u7c7b App \u5185\u7f6e\u7684 AI \u52a9\u624b\u3002"
                        + "\u82e5\u63d0\u4f9b\u4e86\u6587\u6863\u80cc\u666f\u6750\u6599\uff0c\u8bf7\u4f18\u5148\u57fa\u4e8e\u5b83\u4f5c\u7b54\uff1b"
                        + "\u7528\u6237\u63d0\u95ee\u7684\u8bed\u8a00\u4f5c\u7b54\uff0c\u7b80\u660e\u51c6\u786e\u3002");
        msgs.put(sys);

        // document contexts as background material
        StringBuilder ctx = new StringBuilder();
        try {
            if (body.qa_config != null && body.qa_config.contexts != null) {
                for (Object o : body.qa_config.contexts) {
                    try {
                        Object v = o.getClass().getField("content").get(o);
                        if (v != null) ctx.append(v.toString()).append("\n\n");
                    } catch (Exception ignore) {
                    }
                }
            }
        } catch (Exception ignore) {
        }
        if (ctx.length() > 0) {
            String c = ctx.toString();
            if (c.length() > 6000) c = c.substring(0, 6000);
            JSONObject m = new JSONObject();
            m.put("role", "user");
            m.put("content", "\u3010\u6587\u6863\u80cc\u666f\u6750\u6599\u3011\n" + c);
            msgs.put(m);
            JSONObject m2 = new JSONObject();
            m2.put("role", "assistant");
            m2.put("content", "\u597d\u7684\uff0c\u6211\u5df2\u4e86\u89e3\u4e0a\u8ff0\u6587\u6863\u5185\u5bb9\u3002");
            msgs.put(m2);
        }

        // rolling history
        ArrayDeque<String[]> hist;
        synchronized (HISTORY) {
            hist = HISTORY.get(key);
            if (hist == null) {
                hist = new ArrayDeque<String[]>();
                HISTORY.put(key, hist);
            }
            for (String[] t : hist) {
                JSONObject m = new JSONObject();
                m.put("role", t[0]);
                m.put("content", t[1]);
                msgs.put(m);
            }
        }
        JSONObject um = new JSONObject();
        um.put("role", "user");
        um.put("content", user);
        msgs.put(um);

        JSONObject req = new JSONObject();
        req.put("model", model);
        req.put("messages", msgs);
        req.put("stream", false);
        JSONObject kw = new JSONObject();
        kw.put("enable_thinking", false);
        req.put("chat_template_kwargs", kw);

        // Native HttpURLConnection has no CORS constraint: always talk to the
        // user's endpoint directly. The localhost relay is only needed by the
        // translate.js WebView (browser fetch), never by native code.
        String url = baseUrl + "/chat/completions";

        HttpURLConnection c = (HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(180000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (AiChatBridge)");
        c.setRequestProperty("ngrok-skip-browser-warning", "true");
        if (!apiKey.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + apiKey);
        byte[] payload = req.toString().getBytes("UTF-8");
        OutputStream os = c.getOutputStream();
        os.write(payload);
        os.close();

        int code = c.getResponseCode();
        java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
        }
        if (code != 200) {
            String b = sb.toString();
            throw new Exception("HTTP " + code + ": " + b.substring(0, Math.min(200, b.length())));
        }

        JSONObject resp = new JSONObject(sb.toString());
        String content = "";
        String reasoning = "";
        try {
            JSONObject msg = resp.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
            content = msg.optString("content", "");
            reasoning = msg.optString("reasoning_content", "");
        } catch (Exception e) {
            throw new Exception("\u54cd\u5e94\u683c\u5f0f\u5f02\u5e38: " + sb.substring(0, Math.min(200, sb.length())));
        }
        if (content.isEmpty()) {
            throw new Exception("AI \u8fd4\u56de\u7a7a\u5185\u5bb9");
        }

        synchronized (HISTORY) {
            hist.add(new String[]{"user", user});
            hist.add(new String[]{"assistant", content});
            while (hist.size() > MAX_TURNS * 2) {
                hist.poll();
            }
            // cap number of documents tracked
            if (HISTORY.size() > 30) {
                Iterator<String> it = HISTORY.keySet().iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
        }

        // build fake SSE stream (typewriter chunks)
        StringBuilder sse = new StringBuilder();
        try {
            if (!reasoning.isEmpty()) {
                for (int i = 0; i < reasoning.length(); i += 60) {
                    JSONObject d = new JSONObject();
                    d.put("reasoning_content", reasoning.substring(i, Math.min(reasoning.length(), i + 60)));
                    sse.append("data: ").append(d).append('\n');
                }
            }
            int step = 40;
            for (int i = 0; i < content.length(); i += step) {
                String piece = content.substring(i, Math.min(content.length(), i + step));
                JSONObject d = new JSONObject();
                d.put("content", piece);
                boolean last = i + step >= content.length();
                if (last) {
                    d.put("finish_reason", "stop");
                    JSONObject qa = new JSONObject();
                    String rid = "" + System.currentTimeMillis();
                    qa.put("q_msg_id", "local-q-" + rid);
                    qa.put("a_msg_id", "local-a-" + rid);
                    qa.put("session_id", key);
                    qa.put("suggestions", new JSONArray());
                    d.put("qa_data", qa);
                }
                sse.append("data: ").append(d).append('\n');
            }
        } catch (Exception e) {
            throw new Exception("SSE \u6784\u5efa\u5931\u8d25: " + e);
        }
        return sse.toString();
    }
}
