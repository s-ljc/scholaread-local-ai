"""ScholarRead 1.9.17 — translate.js local-AI patch builder.

Replaces the native bridge call in assets/pdf-static-res/js/translate.js with a
direct fetch() to a user-hosted OpenAI-compatible endpoint.

Evidence (REA/jadx):
  - JS bridge call site: translate.js line ~198 window.android?.onHandleTranslateRequest(...)
  - Callback contract: window[callbackName](base64(JSON)) with
      {status:'success', statusCode:200, data:{translation_text:{id,text,textArray,filtered}}}
  - WebView: origin https://com.scholaread.pdf2html, setMixedContentMode(2)=ALWAYS_ALLOW
    (PDFWidget.java:949-951) => http:// cleartext fetch allowed.
  - Manifest: usesCleartextTraffic=true.

Usage: python patch_translate_js.py [--base-url URL] [--model MODEL] [--api-key KEY]
"""
import argparse
import zipfile
import shutil
import os

SRC_APK = r'E:\Downloads\scholaread_944159.apk'
OUT_APK = r'C:\Users\MR\.dsh\ungrouped\scholaread_re\out\scholaread_944159_localai_unsigned.apk'
TARGET = 'assets/pdf-static-res/js/translate.js'

CONFIG_TEMPLATE = '''/* ============================================================
 * LOCAL AI PATCH (built by REA analysis, morluto/rea)
 * 翻译直连自定义 OpenAI 兼容服务；页面右下角 🤖 按钮可在
 * App 内修改 baseUrl / apiKey / model（存 cookie，重启不丢）。
 * ============================================================ */
const LOCAL_AI_DEFAULTS = {
    baseUrl: '__BASE_URL__',
    apiKey: '__API_KEY__',
    model: '__MODEL__',
    relay: '__RELAY__',
    temperature: 0.3,
    maxTokens: 4096
};
const AI_CFG_COOKIE = 'scholaread_local_ai_cfg';

function aiNormalizeBaseUrl(u) {
    u = String(u || '').trim().replace(/\\/+$/, '');
    u = u.replace(/\\/chat\\/completions$/, '');
    if (!/\\/v\\d+$/.test(u)) u += '/v1';
    return u;
}

function aiCfgGet() {
    try {
        const m = document.cookie.match(new RegExp('(?:^|;\\\\s*)' + AI_CFG_COOKIE + '=([^;]*)'));
        if (m && m[1]) {
            const saved = JSON.parse(decodeURIComponent(m[1]));
            return {
                baseUrl: saved.baseUrl || LOCAL_AI_DEFAULTS.baseUrl,
                apiKey: saved.apiKey != null ? saved.apiKey : LOCAL_AI_DEFAULTS.apiKey,
                model: saved.model || LOCAL_AI_DEFAULTS.model,
                relay: saved.relay != null ? saved.relay : LOCAL_AI_DEFAULTS.relay,
                temperature: LOCAL_AI_DEFAULTS.temperature,
                maxTokens: LOCAL_AI_DEFAULTS.maxTokens
            };
        }
    } catch (e) {}
    return Object.assign({}, LOCAL_AI_DEFAULTS);
}

function aiCfgSet(cfg) {
    try {
        document.cookie = AI_CFG_COOKIE + '=' + encodeURIComponent(JSON.stringify({
            baseUrl: cfg.baseUrl, apiKey: cfg.apiKey, model: cfg.model, relay: cfg.relay
        })) + '; path=/; max-age=31536000';
        return true;
    } catch (e) { return false; }
}

function aiFetchUrl(cfg) {
    const target = aiNormalizeBaseUrl(cfg.baseUrl) + '/chat/completions';
    const relay = String(cfg.relay || '').trim().replace(/\\/+$/, '');
    return relay ? relay + '/relay/' + encodeURIComponent(target) : target;
}

function localAiChatOne(text, targetLanguage) {
    const cfg = aiCfgGet();
    const sys = 'You are a professional academic translator. Translate the user text into '
        + targetLanguage
        + '. Preserve paragraph structure and any LaTeX/HTML markup. Output ONLY the translation, no explanations.';
    return fetch(aiFetchUrl(cfg), {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + cfg.apiKey
        },
        body: JSON.stringify({
            model: cfg.model,
            temperature: cfg.temperature,
            max_tokens: cfg.maxTokens,
            chat_template_kwargs: { enable_thinking: false },
            messages: [
                { role: 'system', content: sys },
                { role: 'user', content: text }
            ]
        })
    }).then(function (r) {
        if (!r.ok) throw new Error('HTTP ' + r.status + ' from local AI');
        return r.json();
    }).then(function (j) {
        const c = j && j.choices && j.choices[0] && j.choices[0].message && j.choices[0].message.content;
        if (!c) throw new Error('empty completion from local AI');
        return String(c).trim();
    });
}

function localAiTranslate(texts, targetLanguage) {
    return Promise.all(texts.map(function (t) { return localAiChatOne(t, targetLanguage); }));
}

/* ---------- test button (settings live in app Settings page) ---------- */
function aiToast(msg, ok) {
    let t = document.getElementById('local-ai-toast');
    if (!t) {
        t = document.createElement('div');
        t.id = 'local-ai-toast';
        t.style.cssText = 'position:fixed;left:50%;transform:translateX(-50%);bottom:140px;z-index:2147483600;max-width:80%;padding:10px 16px;border-radius:8px;font-size:14px;color:#fff;box-shadow:0 2px 10px rgba(0,0,0,.4);word-break:break-all;';
        document.body.appendChild(t);
    }
    t.style.background = ok ? 'rgba(20,120,60,.92)' : 'rgba(180,40,40,.92)';
    t.textContent = msg;
    t.style.display = 'block';
    clearTimeout(t._timer);
    t._timer = setTimeout(function () { t.style.display = 'none'; }, 4000);
}

function aiInjectGear() {
    if (!document.body) { document.addEventListener('DOMContentLoaded', aiInjectGear); return; }
    if (document.getElementById('local-ai-gear')) return;
    const btn = document.createElement('div');
    btn.id = 'local-ai-gear';
    btn.textContent = '\\ud83e\\udd16';
    btn.style.cssText = 'position:fixed;right:14px;bottom:90px;z-index:2147483400;width:42px;height:42px;line-height:42px;text-align:center;background:rgba(26,86,184,.8);color:#fff;border-radius:50%;font-size:22px;box-shadow:0 2px 8px rgba(0,0,0,.35);cursor:pointer;user-select:none;';
    btn.onclick = function () {
        aiToast('Testing connection...', true);
        localAiChatOne('Hello, are you working?', 'Simplified Chinese').then(function (c) {
            aiToast('AI connection OK: ' + c.slice(0, 60), true);
        }).catch(function (e) {
            aiToast('AI connection failed: ' + (e && e.message || e), false);
        });
    };
    document.body.appendChild(btn);
}
aiInjectGear();
/* ================= END LOCAL AI PATCH ================= */
'''

OLD_BLOCK = """                try {
                    // 调用原生方法，当前是否是重排预览模式、传递数据、回调函数名
                    window.android?.onHandleTranslateRequest(isReflowMode, base64PayloadToSend, callbackName);
                } catch (e) {
                    reject(new Error(JSON.stringify({ 'ids': ids, 'error': e, 'statusCode': 400 })));
                    delete window[callbackName];
                }"""

NEW_BLOCK = """                // ===== LOCAL AI PATCH: 直连本地 OpenAI 兼容服务 =====
                localAiTranslate(originText.texts, translateDataPayload.target_language).then(function (translatedArray) {
                    var payload = {
                        status: 'success',
                        statusCode: 200,
                        data: {
                            translation_text: {
                                id: originText.id,
                                text: translatedArray.join(''),
                                textArray: translatedArray,
                                filtered: false
                            }
                        }
                    };
                    window[callbackName](base64Encode(JSON.stringify(payload)));
                }).catch(function (e) {
                    console.error('local ai translate failed:', e);
                    window[callbackName](base64Encode(JSON.stringify({ status: 'error', statusCode: 500 })));
                });"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--base-url', default='http://192.168.1.100:8000/v1')
    ap.add_argument('--api-key', default='sk-local')
    ap.add_argument('--model', default='qwen2.5-7b-instruct')
    ap.add_argument('--relay', default='')
    args = ap.parse_args()

    zin = zipfile.ZipFile(SRC_APK, 'r')
    js = zin.read(TARGET).decode('utf-8')

    # sanity: original must contain the exact bridge block
    assert OLD_BLOCK in js, 'bridge block not found — APK version mismatch?'
    assert 'LOCAL AI PATCH' not in js, 'already patched'

    patched = (CONFIG_TEMPLATE
               .replace('__BASE_URL__', args.base_url)
               .replace('__API_KEY__', args.api_key)
               .replace('__MODEL__', args.model)
               .replace('__RELAY__', args.relay)) + js.replace(OLD_BLOCK, NEW_BLOCK, 1)

    os.makedirs(os.path.dirname(OUT_APK), exist_ok=True)
    if os.path.exists(OUT_APK):
        os.remove(OUT_APK)
    zout = zipfile.ZipFile(OUT_APK, 'w')
    replaced = 0
    for item in zin.infolist():
        data = zin.read(item.filename)
        if item.filename == TARGET:
            data = patched.encode('utf-8')
            replaced += 1
        # keep original compression type and metadata
        zi = zipfile.ZipInfo(item.filename, date_time=item.date_time)
        zi.compress_type = item.compress_type
        zi.external_attr = item.external_attr
        zi.internal_attr = item.internal_attr
        zi.create_system = item.create_system
        zout.writestr(zi, data)
    zout.close()
    zin.close()
    assert replaced == 1
    print('patched translate.js: %d -> %d bytes' % (len(js), len(patched)))
    print('unsigned APK written:', OUT_APK)
    print('next: java -jar tools/uber-apk-signer-1.3.0.jar -a "%s"' % OUT_APK)


if __name__ == '__main__':
    main()
