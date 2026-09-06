#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Api-Web 一键安装脚本（Linux）
# 完成：Node 检查 → npm ci 安装依赖 → Prisma 生成+迁移 → 可选演示数据 → 构建前后端
# 用法：bash deploy/install.sh        （AUTO_SEED=1 可自动写演示数据，免交互）
# ============================================================

cd "$(dirname "$0")/.."
APP_DIR="$(pwd)"
echo "==> 项目目录：$APP_DIR"

# 1) Node 20+ 检查
if ! command -v node >/dev/null 2>&1; then
  echo "错误：未找到 Node.js，请先安装 Node 20+（https://nodejs.org）"
  exit 1
fi
NODE_MAJOR=$(node -p 'process.versions.node.split(".")[0]')
if [ "$NODE_MAJOR" -lt 20 ]; then
  echo "错误：需要 Node 20+，当前 $(node -v)"
  exit 1
fi
echo "==> Node $(node -v) 就绪"

# 2) 安装依赖（按 lock 精确安装，覆盖全部 workspace）
echo "==> 安装依赖（npm ci）…"
npm ci

# 3) 初始化数据库
echo "==> 初始化数据库（生成 Prisma Client + 应用迁移）…"
( cd server && npx prisma generate && npx prisma migrate deploy )

# 4) 可选演示数据
if [ "${AUTO_SEED:-0}" = "1" ]; then
  echo "==> 写入演示数据…"
  npm run seed -w @api-web/server
else
  read -r -p "是否写入演示数据（admin/admin@123 + 演示项目）？[y/N] " yn || yn=""
  if [[ "$yn" =~ ^[Yy]$ ]]; then
    npm run seed -w @api-web/server
  fi
fi

# 5) 构建前后端
echo "==> 构建前后端…"
npm run build

echo ""
if [ ! -f server/jmeter/bin/ApacheJMeter.jar ]; then
  echo "提示：性能测试模块需要 JMeter 运行时（可选）。"
  echo "  下载 JMeter 5.x 并解压到 server/jmeter/（需存在 bin/ApacheJMeter.jar），"
  echo "  或设置环境变量 JMETER_HOME；不配置也不影响接口/UI 自动化使用。"
  echo "  详见 deploy/DEPLOY-README.md。"
fi

echo ""
echo "✅ 安装完成。启动方式："
echo "   快速启动（前端+后端一体）：bash deploy/start.sh"
echo "   生产部署（systemd + Nginx）：见 deploy/DEPLOY-README.md"
