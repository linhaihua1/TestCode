/**
 * UI 自动化执行引擎（基于 selenium-webdriver，驱动 Chrome 浏览器）。
 * 将一组步骤（打开/点击/输入/断言/等待）在真实浏览器中逐步执行，
 * 记录每一步的结果，失败/出错时自动截图。
 * 支持单用例执行与场景执行（复用同一浏览器会话串联多个用例）。
 */
import { Builder, By, until, type WebDriver } from 'selenium-webdriver'
import chrome from 'selenium-webdriver/chrome'
import { existsSync } from 'node:fs'
import path from 'node:path'
import type { UiStep, UiStepResult } from './types.js'

/** 自动查找 chromedriver：优先环境变量，其次项目 bin/ 目录 */
function resolveChromedriver(): string | undefined {
  const candidates = [
    process.env.CHROMEDRIVER_PATH,
    path.join(process.cwd(), 'bin', 'chromedriver.exe'),
    path.join(process.cwd(), 'server', 'bin', 'chromedriver.exe'),
  ].filter(Boolean) as string[]
  for (const p of candidates) {
    if (existsSync(p)) return p
  }
  return undefined
}

/** 根据定位方式构造 selenium 的 By 定位器（8 种常见定位方式） */
function buildLocator(step: UiStep): By {
  const target = step.target ?? ''
  switch (step.locatorType ?? 'css') {
    case 'id':
      return By.id(target)
    case 'name':
      return By.name(target)
    case 'className':
      return By.className(target)
    case 'tagName':
      return By.tagName(target)
    case 'linkText':
      return By.linkText(target)
    case 'partialLinkText':
      return By.partialLinkText(target)
    case 'xpath':
      return By.xpath(target)
    case 'css':
    default:
      return By.css(target)
  }
}

/** 拼接 URL：相对路径拼接 baseUrl，完整 URL 直接使用 */
function resolveUrl(url: string | undefined, baseUrl: string): string {
  if (!url) return baseUrl
  if (/^https?:\/\//i.test(url)) return url
  return `${baseUrl.replace(/\/$/, '')}/${url.replace(/^\//, '')}`
}

/** 执行单个步骤，返回该步骤结果 */
async function executeStep(driver: WebDriver, step: UiStep, baseUrl: string): Promise<UiStepResult> {
  try {
    switch (step.action) {
      case 'open': {
        const url = resolveUrl(step.target, baseUrl)
        await driver.get(url)
        return { action: 'open', target: url, status: 'PASS', message: `打开页面成功：${url}` }
      }
      case 'click': {
        const el = await driver.findElement(buildLocator(step))
        await el.click()
        return { action: 'click', target: step.target, status: 'PASS', message: '点击成功' }
      }
      case 'type': {
        const el = await driver.findElement(buildLocator(step))
        await el.clear()
        await el.sendKeys(step.value ?? '')
        return { action: 'type', target: step.target, status: 'PASS', message: '输入成功' }
      }
      case 'assertText': {
        const el = await driver.findElement(buildLocator(step))
        const text = await el.getText()
        const expected = step.value ?? ''
        if (text.includes(expected)) {
          return { action: 'assertText', target: step.target, status: 'PASS', message: `文本匹配："${text}"` }
        }
        return {
          action: 'assertText',
          target: step.target,
          status: 'FAIL',
          message: `文本不匹配：期望 "${expected}"，实际 "${text}"`,
        }
      }
      case 'assertExists': {
        await driver.findElement(buildLocator(step))
        return { action: 'assertExists', target: step.target, status: 'PASS', message: '元素存在' }
      }
      case 'assertTitle': {
        const title = await driver.getTitle()
        const expected = step.value ?? ''
        if (title.includes(expected)) {
          return { action: 'assertTitle', status: 'PASS', message: `标题匹配："${title}"` }
        }
        return {
          action: 'assertTitle',
          status: 'FAIL',
          message: `标题不匹配：期望 "${expected}"，实际 "${title}"`,
        }
      }
      case 'wait': {
        const timeout = Number(step.value ?? 5) * 1000
        await driver.wait(until.elementLocated(buildLocator(step)), timeout)
        return { action: 'wait', target: step.target, status: 'PASS', message: '元素已出现' }
      }
      default:
        return { action: step.action, target: step.target, status: 'ERROR', message: '未知步骤类型' }
    }
  } catch (err) {
    // 出错时截图，便于定位问题
    let screenshot: string | undefined
    try {
      screenshot = await driver.takeScreenshot()
    } catch {
      // 截图失败忽略
    }
    const message = err instanceof Error ? err.message : String(err)
    return { action: step.action, target: step.target, status: 'ERROR', message, screenshot }
  }
}

/** 创建 Chrome WebDriver 会话 */
function buildDriver(chromedriverPath?: string): WebDriver {
  const builder = new Builder().forBrowser('chrome')
  const chromedriver = chromedriverPath ?? resolveChromedriver()
  if (chromedriver) {
    // 显式指定 chromedriver 路径（否则用 Selenium Manager 自动管理）
    builder.setChromeService(new chrome.ServiceBuilder(chromedriver))
  }
  return builder.build()
}

/** 在给定 driver 上依次执行一组步骤，失败/出错后默认停止 */
async function executeStepsOnDriver(
  driver: WebDriver,
  steps: UiStep[],
  baseUrl: string,
  continueOnError = false,
): Promise<UiStepResult[]> {
  const results: UiStepResult[] = []
  for (const step of steps) {
    const result = await executeStep(driver, step, baseUrl)
    results.push(result)
    if (result.status !== 'PASS' && !continueOnError) break
  }
  return results
}

export interface RunUiOptions {
  baseUrl?: string
  /** chromedriver 可执行文件路径（可选，默认用 Selenium Manager 自动管理） */
  chromedriverPath?: string
  /** 遇到失败/错误步骤时是否继续执行后续步骤（默认停止） */
  continueOnError?: boolean
}

/**
 * 执行单个 UI 测试用例的一组步骤，返回每步结果。
 */
export async function runUiSteps(steps: UiStep[], options: RunUiOptions = {}): Promise<UiStepResult[]> {
  const driver = buildDriver(options.chromedriverPath)
  try {
    return await executeStepsOnDriver(driver, steps, options.baseUrl ?? '', options.continueOnError)
  } finally {
    await driver.quit()
  }
}

/** UI 场景中的一个用例（含其步骤） */
export interface UiScenarioCase {
  id: string
  name: string
  baseUrl?: string | null
  steps: UiStep[]
}

/** UI 场景中单个用例的执行结果 */
export interface UiScenarioCaseResult {
  testCaseId: string
  name: string
  status: 'PASS' | 'FAIL' | 'ERROR'
  steps: UiStepResult[]
}

/**
 * 执行 UI 场景：在【同一个浏览器会话】中按顺序执行多个用例的步骤，
 * 登录态/页面状态在用例间保持；返回每个用例的执行结果。
 */
export async function runUiScenario(
  cases: UiScenarioCase[],
  options: { chromedriverPath?: string } = {},
): Promise<UiScenarioCaseResult[]> {
  const driver = buildDriver(options.chromedriverPath)
  const results: UiScenarioCaseResult[] = []
  try {
    for (const tc of cases) {
      const stepResults = await executeStepsOnDriver(driver, tc.steps, tc.baseUrl ?? '')
      const status = stepResults.every((r) => r.status === 'PASS')
        ? 'PASS'
        : stepResults.some((r) => r.status === 'ERROR')
          ? 'ERROR'
          : 'FAIL'
      results.push({ testCaseId: tc.id, name: tc.name, status, steps: stepResults })
    }
  } finally {
    await driver.quit()
  }
  return results
}
