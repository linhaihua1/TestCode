#!/usr/bin/env node
/**
 * 前端静态服务器 + 反向代理（无需 Nginx 的简易部署方式）。
 * 监听 FRONTEND_PORT（默认 8080），托管 client/dist，并把 /api、/mock 转发到后端。
 *
 * 环境变量：
 *   FRONTEND_PORT  前端监听端口（默认 8080）
 *   API_ORIGIN     后端地址（默认 http://127.0.0.1:4000，不带 /api 前缀）
 */
import { createServer } from 'node:http'
import { createReadStream, existsSync, statSync } from 'node:fs'
import { extname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const deployDir = fileURLToPath(new URL('.', import.meta.url))
const distDir = join(deployDir, '..', 'client', 'dist')
const PORT = Number(process.env.FRONTEND_PORT ?? 8080)
const API = (process.env.API_ORIGIN ?? 'http://127.0.0.1:4000').replace(/\/+$/, '')

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.map': 'application/json',
  '.txt': 'text/plain; charset=utf-8',
}

async function readBody(req) {
  const chunks = []
  for await (const c of req) chunks.push(c)
  return Buffer.concat(chunks)
}

async function proxy(req, res, target) {
  try {
    const body = req.method === 'GET' || req.method === 'HEAD' ? undefined : await readBody(req)
    const headers = { ...req.headers, host: new URL(target).host }
    delete headers['content-length']
    const r = await fetch(target, { method: req.method, headers, body, redirect: 'manual' })
    const resHeaders = {}
    for (const [k, v] of r.headers) {
      if (['transfer-encoding', 'connection', 'content-encoding'].includes(k.toLowerCase())) continue
      resHeaders[k] = v
    }
    res.writeHead(r.status, resHeaders)
    if (r.body) {
      const reader = r.body.getReader()
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        res.write(value)
      }
    }
    res.end()
  } catch (e) {
    res.writeHead(502, { 'content-type': 'application/json; charset=utf-8' })
    res.end(JSON.stringify({ error: '后端不可用：' + (e && e.message ? e.message : String(e)) }))
  }
}

createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost')
  const pathname = decodeURIComponent(url.pathname)

  if (pathname.startsWith('/api/') || pathname.startsWith('/mock/')) {
    return proxy(req, res, `${API}${pathname}${url.search}`)
  }

  let file = join(distDir, pathname === '/' ? 'index.html' : pathname.slice(1))
  if (!existsSync(file) || !statSync(file).isFile()) {
    file = join(distDir, 'index.html') // SPA 前端路由回退
  }
  res.setHeader('content-type', MIME[extname(file).toLowerCase()] ?? 'application/octet-stream')
  createReadStream(file)
    .on('error', () => {
      res.writeHead(404)
      res.end('Not Found')
    })
    .pipe(res)
}).listen(PORT, () => {
  console.log(`前端已启动：http://0.0.0.0:${PORT}（/api、/mock → ${API}）`)
})
