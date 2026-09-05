-- RedefineTables
PRAGMA defer_foreign_keys=ON;
PRAGMA foreign_keys=OFF;
CREATE TABLE "new_TestTask" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "projectId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT,
    "caseIds" JSONB NOT NULL DEFAULT [],
    "environmentId" TEXT,
    "executeMode" TEXT NOT NULL DEFAULT 'sequential',
    "retryCount" INTEGER NOT NULL DEFAULT 0,
    "timeout" INTEGER NOT NULL DEFAULT 300000,
    "cronExpr" TEXT,
    "enabled" BOOLEAN NOT NULL DEFAULT true,
    "notifyUrl" TEXT,
    "variables" JSONB NOT NULL DEFAULT [],
    "baseUrl" TEXT,
    "createdBy" TEXT,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    "deletedAt" DATETIME,
    CONSTRAINT "TestTask_projectId_fkey" FOREIGN KEY ("projectId") REFERENCES "Project" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);
INSERT INTO "new_TestTask" ("caseIds", "createdAt", "createdBy", "cronExpr", "deletedAt", "description", "enabled", "environmentId", "executeMode", "id", "name", "notifyUrl", "projectId", "retryCount", "timeout", "updatedAt", "variables") SELECT "caseIds", "createdAt", "createdBy", "cronExpr", "deletedAt", "description", "enabled", "environmentId", "executeMode", "id", "name", "notifyUrl", "projectId", "retryCount", "timeout", "updatedAt", '[]' FROM "TestTask";
DROP TABLE "TestTask";
ALTER TABLE "new_TestTask" RENAME TO "TestTask";
PRAGMA foreign_keys=ON;
PRAGMA defer_foreign_keys=OFF;
