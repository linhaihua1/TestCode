-- 给压测用例增加「加压方式」配置（固定并发 / 阶梯加压 / 目标并发）
-- 注意：SQLite 下 Prisma 的 Json 默认值会生成不合法 SQL（DEFAULT {}），
-- 故按项目约定手工写成带引号的 TEXT 默认值。
ALTER TABLE "PerfCase" ADD COLUMN "profile" TEXT NOT NULL DEFAULT '{}';
