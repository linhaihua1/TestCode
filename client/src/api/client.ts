/**
 * Axios HTTP 客户端封装。
 *
 * <p>所有 API 请求通过本实例发送，已处理：
 * <ul>
 *   <li>JWT 自动注入：从 localStorage 读 token 加到 {@code Authorization} Header</li>
 *   <li>响应自动解包：把后端 {@code {code, message, data}} 中的 data 取出直接返回</li>
 *   <li>401 自动跳转：token 过期时清理登录态并跳登录页</li>
 *   <li>统一错误：网络异常或业务错误统一抛 {@link Error}，调用方可 try/catch</li>
 * </ul>
 *
 * <h3>时间字段约定</h3>
 * 后端用 {@code Instant} 序列化输出 ISO-8601 字符串（带时区）。
 * 前端用 dayjs 做时区转换：{@code dayjs.utc(str).local().format('YYYY-MM-DD HH:mm:ss')}。
 */
import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'

const instance: AxiosInstance = axios.create({
  baseURL: '/api',         // 由 vite.config.ts 的 server.proxy 转发到后端 8080
  timeout: 60_000          // 60 秒超时（性能测试 / 大文件下载可单独覆盖）
})

/**
 * 请求拦截器：自动注入 JWT。
 */
instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('token')
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * 响应拦截器：
 * <ul>
 *   <li>code=0：返回 data 字段</li>
 *   <li>code=401：清理登录态，跳登录页</li>
 *   <li>其它 code：抛 Error(message)</li>
 *   <li>HTTP 错误（4xx/5xx）：从 response.data.message 取错误信息</li>
 * </ul>
 */
instance.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code === 0) {
        return data.data
      }
      if (data.code === 401) {
        // token 过期或被服务端吊销
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        window.location.href = '/#/login'
        throw new Error('登录已过期')
      }
      throw new Error(data.message || '请求失败')
    }
    // 非 Result 格式（如文件下载、二进制流）原样返回
    return data
  },
  (error) => {
    // 网络异常 / HTTP 5xx / 业务异常 code
    const message = error.response?.data?.message || error.message
    return Promise.reject(new Error(message))
  }
)

export default instance
