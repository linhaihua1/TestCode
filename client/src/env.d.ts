/// <reference types="vite/client" />

/**
 * Vite 环境变量类型声明。
 *
 * <p>所有变量以 VITE_ 前缀暴露给前端；配置示例见项目根目录 .env.example。
 */
interface ImportMetaEnv {
  /** 后端 API 前缀，默认 /api/v1 */
  readonly VITE_API_BASE?: string;
  /** 请求超时（毫秒），默认 60000 */
  readonly VITE_API_TIMEOUT?: string;
  /** 登录页是否预填演示账号（true/false），默认 false */
  readonly VITE_PREFILL_DEMO_ACCOUNT?: string;
  /** 演示账号用户名（仅 VITE_PREFILL_DEMO_ACCOUNT=true 时使用） */
  readonly VITE_DEMO_USERNAME?: string;
  /** 演示账号密码（仅 VITE_PREFILL_DEMO_ACCOUNT=true 时使用） */
  readonly VITE_DEMO_PASSWORD?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
