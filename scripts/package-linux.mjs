/**
 * 打包 Linux 部署安装包。
 *
 * 产物：<项目根>/api-web-<version>-linux.tar.gz
 * 内容：完整源码（排除 node_modules / 构建产物 / JMeter 运行时 / 数据库 / 日志 / git 等）
 *      + deploy/（install.sh / start.sh / stop.sh / serve.mjs / systemd / nginx 配置 / 说明）
 *
 * 用法：node scripts/package-linux.mjs
 * 安装包上传到 Linux 服务器后：tar -xzf ... -C /opt && cd /opt/api-web && bash deploy/install.sh
 */
import { execSync } from 'node:child_process'
import { cpSync, mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const pkg = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8'))
const version = pkg.version
const outName = `api-web-${version}-linux.tar.gz`
const outPath = join(root, outName)

const rel = (p) => relative(root, p).replace(/\\/g, '/')

/** 需从安装包中排除的路径/模式（相对项目根） */
function excluded(src) {
  const r = rel(src)
  const segs = r.split('/')
  if (segs.includes('node_modules')) return true
  if (segs.includes('.git') || segs.includes('.idea')) return true
  if (segs.includes('dist')) return true // 各 workspace 的构建产物
  if (r === 'server/jmeter' || r.startsWith('server/jmeter/')) return true // 55MB 运行时，不随包
  if (r === 'server/bin' || r.startsWith('server/bin/')) return true // chromedriver
  if (r === 'server/.perf-runs' || r.startsWith('server/.perf-runs/')) return true
  if (/^server\/(dev|test)\.db(-journal)?$/.test(r)) return true // 不携带数据
  if (r === '.env' || r.startsWith('.env.')) return true
  if (r.endsWith('.log') || r.endsWith('.tar.gz') || r.endsWith('.tsbuildinfo')) return true
  return false
}

function main() {
  console.log('==> 1) 构建前后端（校验可构建）…')
  execSync('npm run build', { cwd: root, stdio: 'inherit' })

  console.log('==> 2) 打包源码…')
  const pkgRoot = mkdtempSync(join(tmpdir(), 'apiweb-pkg-'))
  const inner = join(pkgRoot, 'api-web')
  try {
    cpSync(root, inner, { recursive: true, filter: (src) => !excluded(src) })

    console.log('==> 3) 生成压缩包…')
    rmSync(outPath, { force: true })
    execSync(`tar -czf "${outPath}" -C "${pkgRoot}" api-web`, { stdio: 'inherit' })

    const mb = (readFileSync(outPath).length / 1024 / 1024).toFixed(2)
    console.log(`\n✅ 安装包已生成：${outPath}（${mb} MB）`)
    console.log('   部署：tar -xzf ' + outName + ' -C /opt && cd /opt/api-web && bash deploy/install.sh')
  } finally {
    rmSync(pkgRoot, { recursive: true, force: true })
  }
}

main()
