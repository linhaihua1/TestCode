-- CreateTable
CREATE TABLE "CaseReview" (
    "id" TEXT NOT NULL PRIMARY KEY,
    "caseId" TEXT NOT NULL,
    "reviewerId" TEXT,
    "reviewerName" TEXT,
    "action" TEXT NOT NULL,
    "comment" TEXT,
    "fromStatus" TEXT,
    "toStatus" TEXT,
    "createdAt" DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "CaseReview_caseId_fkey" FOREIGN KEY ("caseId") REFERENCES "CaseInfo" ("id") ON DELETE CASCADE ON UPDATE CASCADE
);
