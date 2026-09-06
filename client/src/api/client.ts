/**
 * Axios 客户端：所有请求自动带上 JWT，统一处理 401。
 * 时间字段按 UTC ISO 格式传递，前端展示时由 dayjs 转换本地时区。
 */
import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'

const instance: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 60_000
})

instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('token')
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

instance.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code === 0) {
        return data.data
      }
      if (data.code === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        window.location.href = '/#/login'
      }
      throw new Error(data.message || '请求失败')
    }
    return data
  },
  (error) => {
    const message = error.response?.data?.message || error.message
    return Promise.reject(new Error(message))
  }
)

export default instance