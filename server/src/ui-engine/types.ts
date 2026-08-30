/** UI 自动化步骤的动作类型（参考 Selenium） */
export type UiAction =
  | 'open' // 打开页面（target 为 URL）
  | 'click' // 点击元素
  | 'type' // 输入文本
  | 'assertText' // 断言元素文本
  | 'assertExists' // 断言元素存在
  | 'assertTitle' // 断言页面标题
  | 'wait' // 等待元素出现

/** 元素定位方式（参考 Selenium 的 By） */
export type LocatorType = 'css' | 'xpath' | 'id' | 'name' | 'linkText'

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
