import axios from 'axios'
import type { RequestSpec, ResponseData } from './types.js'

/**
 * 发送 HTTP 请求并返回统一结构的响应。
 * 所有 HTTP 状态码均视为成功返回（断言层负责判定），
 * 仅网络错误 / 超时抛异常。
 */
export async function executeRequest(spec: RequestSpec): Promise<ResponseData> {
  const start = Date.now()

  const res = await axios.request({
    method: spec.method,
    url: spec.url,
    headers: spec.headers,
    params: spec.query,
    data: spec.body,
    timeout: spec.timeout ?? 30_000,
    validateStatus: () => true,
    maxRedirects: 0,
  })

  const rawBody =
    typeof res.data === 'string'
      ? res.data
      : res.data === undefined
        ? ''
        : JSON.stringify(res.data)

  const headers: Record<string, string> = {}
  for (const [key, value] of Object.entries(res.headers)) {
    if (value !== undefined) headers[key] = Array.isArray(value) ? value.join(', ') : String(value)
  }

  return {
    status: res.status,
    headers,
    body: res.data,
    rawBody,
    duration: Date.now() - start,
  }
}
