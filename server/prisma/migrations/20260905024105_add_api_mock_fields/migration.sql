-- RedefineTables
PRAGMA defer_foreign_keys=ON;
PRAGMA foreign_keys=OFF;
CREATE TABLE "new_ApiDefinition" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "projectId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "method" TEXT NOT NULL,
    "path" TEXT NOT NULL,
    "headers" JSONB NOT NULL DEFAULT [],
    "query" JSONB NOT NULL DEFAULT [],
    "body" TEXT,
    "description" TEXT,
    "mockEnabled" BOOLEAN NOT NULL DEFAULT false,
    "mockResponse" TEXT,
    "moduleId" TEXT,
    "tags" JSONB NOT NULL DEFAULT [],
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "ApiDefinition_projectId_fkey" FOREIGN KEY ("projectId") REFERENCES "Project" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);
INSERT INTO "new_ApiDefinition" ("body", "createdAt", "description", "headers", "id", "method", "name", "path", "projectId", "query", "tags", "updatedAt") SELECT "body", "createdAt", "description", "headers", "id", "method", "name", "path", "projectId", "query", '[]', "updatedAt" FROM "ApiDefinition";
DROP TABLE "ApiDefinition";
ALTER TABLE "new_ApiDefinition" RENAME TO "ApiDefinition";
PRAGMA foreign_keys=ON;
PRAGMA defer_foreign_keys=OFF;
