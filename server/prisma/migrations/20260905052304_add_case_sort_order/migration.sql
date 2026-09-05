-- RedefineTables
PRAGMA defer_foreign_keys=ON;
PRAGMA foreign_keys=OFF;
CREATE TABLE "new_CaseInfo" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "projectId" TEXT NOT NULL,
    "moduleId" TEXT,
    "name" TEXT NOT NULL,
    "description" TEXT,
    "status" TEXT NOT NULL DEFAULT 'draft',
    "priority" TEXT NOT NULL DEFAULT 'P2',
    "tags" JSONB NOT NULL DEFAULT [],
    "steps" JSONB NOT NULL DEFAULT [],
    "version" INTEGER NOT NULL DEFAULT 1,
    "sortOrder" INTEGER NOT NULL DEFAULT 0,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    "deletedAt" DATETIME,
    CONSTRAINT "CaseInfo_projectId_fkey" FOREIGN KEY ("projectId") REFERENCES "Project" ("id") ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "CaseInfo_moduleId_fkey" FOREIGN KEY ("moduleId") REFERENCES "Module" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);
INSERT INTO "new_CaseInfo" ("createdAt", "deletedAt", "description", "id", "moduleId", "name", "priority", "projectId", "status", "steps", "tags", "updatedAt", "version") SELECT "createdAt", "deletedAt", "description", "id", "moduleId", "name", "priority", "projectId", "status", "steps", "tags", "updatedAt", "version" FROM "CaseInfo";
DROP TABLE "CaseInfo";
ALTER TABLE "new_CaseInfo" RENAME TO "CaseInfo";
PRAGMA foreign_keys=ON;
PRAGMA defer_foreign_keys=OFF;
