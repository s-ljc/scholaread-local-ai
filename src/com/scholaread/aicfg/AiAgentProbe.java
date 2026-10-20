package com.scholaread.aicfg;

import android.graphics.Bitmap;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.net.URLDecoder;

/**
 * Hooks the chat-study (research tab) agent WebView:
 *  - injects window.__aiCfg (active local AI profile, refreshed every 2s)
 *  - wraps window.WebSocket: for /agents/ws URLs a fake socket emulates the
 *    official agent event protocol (server.hello / run.started /
 *    run.message_delta / run.completed ...) backed by the user's own AI.
 * Non-agent sockets pass through to the real WebSocket.
 */
public class AiAgentProbe {

    static final String TAG = "AiCfgPanel";

    /** Injected once per page; reads window.__aiCfg at send time (live switch). */
    static final String SHIM =
            "(function(){try{if(window.__aiLocalWS)return;window.__aiLocalWS=1;"
            + "window.__AGENT_NATIVE_WEBSOCKET_PATCHED__=true;"
            + "function dump(n,s){try{var c=window.__aiCfg||{};var x=new XMLHttpRequest();"
            + "x.open('POST',(c.relay||'http://127.0.0.1:8787')+'/dump/'+n,true);"
            + "x.setRequestHeader('Content-Type','text/plain');x.send(String(s));}catch(e){}}"
            + "window.__aiFid=0;window.__aiProm={};window.__aiFetchDone=function(id,env){var f=window.__aiProm[id];if(f){delete window.__aiProm[id];try{f(env);}catch(e){}}};"
            + "dump('agent_install','ok '+String(location.href).slice(0,90));"
            + "function chip(){try{if(!window.__aiLocal||document.getElementById('__aiChip')||!document.body)return;"
            + "var b=document.createElement('div');b.id='__aiChip';"
            + "b.style.cssText='position:fixed;right:10px;bottom:170px;z-index:99999;background:rgba(255,255,255,.94);color:#8a8f99;border:1px solid #e8e8ee;box-shadow:0 1px 3px rgba(0,0,0,.05);padding:3px 10px;border-radius:999px;font-size:11px;line-height:1.15;max-width:46%;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;-webkit-tap-highlight-color:transparent;';"
            + "function label(){var m=(window.__aiCfg&&window.__aiCfg.model)||'';b.textContent='\uD83E\uDD16 '+(m.length>18?m.slice(0,16)+'\u2026':(m||'AI'));}"
            + "label();setInterval(label,3000);"
            + "function rowRect(){var els=document.querySelectorAll('textarea,input,[contenteditable]');for(var i=0;i<els.length;i++){var el=els[i];var rr=el.getBoundingClientRect();if(rr.width<150||rr.height<16||rr.bottom<innerHeight*0.5)continue;var n=el;for(var k=0;k<6&&n.parentElement;k++){n=n.parentElement;var nr=n.getBoundingClientRect();if(nr.width>innerWidth*0.62&&nr.height>45){var bs=n.querySelectorAll('button'),row=[],btn=null;for(var m=0;m<bs.length;m++){var br=bs[m].getBoundingClientRect();if(br.width>=24&&br.width<=44&&br.height>=24&&br.height<=44&&br.bottom>nr.bottom-60&&br.top>nr.top+20){row.push(bs[m]);if(br.right>nr.right-70)btn=bs[m];}}return {bar:nr,btn:btn,els:row};}}return {bar:rr,btn:null,els:[]};}return null;}"
            + "function place(){var r=rowRect();if(r&&r.els.length){b.style.right='auto';b.style.bottom='auto';var w=b.offsetWidth,h=b.offsetHeight,i,n=r.els.length,ref=[],oth=[];for(i=0;i<n;i++){var q0=r.els[i].getBoundingClientRect();if(q0.left<r.bar.left+120)ref.push(r.els[i]);else oth.push(r.els[i]);}var arr=ref.length?ref:r.els,m=arr.length,avg=0;for(i=0;i<m;i++){var q3=arr[i].getBoundingClientRect();avg+=q3.top+q3.height/2;}avg/=m;for(i=0;i<oth.length;i++){var q4=oth[i].getBoundingClientRect(),d=avg-(q4.top+q4.height/2);oth[i].style.transform=Math.abs(d)>1?'translateY('+d.toFixed(1)+'px)':'';}var T=avg-h/2;b.style.left=Math.max(6,(r.btn?(r.btn.getBoundingClientRect().left-w-8):(r.bar.right-w-46)))+'px';b.style.top=T+'px';try{if(!window.__pd){window.__pd=1;var rr=[];for(i=0;i<ref.length;i++){var q6=ref[i].getBoundingClientRect();rr.push((q6.top+q6.height/2).toFixed(1));}var sd=r.btn?(function(q){return (q.top+q.height/2).toFixed(1);})(r.btn.getBoundingClientRect()):'nil';dump('place_diag','chipC='+(T+h/2).toFixed(1)+' avg='+avg.toFixed(1)+' ref=['+rr.join(',')+'] sendC='+sd+' barB='+r.bar.bottom.toFixed(1)+' vh='+innerHeight);}}catch(e){}}else if(r){b.style.right='auto';b.style.bottom='auto';b.style.left=Math.max(6,r.bar.right-b.offsetWidth-46)+'px';b.style.top=(r.bar.bottom-b.offsetHeight-12)+'px';}else{b.style.left='auto';b.style.top='auto';b.style.right='10px';b.style.bottom='170px';}}"
            + "setTimeout(place,60);setInterval(place,900);"
            + "b.onclick=function(){var ov=document.getElementById('__aiOv');if(ov){ov.remove();b.style.visibility='';return;}"
            + "ov=document.createElement('div');ov.id='__aiOv';ov.style.cssText='position:fixed;left:0;top:0;right:0;bottom:0;z-index:99998;background:rgba(0,0,0,.35);';"
            + "ov.addEventListener('click',function(e){if(e.target===ov){ov.remove();b.style.visibility='';}});"
            + "var sheet=document.createElement('div');sheet.style.cssText='position:fixed;left:0;right:0;bottom:0;background:#fff;border-radius:18px 18px 0 0;padding:16px 18px 30px;';"
            + "var t=document.createElement('div');t.textContent='\u9009\u62e9\u672c\u5730 AI';t.style.cssText='font-size:16px;font-weight:600;color:#222;margin-bottom:10px;';sheet.appendChild(t);"
            + "var act='';try{act=window.__aiLocal.active();}catch(e){}"
            + "var ps=[];try{ps=JSON.parse(window.__aiLocal.profiles());}catch(e){}"
            + "for(var i=0;i<ps.length;i++){(function(p){var r=document.createElement('div');"
            + "r.style.cssText='padding:12px 4px;border-bottom:1px solid #f2f2f5;font-size:14px;color:'+(p.name===act?'#3a5af5':'#333')+';';"
            + "r.textContent=(p.name===act?'\u2713 ':'')+p.name+'  \u00b7  '+p.model;"
            + "r.onclick=function(){try{var cfg=window.__aiLocal.switchTo(p.name);if(cfg){window.__aiCfg=JSON.parse(cfg);label();}}catch(e){}ov.remove();b.style.visibility='';};"
            + "sheet.appendChild(r);})(ps[i]);}"
            + "ov.appendChild(sheet);document.body.appendChild(ov);b.style.visibility='hidden';};"
            + "document.body.appendChild(b);}catch(e){}}"
            + "if(document.body)chip();else document.addEventListener('DOMContentLoaded',chip);"
            + "setTimeout(function(){try{"
            + "var out={vw:innerWidth,vh:innerHeight,z:[]};var all=document.querySelectorAll('textarea,input,button,div,span,img,svg');var cnt=0;"
            + "for(var i=0;i<all.length&&cnt<60;i++){var el=all[i];var r=el.getBoundingClientRect();if(r.width<14||r.height<8||r.top<innerHeight-180)continue;cnt++;out.z.push(el.tagName+(el.placeholder?('['+el.placeholder.slice(0,24)+']'):'')+':'+Math.round(r.x)+','+Math.round(r.y)+' '+Math.round(r.width)+'x'+Math.round(r.height));}"
            + "dump('dom_diag',JSON.stringify(out).slice(0,3000));}catch(e){dump('dom_diag_err',String(e).slice(0,120));}},2000);"
            + "setTimeout(function(){try{"
            + "var HC=window.android&&window.android.getHostConfig?String(window.android.getHostConfig()):'none';"
            + "dump('agent_cfg','HC='+HC.slice(0,900));"
            + "var XO=XMLHttpRequest.prototype.open,XS=XMLHttpRequest.prototype.send;"
            + "XMLHttpRequest.prototype.open=function(m,u){this.__u=String(u);return XO.apply(this,arguments);};"
            + "XMLHttpRequest.prototype.send=function(b){var s=this;s.addEventListener('readystatechange',function(){if(s.readyState===4&&/agents|api/.test(s.__u)){dump('agent_xhr','u='+s.__u.slice(0,150)+' st='+s.status+' r='+(s.responseText||'').slice(0,120));}});return XS.apply(this,arguments);};"
            + "if(window.fetch){var F=window.fetch;window.fetch=function(u,o){var us2=String(u&&u.url?u.url:u);return F.apply(this,arguments).then(function(res){if(/agents|api/.test(us2)){var c=res.clone();c.text().then(function(t){dump('agent_fetch','u='+us2.slice(0,150)+' st='+res.status+' r='+String(t).slice(0,120));});}return res;},function(e){if(/agents|api/.test(us2)){dump('agent_fetch_err','u='+us2.slice(0,150)+' e='+String(e).slice(0,120));}throw e;});};}"
            + "var CE=console.error;console.error=function(){try{dump('agent_console',Array.prototype.slice.call(arguments).map(function(a){return (a&&a.stack)?String(a.stack):String(a);}).join(' | ').slice(0,600));}catch(e){}return CE.apply(console,arguments);};"
            + "var CW=console.warn;console.warn=function(){try{dump('agent_warn',Array.prototype.slice.call(arguments).map(String).join(' ').slice(0,300));}catch(e){}return CW.apply(console,arguments);};"
            + "}catch(e){dump('agent_mon_err',String(e).slice(0,120));}},100);"
            + "var NW=window.WebSocket;var HIST={};"
            + "function WS(url,protos){"
            + "var us=String(url);"
            + "dump('agent_ws_any',us.slice(0,140));"
            + "if(us.indexOf('/agents/ws')<0)return new NW(url,protos);"
            + "try{"
            + "var self=this,ls={},sid='',runId='',lastPrompt='';"
            + "try{var u=new URL(us);sid=u.searchParams.get('session_id')||'';}catch(e){}"
            + "this.url=us;this.readyState=0;this.bufferedAmount=0;this.extensions='';this.protocol='';this.binaryType='blob';"
            + "this.onopen=null;this.onmessage=null;this.onerror=null;this.onclose=null;"
            + "this.addEventListener=function(t,f){(ls[t]=ls[t]||[]).push(f);};"
            + "this.removeEventListener=function(t,f){var a=ls[t];if(a){var i=a.indexOf(f);if(i>=0)a.splice(i,1);}};"
            + "this.dispatchEvent=function(ev){(ls[ev.type]||[]).slice().forEach(function(f){try{f.call(self,ev);}catch(e){}});"
            + "var h=self['on'+ev.type];if(h){try{h.call(self,ev);}catch(e){}}return true;};"
            + "function emit(o){setTimeout(function(){var d=JSON.stringify(o),ev;"
            + "try{ev=new MessageEvent('message',{data:d});}catch(e){ev={type:'message',data:d};}"
            + "self.dispatchEvent(ev);},0);}"
            + "function finish(reasoning,content){var key=sid||'d',H=HIST[key]||(HIST[key]=[]);H.push({role:'assistant',content:content});"
            + "emit({type:'run.step_finished',payload:{run_id:runId,step_index:0,credit:1,usage:{prompt_tokens:0,completion_tokens:content.length}}});"
            + "emit({type:'run.summary_started',payload:{run_id:runId}});"
            + "emit({type:'run.summary_completed',payload:{run_id:runId,credit:1}});"
            + "emit({type:'run.completed',payload:{run_id:runId,session_id:sid,credit:1,messages:[{id:'u1-'+runId,run_id:runId,session_id:sid,role:'user',step_index:0,created_at:new Date(Date.now()-3000).toISOString(),parts:[{type:'text',text:lastPrompt}],credit:0},{id:'m1',run_id:runId,session_id:sid,role:'assistant',step_index:0,created_at:new Date().toISOString(),parts:[{type:'text',text:content}],credit:1}]}});}"
            + "function stream(reasoning,content){"
            + "var i=0;while(reasoning&&i<reasoning.length){emit({type:'run.reasoning_delta',payload:{id:'r1',run_id:runId,step_index:0,delta:reasoning.slice(i,i+60)}});i+=60;}"
            + "var j=0;"
            + "function nextC(){if(j>=content.length){finish(reasoning,content);return;}"
            + "emit({type:'run.message_delta',payload:{id:'m1',run_id:runId,step_index:0,delta:content.slice(j,j+40)}});j+=40;setTimeout(nextC,30);}"
            + "nextC();}"
            + "function callAI(prompt){"
            + "var cfg=window.__aiCfg||{};"
            + "if(!cfg.baseUrl||!cfg.model){emit({type:'run.failed',payload:{run_id:runId,error:{message:'未配置本地 AI：请在文档 AI 对话中点 🤖 选择接口'}}});return;}"
            + "var target=String(cfg.baseUrl).replace(/\\/+$/,'')+'/chat/completions';"
            + "var body=JSON.stringify({model:cfg.model,messages:[{role:'system',content:'你是 ScholarRead 研究 Agent，帮助用户理解、分析和总结其阅读的研究资料。用中文回答，结构清晰。'}].concat((HIST[sid||'d']||[]).slice(-12)),stream:false,chat_template_kwargs:{enable_thinking:false}});"
            + "function handleResp(txt){try{var r=JSON.parse(txt);"
            + "var m=(r.choices&&r.choices[0]&&r.choices[0].message)||{};"
            + "var content=String(m.content||''),reasoning=String(m.reasoning_content||'');"
            + "if(!content){emit({type:'run.failed',payload:{run_id:runId,error:{message:'AI 返回为空'}}});return;}"
            + "stream(reasoning,content);}"
            + "catch(e){emit({type:'run.failed',payload:{run_id:runId,error:{message:'AI 响应解析失败: '+String(txt).slice(0,150)}}});}}"
            + "if(window.__aiLocal&&window.__aiLocal.aiFetchAsync){"
            + "dump('agent_call','bridge '+target.slice(0,180));"
            + "var id=++window.__aiFid;"
            + "window.__aiProm[id]=function(env){if(env&&env.ok){handleResp(env.body);}else{emit({type:'run.failed',payload:{run_id:runId,error:{message:'本地 AI 请求失败 HTTP '+((env&&env.status)||0)+' '+String((env&&env.body)||'').slice(0,120)}}});}};"
            + "window.__aiLocal.aiFetchAsync(id,target,body,cfg.apiKey||'');return;}"
            + "var url=(cfg.relay?cfg.relay:'')+'/relay/'+encodeURIComponent(target);"
            + "dump('agent_call',url.slice(0,220));"
            + "var xhr=new XMLHttpRequest();"
            + "xhr.open('POST',url,true);xhr.timeout=180000;"
            + "xhr.setRequestHeader('Content-Type','application/json');"
            + "if(cfg.apiKey)xhr.setRequestHeader('Authorization','Bearer '+cfg.apiKey);"
            + "xhr.onload=function(){dump('agent_resp','status='+xhr.status+' len='+String(xhr.responseText||'').length);handleResp(xhr.responseText);};"
            + "xhr.onerror=function(){dump('agent_err','network fail');emit({type:'run.failed',payload:{run_id:runId,error:{message:'本地 AI 连接失败（检查电脑端中继与 USB 连接）'}}});};"
            + "xhr.ontimeout=xhr.onerror;xhr.send(body);}"
            + "this.send=function(d){var msg;try{msg=JSON.parse(String(d));}catch(e){return;}"
            + "if(msg.type==='client.capabilities')return;"
            + "if(msg.type==='run.cancel'){emit({type:'run.canceled',payload:{run_id:runId}});return;}"
            + "if(msg.type!=='run.start')return;"
            + "var p=msg.payload||{};runId='local-'+Date.now();lastPrompt=String(p.prompt||'');var key=sid||'d',H=HIST[key]||(HIST[key]=[]);H.push({role:'user',content:lastPrompt});"
            + "dump('agent_run','prompt='+String(p.prompt||'').length+' cfg='+(window.__aiCfg?JSON.stringify(window.__aiCfg).slice(0,180):'NONE'));"
            + "emit({type:'run.started',payload:{run_id:runId}});"
            + "emit({type:'run.step_started',payload:{run_id:runId,step_index:0}});"
            + "callAI(p.prompt);};"
            + "this.close=function(c,r){if(self.readyState>1)return;self.readyState=3;"
            + "self.dispatchEvent({type:'close',code:c||1000,reason:r||'',wasClean:true});};"
            + "setTimeout(function(){self.readyState=1;dump('agent_open',us.slice(0,140));self.dispatchEvent({type:'open'});"
            + "emit({type:'server.hello',payload:{session_status:'ready'}});},30);"
            + "}catch(e){dump('agent_ctor_err',String((e&&e.stack)||e).slice(0,500));throw e;}}"
            + "WS.prototype={};"
            + "WS.CONNECTING=0;WS.OPEN=1;WS.CLOSING=2;WS.CLOSED=3;"
            + "window.WebSocket=WS;}catch(e){}})();";

    /** JS-visible bridge for the research page model switcher chip. */
    static final class Bridge {
        final WebView w;
        Bridge(WebView w) { this.w = w; }
        @JavascriptInterface
        public String profiles() {
            return AiCfgPanel.jsProfiles(w.getContext());
        }
        @JavascriptInterface
        public String active() {
            return AiCfgPanel.jsActive(w.getContext());
        }
        @JavascriptInterface
        public String switchTo(String name) {
            try {
                AiCfgPanel.activate(w.getContext(), name);
                return cfgJson();
            } catch (Exception e) {
                return "";
            }
        }

        /**
         * Async HTTP POST from JS without CORS pain: runs on a worker thread,
         * delivers {"ok":bool,"status":N,"body":"..."} to window.__aiFetchDone(id, env).
         * Lets the research page reach the user's AI directly (ngrok / LAN),
         * no localhost relay required.
         */
        @JavascriptInterface
        public void aiFetchAsync(final int id, final String url, final String body,
                final String auth) {
            new Thread(new Runnable() {
                public void run() {
                    JSONObject env = new JSONObject();
                    try {
                        java.net.HttpURLConnection c =
                                (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                        c.setConnectTimeout(15000);
                        c.setReadTimeout(180000);
                        c.setRequestMethod("POST");
                        c.setDoOutput(true);
                        c.setRequestProperty("Content-Type", "application/json");
                        if (auth != null && auth.length() > 0) {
                            c.setRequestProperty("Authorization", "Bearer " + auth);
                        }
                        java.io.OutputStream os = c.getOutputStream();
                        os.write(body.getBytes("UTF-8"));
                        os.flush();
                        int code = c.getResponseCode();
                        java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                        if (in != null) {
                            byte[] buf = new byte[4096];
                            int n;
                            while ((n = in.read(buf)) > 0) {
                                bo.write(buf, 0, n);
                            }
                            in.close();
                        }
                        env.put("ok", code == 200);
                        env.put("status", code);
                        env.put("body", bo.toString("UTF-8"));
                        c.disconnect();
                    } catch (Exception e) {
                        try {
                            env.put("ok", false);
                            env.put("status", 0);
                            env.put("body", String.valueOf(e));
                        } catch (Exception e2) {
                        }
                    }
                    String js = "window.__aiFetchDone&&window.__aiFetchDone(" + id + ","
                            + env.toString() + ");";
                    js = js.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
                    final String script = js;
                    try {
                        w.post(new Runnable() {
                            public void run() {
                                w.evaluateJavascript(script, null);
                            }
                        });
                    } catch (Exception e) {
                    }
                }
            }).start();
        }
    }

    public static void wrap(final WebView w) {
        try {
            w.addJavascriptInterface(new Bridge(w), "__aiLocal");
        } catch (Exception e) {
            Log.e(TAG, "js bridge fail: " + e);
        }
        try {
            final WebViewClient orig = w.getWebViewClient();
            w.setWebViewClient(new WebViewClient() {
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    if (orig != null) orig.onPageStarted(view, url, favicon);
                }

                public void onReceivedHttpError(WebView view, WebResourceRequest req,
                        WebResourceResponse resp) {
                    try {
                        Log.e(TAG, "http error " + resp.getStatusCode() + " "
                                + req.getMethod() + " "
                                + req.getUrl().toString().substring(0, Math.min(120,
                                        req.getUrl().toString().length())));
                    } catch (Exception e) {
                    }
                    if (orig != null) {
                        try { orig.onReceivedHttpError(view, req, resp); } catch (Throwable t) { }
                    }
                }

                public void onPageFinished(WebView view, String url) {
                    if (orig != null) orig.onPageFinished(view, url);
                    try {
                        Log.e(TAG, "agent page finished: " + url);
                        view.evaluateJavascript("window.__aiCfg=" + cfgJson() + ";", null);
                        view.evaluateJavascript(SHIM, null);
                        view.evaluateJavascript(
                                "(function(){return JSON.stringify({shim:!!window.__aiLocalWS,"
                                + "ws:String(window.WebSocket).slice(0,30),cfg:window.__aiCfg?1:0});})()",
                                new ValueCallback<String>() {
                                    public void onReceiveValue(String v) {
                                        Log.e(TAG, "shim check: " + v);
                                    }
                                });
                        startCfgPolling(view);
                    } catch (Exception e) {
                        Log.e(TAG, "inject fail: " + e);
                    }
                }

                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                    try {
                        String u = req.getUrl().toString();
                        String m = req.getMethod();
                        java.util.Map<String, String> h = new java.util.HashMap<String, String>();
                        h.put("Access-Control-Allow-Origin", "*");
                        h.put("Access-Control-Allow-Headers", "*");
                        h.put("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
                        h.put("Access-Control-Max-Age", "86400");
                        String path = req.getUrl().getPath();
                        if (u.startsWith("http")) {
                            Log.e(TAG, "req " + m + " " + u.substring(0, Math.min(130, u.length())));
                        }
                        if (u.contains("/agents/ws_ticket")) {
                            Log.e(TAG, "fake ws_ticket for " + u.substring(0, Math.min(90, u.length())));
                            String body = "{\"ws_ticket\":\"local-" + System.currentTimeMillis()
                                    + "\",\"routing_key\":\"local\"}";
                            return new WebResourceResponse("application/json", "UTF-8",
                                    200, "OK", h,
                                    new java.io.ByteArrayInputStream(body.getBytes("UTF-8")));
                        }
                        if ("OPTIONS".equals(m) && u.contains("/agents/")) {
                            return new WebResourceResponse("text/plain", "UTF-8",
                                    204, "No Content", h,
                                    new java.io.ByteArrayInputStream(new byte[0]));
                        }
                        // Prepend shim into the agent HTML BEFORE its module runs
                        // (the bundle captures window.WebSocket at module eval time).
                        if (path != null && path.contains("agent-assets") && path.endsWith(".html")) {
                            java.io.File f = new java.io.File(path);
                            if (f.isFile()) {
                                byte[] data = readAll(f);
                                String html = new String(data, "UTF-8");
                                String inj = "<script>window.__aiCfg=" + cfgJson() + ";" + SHIM + "</script>";
                                int i0 = html.indexOf("<head>");
                                if (i0 >= 0) {
                                    html = html.substring(0, i0 + 6) + inj + html.substring(i0 + 6);
                                } else {
                                    html = inj + html;
                                }
                                Log.e(TAG, "shim pre-injected into " + path);
                                return new WebResourceResponse("text/html", "UTF-8",
                                        new java.io.ByteArrayInputStream(html.getBytes("UTF-8")));
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "intercept fail: " + e);
                    }
                    if (orig != null) return orig.shouldInterceptRequest(view, req);
                    return null;
                }

                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                    return orig != null && orig.shouldOverrideUrlLoading(view, req);
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "probe wrap failed: " + t);
        }
    }

    /** Re-push window.__aiCfg every 2s so profile switches apply without reload. */
    static void startCfgPolling(final WebView view) {
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(new Runnable() {
                    public void run() {
                        try {
                            if (!view.isAttachedToWindow()) return;
                            view.evaluateJavascript("window.__aiCfg=" + cfgJson() + ";", null);
                            new android.os.Handler(android.os.Looper.getMainLooper())
                                    .postDelayed(this, 2000);
                        } catch (Exception e) {
                        }
                    }
                }, 2000);
    }

    static byte[] readAll(java.io.File f) throws java.io.IOException {
        java.io.InputStream in = new java.io.FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        } finally {
            in.close();
        }
    }

    static String cfgJson() {
        try {
            String cookie = CookieManager.getInstance()
                    .getCookie(AiCfgPanel.COOKIE_HOST);
            if (cookie != null) {
                for (String part : cookie.split(";")) {
                    String p = part.trim();
                    if (p.startsWith(AiCfgPanel.COOKIE_NAME + "=")) {
                        String v = URLDecoder.decode(
                                p.substring(AiCfgPanel.COOKIE_NAME.length() + 1), "UTF-8");
                        new JSONObject(v); // validate
                        return v;
                    }
                }
            }
        } catch (Exception e) {
        }
        return "{\"baseUrl\":\"" + AiCfgPanel.DEF_BASE + "\",\"model\":\""
                + AiCfgPanel.DEF_MODEL + "\",\"apiKey\":\"" + AiCfgPanel.DEF_KEY
                + "\",\"relay\":\"" + AiCfgPanel.DEF_RELAY + "\"}";
    }
}
