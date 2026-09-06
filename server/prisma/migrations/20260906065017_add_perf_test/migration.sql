-- CreateTable
-- 注意：SQLite 下 Prisma 会为 Json 字段生成未加引号的 DEFAULT [] / DEFAULT {}，
-- 其中 {} 直接是语法错误、[] 会被当成空标识符从而存入空串（P2023）。
-- 因此这里显式改为字符串字面量默认值。
CREATE TABLE "PerfCase" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "projectId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT,
    "threads" INTEGER NOT NULL DEFAULT 1,
    "rampUp" INTEGER NOT NULL DEFAULT 1,
    "loops" INTEGER NOT NULL DEFAULT 1,
    "duration" INTEGER NOT NULL DEFAULT 0,
    "thinkTime" INTEGER NOT NULL DEFAULT 0,
    "onSampleError" TEXT NOT NULL DEFAULT 'continue',
    "variables" TEXT NOT NULL DEFAULT '[]',
    "steps" TEXT NOT NULL DEFAULT '[]',
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    "deletedAt" DATETIME,
    CONSTRAINT "PerfCase_projectId_fkey" FOREIGN KEY ("projectId") REFERENCES "Project" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "PerfReport" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "projectId" TEXT NOT NULL,
    "caseId" TEXT,
    "name" TEXT NOT NULL,
    "status" TEXT NOT NULL,
    "duration" INTEGER NOT NULL DEFAULT 0,
    "startedAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "summary" TEXT NOT NULL DEFAULT '{}',
    "series" TEXT NOT NULL DEFAULT '[]',
    "labels" TEXT NOT NULL DEFAULT '[]',
    "errors" TEXT NOT NULL DEFAULT '[]',
    "message" TEXT,
    CONSTRAINT "PerfReport_projectId_fkey" FOREIGN KEY ("projectId") REFERENCES "Project" ("id") ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "PerfReport_caseId_fkey" FOREIGN KEY ("caseId") REFERENCES "PerfCase" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);
