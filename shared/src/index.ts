// 前后端共享的领域类型与常量

export type HttpMethod =
  | 'GET'
  | 'POST'
  | 'PUT'
  | 'PATCH'
  | 'DELETE'
  | 'HEAD'
  | 'OPTIONS'

export interface KeyValue {
  key: string
  value: string
  enabled?: boolean
}

/** 接口定义（API Definition） */
export interface ApiDefinition {
  id: string
  projectId: string
  name: string
  method: HttpMethod
  path: string
  headers: KeyValue[]
  query: KeyValue[]
  /** JSON 字符串或 raw 文本 */
  body: string
  description?: string
}
