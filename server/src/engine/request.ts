/**
 * HTTP 请求执行模块。
 * 基于 axios 按请求规格发出 HTTP 请求，并把结果规整为统一的 ResponseData 结构。
 */
import axios from 'axios'
import type { RequestSpec, ResponseData } from './types.js'

/**
 * 发送 HTTP 请求并返回统一结构的响应。
 * 所有 HTTP 状态码均视为成功返回（断言层负责判定），
 * 仅网络错误 / 超时抛异常。
 */
export async function executeRequest(spec: RequestSpec): Promise<ResponseData> {
  const start = Date.now() // 记录开始时间，用于计算耗时

  const res = await axios.request({
    method: spec.method,
    url: spec.url,
    headers: spec.headers,
    params: spec.query,
    data: spec.body,
    timeout: spec.timeout ?? 30_000, // 默认超时 30 秒
    validateStatus: () => true, // 任意状态码都视为成功，交由断言判定
    maxRedirects: 0, // 不自动跟随重定向
  })

  // 规整原始响应体：字符串原样保留，其余序列化为 JSON 字符串
  const rawBody =
    typeof res.data === 'string'
      ? res.data
      : res.data === undefined
        ? ''
        : JSON.stringify(res.data)

  // 规整响应头：数组值合并为逗号分隔字符串，保证值统一为字符串
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
