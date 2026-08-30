-- RedefineTables
PRAGMA defer_foreign_keys=ON;
PRAGMA foreign_keys=OFF;
CREATE TABLE "new_UiTestCase" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "projectId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT,
    "baseUrl" TEXT,
    "setupSteps" JSONB NOT NULL DEFAULT [],
    "steps" JSONB NOT NULL DEFAULT [],
    "teardownSteps" JSONB NOT NULL DEFAULT [],
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "UiTestCase_projectId_fkey" FOREIGN KEY ("projectId") REFERENCES "Project" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);
INSERT INTO "new_UiTestCase" ("baseUrl", "createdAt", "description", "id", "name", "projectId", "steps", "updatedAt") SELECT "baseUrl", "createdAt", "description", "id", "name", "projectId", "steps", "updatedAt" FROM "UiTestCase";
DROP TABLE "UiTestCase";
ALTER TABLE "new_UiTestCase" RENAME TO "UiTestCase";
PRAGMA foreign_keys=ON;
PRAGMA defer_foreign_keys=OFF;
