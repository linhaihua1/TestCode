// 前端领域类型（与后端 Prisma 实体对齐）

export interface KeyValue {
  key: string
  value: string
  enabled?: boolean
}

export interface Project {
  id: string
  name: string
  description?: string | null
  createdAt: string
  updatedAt: string
}

export interface Environment {
  id: string
  projectId: string
  name: string
  baseUrl?: string | null
  variables: KeyValue[]
  headers: KeyValue[]
  createdAt: string
  updatedAt: string
}

export interface ApiDefinition {
  id: string
  projectId: string
  name: string
  method: string
  path: string
  headers: KeyValue[]
  query: KeyValue[]
  body?: string | null
  description?: string | null
  createdAt: string
  updatedAt: string
}

export interface Assertion {
  type: 'statusCode' | 'jsonPath' | 'header' | 'regex'
  expression: string
  expected: string
  operator?: 'eq' | 'ne' | 'contains' | 'notContains' | 'regex' | 'gt' | 'lt'
}

export interface ExtractRule {
  name: string
  type: 'jsonPath' | 'header' | 'regex'
  expression: string
}

export interface ApiCase {
  id: string
  apiId: string
  name: string
  assertions: Assertion[]
  extracts: ExtractRule[]
  createdAt: string
  updatedAt: string
}

export interface Scenario {
  id: string
  projectId: string
  name: string
  description?: string | null
  createdAt: string
  updatedAt: string
}

export interface AssertionResult {
  passed: boolean
  type: string
  expression: string
  expected: string
  actual: string
  message: string
}

export interface ScenarioStep {
  id: string
  scenarioId: string
  order: number
  apiCaseId?: string | null
  name?: string | null
  assertions: Assertion[]
  extracts: ExtractRule[]
  apiCase?: (ApiCase & { api?: ApiDefinition }) | null
}

export interface ReportDetail {
  id: string
  stepName: string
  status: 'PASS' | 'FAIL' | 'ERROR'
  error?: string | null
  assertions: AssertionResult[]
  extracts: Record<string, string>
}

export interface Report {
  id: string
  projectId: string
  scenarioId?: string | null
  name: string
  status: 'PASS' | 'FAIL' | 'ERROR'
  duration: number
  startedAt: string
  details: ReportDetail[]
}

export const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'] as const
