package com.scholaread.aicfg;

import android.webkit.JavascriptInterface;
import android.webkit.WebView;

/**
 * JS interface attached to the PDF translation WebView (PDFWidget, origin
 * https://com.scholaread.pdf2html) as window.__aiTranslate. translate.js
 * prefers __aiTranslate.aiFetchAsync over browser fetch/relay, so page
 * translation reaches the user's AI endpoint natively: no CORS, no localhost
 * relay, no USB. HTTP logic is shared with the research-page bridge.
 */
public class AiTranslateBridge {
    private final WebView w;

    public AiTranslateBridge(WebView w) {
        this.w = w;
    }

    @JavascriptInterface
    public void aiFetchAsync(final int id, final String url, final String body,
            final String auth) {
        AiAgentProbe.doFetch(w, id, url, body, auth);
    }
}
