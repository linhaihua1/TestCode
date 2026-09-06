# Api-Web 自动化测试平台

一个参考 MeterSphere 的**接口自动化 + UI 自动化 + 性能测试**一体化测试平台。前后端分离的 Web 应用，开箱即用，数据存储于单文件 SQLite，零外部数据库依赖。

## ✨ 功能特性

- **接口自动化**：三栏工作台（用例库 / 编写用例 / 接口管理）、多步骤用例、断言 / 提取 / 变量、IF / FOR / WHILE 流程控制器、JavaScript / Python / Java 自定义脚本、Swagger / Excel 导入、Mock、测试任务执行与报告
- **UI 自动化**：基于 Selenium 驱动真实 Chrome，步骤编排（8 种定位方式）、失败自动截图、执行计划与报告
- **性能测试（JMeter）**：结构化用例编辑、**阶梯加压 / 目标并发**线程组、`.jmx` 导入导出、**异步执行 + 实时进度 + 可停止**、P90/P95/P99/TPS/线程数/响应码等多维报告、PDF 导出
- **权限与审计**：管理员 / 成员 / 查看者三角色 RBAC，操作审计日志
- **测试报告**：图表化概览 + 明细 + PDF 导出

## 🧰 技术栈

| 层 | 技术 |
|---|---|
| 前端 | React 19 · Vite · Ant Design 6 · @ant-design/plots |
| 后端 | Node.js 20+ · TypeScript · Fastify 5 · Prisma 6 |
| 数据库 | SQLite（单文件，零配置） |
| 性能测试 | Apache JMeter 5.x（内置运行时，可选） |
| UI 自动化 | Selenium WebDriver + Chrome |

## 🚀 本地开发（快速开始）

```bash
# 1) 安装依赖
npm install

# 2) 初始化数据库 + 造演示数据（admin / admin@123）
cd server
npx prisma migrate deploy
npm run seed
cd ..

# 3) 启动后端（4000）与前端（5173）
npm run dev:server
npm run dev:client
```

浏览器打开 `http://localhost:5173`，默认账号 `admin / admin@123`。

> 性能测试模块需要 JMeter 运行时（可选）：解压 JMeter 5.x 到 `server/jmeter/`（确保存在 `bin/ApacheJMeter.jar`），或设置环境变量 `JMETER_HOME`。详见文档。

## 📦 Linux 服务器部署

```bash
# 一键打包安装包（在项目根目录执行）
node scripts/package-linux.mjs

# 上传到服务器后：一键安装并启动（首次自动装依赖→迁移→构建→启动）
tar -xzf api-web-<版本>-linux.tar.gz -C /opt
cd /opt/api-web && bash deploy/start.sh
```

详见 `deploy/DEPLOY-README.md` 与 `docs/安装文档-Linux.md`。

## 📚 文档

| 文档 | 说明 |
|---|---|
| [操作文档](docs/操作文档.md) | 各模块使用说明 |
| [开发文档](docs/开发文档.md) | 架构、目录结构、核心流程 |
| [安装文档（本地开发）](docs/安装文档.md) | Windows 本地开发环境 |
| [安装文档（Linux）](docs/安装文档-Linux.md) | Linux 服务器部署 |

## 📁 目录结构

```
Api-web/
├── client/            # 前端（React + Ant Design）
├── server/            # 后端（Fastify + Prisma）
│   ├── src/engine/    # 接口执行引擎 / JMeter 压测引擎
│   ├── prisma/        # 数据模型与迁移
│   └── scripts/       # 造数 / 打包等脚本
├── shared/            # 共享类型
├── deploy/            # Linux 部署模板（install/start/systemd/nginx）
└── docs/              # 文档
```

## 📄 License

[MIT](LICENSE)
