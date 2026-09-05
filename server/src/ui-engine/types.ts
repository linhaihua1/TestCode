/** UI 自动化步骤的动作（用户自定义输入，参考 Selenium） */
export type UiAction = string

/** 元素定位方式（Selenium 常见 8 种） */
export type LocatorType =
  | 'id' // ID
  | 'name' // Name
  | 'className' // Class Name
  | 'tagName' // Tag Name
  | 'linkText' // 完整链接文本
  | 'partialLinkText' // 部分链接文本
  | 'css' // CSS 选择器
  | 'xpath' // XPath

/** UI 测试步骤定义 */
export interface UiStep {
  action: UiAction
  locatorType?: LocatorType // 定位方式，默认 css
  target?: string // 选择器（或 open 时的 URL）
  value?: string // 输入值 / 期望值 / 等待超时（秒）
}

/** UI 测试步骤执行结果 */
export interface UiStepResult {
  action: UiAction
  target?: string
  status: 'PASS' | 'FAIL' | 'ERROR'
  message: string
  screenshot?: string // 失败/出错时的 base64 截图
}
