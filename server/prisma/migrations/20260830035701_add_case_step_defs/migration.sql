-- RedefineTables
PRAGMA defer_foreign_keys=ON;
PRAGMA foreign_keys=OFF;
CREATE TABLE "new_ApiCase" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "apiId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "assertions" JSONB NOT NULL DEFAULT [],
    "extracts" JSONB NOT NULL DEFAULT [],
    "stepDefs" JSONB NOT NULL DEFAULT '[]',
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "ApiCase_apiId_fkey" FOREIGN KEY ("apiId") REFERENCES "ApiDefinition" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);
INSERT INTO "new_ApiCase" ("apiId", "assertions", "createdAt", "extracts", "id", "name", "updatedAt") SELECT "apiId", "assertions", "createdAt", "extracts", "id", "name", "updatedAt" FROM "ApiCase";
DROP TABLE "ApiCase";
ALTER TABLE "new_ApiCase" RENAME TO "ApiCase";
PRAGMA foreign_keys=ON;
PRAGMA defer_foreign_keys=OFF;
