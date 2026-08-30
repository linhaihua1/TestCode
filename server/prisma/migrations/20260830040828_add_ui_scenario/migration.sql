-- CreateTable
CREATE TABLE "UiScenario" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "projectId" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "description" TEXT,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "UiScenario_projectId_fkey" FOREIGN KEY ("projectId") REFERENCES "Project" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);

-- CreateTable
CREATE TABLE "UiScenarioStep" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "scenarioId" TEXT NOT NULL,
    "order" INTEGER NOT NULL,
    "uiTestCaseId" TEXT,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" DATETIME NOT NULL,
    CONSTRAINT "UiScenarioStep_scenarioId_fkey" FOREIGN KEY ("scenarioId") REFERENCES "UiScenario" ("id") ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "UiScenarioStep_uiTestCaseId_fkey" FOREIGN KEY ("uiTestCaseId") REFERENCES "UiTestCase" ("id") ON DELETE SET NULL ON UPDATE CASCADE
);
