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
import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios';
import { API_CODE, LOGIN_ROUTE_PATH } from '@/constants';

/**
 * 后端 API 前缀：优先取构建期环境变量 VITE_API_BASE，未配置时回退到开发约定 /api/v1。
 * 部署到子路径 / 独立域名时只需改环境变量，无需改代码。
 */
export const API_BASE_URL = import.meta.env.VITE_API_BASE || '/api/v1';

/** 请求超时（毫秒）：可由 VITE_API_TIMEOUT 覆盖 */
export const API_TIMEOUT = Number(import.meta.env.VITE_API_TIMEOUT) || 60_000;

const instance: AxiosInstance = axios.create({
  baseURL: API_BASE_URL, // 由 vite.config.ts 的 server.proxy 转发到后端 8080
  timeout: API_TIMEOUT, // 性能测试 / 大文件下载可单独覆盖
});

/**
 * 请求拦截器：自动注入 JWT。
 */
instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('token');
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/**
 * 清空本地登录态并跳转登录页（带 redirect，登录后回到原页面）。
 *
 * <p>已在登录页时只清理凭证、不再跳转，避免打断用户正在输入的账号密码。</p>
 */
function redirectToLogin(): void {
  localStorage.removeItem('token');
  localStorage.removeItem('user');
  const loginHash = '#' + LOGIN_ROUTE_PATH;
  if (window.location.hash.startsWith(loginHash)) return;
  const current = window.location.hash.replace(/^#/, '');
  const redirect =
    current && current !== LOGIN_ROUTE_PATH ? `?redirect=${encodeURIComponent(current)}` : '';
  window.location.href = `${import.meta.env.BASE_URL}${loginHash}${redirect}`;
}

/**
 * 响应拦截器：
 * <ul>
 *   <li>code=0：返回 data 字段</li>
 *   <li>code=1002（登录态失效）/ HTTP 401：清理登录态并跳登录页</li>
 *   <li>code=1006（账号或密码错误）：按普通业务错误抛出，不影响登录态</li>
 *   <li>其它 code（含 1003 无权限、1004 不存在、1005 冲突）：抛 Error(message)，由调用方提示</li>
 *   <li>其它 code：抛 Error(message)</li>
 *   <li>HTTP 错误（4xx/5xx）：从 response.data.message 取错误信息</li>
 * </ul>
 */
instance.interceptors.response.use(
  (response) => {
    const data = response.data;
    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code === API_CODE.SUCCESS) {
        return data.data;
      }
      if (data.code === API_CODE.UNAUTHORIZED) {
        // 登录态失效：清理凭证并回登录页
        redirectToLogin();
        throw new Error(data.message || '登录已过期，请重新登录');
      }
      // 1006 等其它业务码：原样抛出，由调用方（如登录页）展示真实原因
      throw new Error(data.message || `请求失败(code=${data.code})`);
    }
    // 非 Result 格式（如文件下载、二进制流）原样返回
    return data;
  },
  (error) => {
    // 鉴权拦截器直接返回 HTTP 401（未经过 Result 包装层）时，同样按登录态失效处理
    if (error.response?.status === 401) {
      redirectToLogin();
      throw new Error(error.response?.data?.message || '登录已过期，请重新登录');
    }
    // 网络异常 / HTTP 5xx / 业务异常 code
    const message = error.response?.data?.message || error.message;
    return Promise.reject(new Error(message));
  },
);

/**
 * 类型化的 HTTP 客户端接口。
 *
 * <p>响应拦截器已经把后端的 {@code {code, message, data}} 解包，直接返回其中的
 * {@code data}，所以实例方法在运行时返回的是**业务数据 T**，而不是 {@code AxiosResponse<T>}。
 * 但 axios 自带的类型签名仍是 {@code Promise<AxiosResponse<T>>}，两者不一致会导致
 * 调用方到处需要 {@code .data} 或类型断言。
 *
 * <p>这里显式声明真实行为，{@link HttpClient} 由下面的 {@code instance} 断言得到，
 * 纯类型层面修正，运行时行为零变化。
 */
export interface HttpClient {
  request<T = any>(config: AxiosRequestConfig): Promise<T>;
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>;
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>;
  head<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>;
  options<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>;
  post<T = any>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>;
  put<T = any>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>;
  patch<T = any>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>;
}

/** 已按 {@link HttpClient} 语义修正类型的单例（拦截器完成解包）。 */
const http = instance as unknown as HttpClient;

export default http;
