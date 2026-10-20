// ScholarRead 本地 AI CORS 垫片
// 用途：WebView 里的 translate.js 从 https://com.scholaread.pdf2html 源发起跨域请求，
// 要求你的 AI 服务器返回 Access-Control-Allow-Origin。若你的服务器不带 CORS 头，
// 用这个脚本转发一层即可。
//
// 运行：node local_ai_cors_proxy.mjs --listen 8787 --upstream http://127.0.0.1:8000
// 手机上的 APK 配置：baseUrl = http://<电脑局域网IP>:8787/v1
//
// 零依赖，Node 18+。
const args = {};
for (let i = 2; i < process.argv.length; i += 2) {
  args[process.argv[i].replace(/^--/, '')] = process.argv[i + 1];
}
const LISTEN_PORT = Number(args.listen || 8787);
const UPSTREAM = (args.upstream || 'http://127.0.0.1:8000').replace(/\/+$/, '');

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, GET, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
  'Access-Control-Max-Age': '86400',
};

const server = await import('node:http').then(({ default: http }) =>
  http.createServer(async (req, res) => {
    if (req.method === 'OPTIONS') {
      res.writeHead(204, CORS);
      res.end();
      return;
    }
    // 中继模式：/relay/<encodeURIComponent(完整目标URL)> —— App 里填真实 API 地址，
    // 由这里代发并补 CORS 头（解决 ngrok 等服务器不带 Access-Control-Allow-Origin 的问题）。
    // 转储模式：POST /dump/<name> —— 手机 WebView 把 JS/文本回传到电脑 agent_dump/ 目录。
    const dm = req.url.match(/^\/dump\/(.+)$/);
    if (dm && req.method === 'POST') {
      const name = decodeURIComponent(dm[1]).replace(/[^a-zA-Z0-9._-]/g, '_');
      const chunks = [];
      for await (const c of req) chunks.push(c);
      const buf = Buffer.concat(chunks);
      const fs = await import('node:fs');
      const path = await import('node:path');
      const dir = path.join(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')), 'agent_dump');
      fs.mkdirSync(dir, { recursive: true });
      fs.writeFileSync(path.join(dir, name), buf);
      res.writeHead(200, { ...CORS, 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ ok: true, bytes: buf.length }));
      console.log(new Date().toISOString(), 'DUMP', name, buf.length, 'bytes');
      return;
    }
    let target;
    const m = req.url.match(/^\/relay\/(.+)$/);
    if (m) {
      target = decodeURIComponent(m[1]);
      if (!/^https?:\/\//.test(target)) {
        res.writeHead(400, { ...CORS, 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'bad relay target: ' + target }));
        return;
      }
    } else {
      target = UPSTREAM + req.url; // 兼容旧的固定上游模式
    }
    const chunks = [];
    for await (const c of req) chunks.push(c);
    try {
      const upstream = await fetch(target, {
        method: req.method,
        headers: {
          'Content-Type': req.headers['content-type'] || 'application/json',
          ...(req.headers.authorization ? { Authorization: req.headers.authorization } : {}),
        },
        body: req.method === 'GET' ? undefined : Buffer.concat(chunks),
      });
      const body = Buffer.from(await upstream.arrayBuffer());
      res.writeHead(upstream.status, { ...CORS, 'Content-Type': upstream.headers.get('content-type') || 'application/json' });
      res.end(body);
      console.log(new Date().toISOString(), req.method, '->', target, '=', upstream.status);
    } catch (e) {
      res.writeHead(502, { ...CORS, 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: String(e) }));
      console.error(new Date().toISOString(), target, 'ERROR', e.message);
    }
  }),
);

server.listen(LISTEN_PORT, '0.0.0.0', () => {
  console.log(`CORS shim listening on 0.0.0.0:${LISTEN_PORT} -> ${UPSTREAM}`);
  console.log('手机 APK 里 baseUrl 填: http://<本机局域网IP>:' + LISTEN_PORT + '/v1');
});
