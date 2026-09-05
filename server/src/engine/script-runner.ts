/**
 * 脚本执行引擎（PRD 自定义代码 / IF 自定义代码）。
 * 支持三种语言：
 *   - JavaScript：进程内沙箱（new Function），提供 context.get/set + console.log
 *   - Python：调用本机 python3 / python / py，脚本通过 context.get/set 读写变量
 *   - Java：调用 javac 编译 + java 运行，脚本通过 context.get/set 读写变量
 *
 * 统一脚本 API（三种语言一致）：
 *   context.get(key)        读取变量
 *   context.set(key, value) 写入变量（作为提取参数提交到用例上下文）
 *   console.log / print / System.out.println  输出日志
 *
 * IF 自定义代码通过预留变量返回判断结果：
 *   context.set('__condition__', 'true' | 'false')
 * 该变量不会提交到用例上下文，仅用于 IF 分支判断。
 */
import { spawn } from 'node:child_process'
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import type { VariableContext } from './types.js'

export type ScriptLang = 'javascript' | 'python' | 'java'

export interface ScriptResult {
  status: 'PASS' | 'ERROR'
  message: string
  extracted: Record<string, string>
  condition?: boolean
}

const CONDITION_KEY = '__condition__'
const SCRIPT_TIMEOUT_MS = 30000

/** 把预留变量的字符串值解析为布尔 */
function toBool(v: string | undefined): boolean | undefined {
  if (v === undefined) return undefined
  const s = v.trim().toLowerCase()
  if (['true', '1', 'yes'].includes(s)) return true
  if (['false', '0', 'no'].includes(s)) return false
  return undefined
}

/** 取出条件并清理预留变量 */
function finalize(extracted: Record<string, string>): { extracted: Record<string, string>; condition?: boolean } {
  const condition = toBool(extracted[CONDITION_KEY])
  delete extracted[CONDITION_KEY]
  return { extracted, condition }
}

/** 子进程执行（带超时、捕获 stdout/stderr、区分 spawn 失败） */
function spawnCapture(
  bin: string,
  args: string[],
  opts: { cwd?: string; env?: Record<string, string>; timeoutMs?: number } = {},
): Promise<{ code: number; stdout: string; stderr: string; spawnError?: string }> {
  return new Promise((resolve) => {
    let child
    try {
      child = spawn(bin, args, {
        cwd: opts.cwd,
        env: { ...process.env, ...(opts.env ?? {}) },
        stdio: ['ignore', 'pipe', 'pipe'],
        windowsHide: true,
      })
    } catch (err) {
      resolve({ code: -1, stdout: '', stderr: '', spawnError: err instanceof Error ? err.message : String(err) })
      return
    }
    let stdout = ''
    let stderr = ''
    let settled = false
    const done = (r: { code: number; stdout: string; stderr: string; spawnError?: string }) => {
      if (settled) return
      settled = true
      resolve(r)
    }
    const timer = setTimeout(() => {
      try {
        child.kill('SIGKILL')
      } catch {
        /* ignore */
      }
      done({ code: -2, stdout, stderr, spawnError: '脚本执行超时' })
    }, opts.timeoutMs ?? SCRIPT_TIMEOUT_MS)
    child.on('error', (err) => {
      clearTimeout(timer)
      done({ code: -1, stdout, stderr, spawnError: err.message })
    })
    child.stdout?.on('data', (d) => {
      stdout += d
    })
    child.stderr?.on('data', (d) => {
      stderr += d
    })
    child.on('close', (code) => {
      clearTimeout(timer)
      done({ code: code ?? -1, stdout, stderr })
    })
  })
}

// ---------------------------------------------------------------------------
// JavaScript（进程内）
// ---------------------------------------------------------------------------
function runJavaScript(script: string, context: VariableContext): ScriptResult {
  const logs: string[] = []
  const extracted: Record<string, string> = {}
  const sandbox = {
    get: (key: string) => context[key],
    set: (key: string, value: unknown) => {
      const s = value === undefined || value === null ? '' : String(value)
      context[key] = s
      extracted[key] = s
    },
  }
  const sandboxConsole = {
    log: (...args: unknown[]) => logs.push(args.map((a) => (typeof a === 'object' ? JSON.stringify(a) : String(a))).join(' ')),
  }
  try {
    // eslint-disable-next-line no-new-func
    new Function('context', 'console', script)(sandbox, sandboxConsole)
    const { extracted: out, condition } = finalize(extracted)
    delete context[CONDITION_KEY] // 预留变量不进入用例上下文
    return { status: 'PASS', message: logs.length > 0 ? logs[logs.length - 1] : '脚本执行成功', extracted: out, condition }
  } catch (err) {
    return { status: 'ERROR', message: err instanceof Error ? err.message : String(err), extracted: {} }
  }
}

// ---------------------------------------------------------------------------
// Python
// ---------------------------------------------------------------------------
const PYTHON_WRAPPER = `# -*- coding: utf-8 -*-
import os, json
class _Ctx:
    def __init__(self, d):
        self._d = d
    def get(self, k, default=None):
        return self._d.get(k, default)
    def set(self, k, v):
        self._d[k] = str(v)
_data = json.load(open(os.environ['API_WEB_CTX'], 'r', encoding='utf-8'))
context = _Ctx(_data)
{SCRIPT}
json.dump(_data, open(os.environ['API_WEB_OUT'], 'w', encoding='utf-8'), ensure_ascii=False)
`

const PYTHON_BINS = ['python3', 'python', 'py']

async function runPython(script: string, context: VariableContext): Promise<ScriptResult> {
  const dir = mkdtempSync(join(tmpdir(), 'apiweb-py-'))
  const ctxPath = join(dir, 'ctx.json')
  const outPath = join(dir, 'out.json')
  const pyPath = join(dir, 'script.py')
  try {
    writeFileSync(ctxPath, JSON.stringify(context), 'utf8')
    writeFileSync(pyPath, PYTHON_WRAPPER.replace('{SCRIPT}', script), 'utf8')

    let result: { code: number; stdout: string; stderr: string; spawnError?: string } | null = null
    for (const bin of PYTHON_BINS) {
      const r = await spawnCapture(bin, [pyPath], { env: { API_WEB_CTX: ctxPath, API_WEB_OUT: outPath } })
      if (!r.spawnError) {
        result = r
        break
      }
    }
    if (!result) {
      return { status: 'ERROR', message: '未检测到 Python 环境（请安装 Python 并加入 PATH）', extracted: {} }
    }
    if (result.spawnError) {
      return { status: 'ERROR', message: `Python 执行失败：${result.spawnError}`, extracted: {} }
    }
    if (result.code !== 0) {
      return { status: 'ERROR', message: result.stderr.trim() || `Python 脚本退出码 ${result.code}`, extracted: {} }
    }
    if (!existsSync(outPath)) {
      return { status: 'ERROR', message: 'Python 脚本未正常产出结果（可能存在语法错误）', extracted: {} }
    }
    const raw: Record<string, string> = JSON.parse(readFileSync(outPath, 'utf8'))
    const { extracted, condition } = finalize(raw)
    return { status: 'PASS', message: result.stdout.trim() || '脚本执行成功', extracted, condition }
  } catch (err) {
    return { status: 'ERROR', message: err instanceof Error ? err.message : String(err), extracted: {} }
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
}

// ---------------------------------------------------------------------------
// Java（javac 编译 + java 运行，通过 tab + base64 传递上下文）
// ---------------------------------------------------------------------------
const JAVA_WRAPPER = `import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
public class ApiWebScript {
  static class Ctx {
    Map<String,String> d = new HashMap<>();
    public String get(String k){ return d.get(k); }
    public void set(String k, Object v){ d.put(k, String.valueOf(v)); }
  }
  public static void main(String[] args) throws Exception {
    Ctx context = new Ctx();
    context.d = readCtx(System.getenv("API_WEB_CTX"));
{SCRIPT}
    writeCtx(System.getenv("API_WEB_OUT"), context.d);
  }
  static Map<String,String> readCtx(String path) throws Exception {
    Map<String,String> m = new HashMap<>();
    for (String line : Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8)) {
      int i = line.indexOf('\\t');
      if (i > 0) m.put(line.substring(0, i), decode(line.substring(i + 1)));
    }
    return m;
  }
  static void writeCtx(String path, Map<String,String> m) throws Exception {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String,String> e : m.entrySet()) sb.append(e.getKey()).append('\\t').append(encode(e.getValue())).append('\\n');
    Files.write(Paths.get(path), sb.toString().getBytes(StandardCharsets.UTF_8));
  }
  static String encode(String s){ return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
  static String decode(String s){ return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8); }
}
`

function encodeCtx(ctx: VariableContext): string {
  return Object.entries(ctx)
    .map(([k, v]) => `${k}\t${Buffer.from(v, 'utf8').toString('base64')}`)
    .join('\n')
}

function decodeCtx(text: string): Record<string, string> {
  const out: Record<string, string> = {}
  for (const line of text.split('\n')) {
    if (!line) continue
    const i = line.indexOf('\t')
    if (i <= 0) continue
    try {
      out[line.slice(0, i)] = Buffer.from(line.slice(i + 1), 'base64').toString('utf8')
    } catch {
      /* 忽略损坏行 */
    }
  }
  return out
}

async function runJava(script: string, context: VariableContext): Promise<ScriptResult> {
  const dir = mkdtempSync(join(tmpdir(), 'apiweb-java-'))
  const ctxPath = join(dir, 'ctx.txt')
  const outPath = join(dir, 'out.txt')
  const javaPath = join(dir, 'ApiWebScript.java')
  try {
    writeFileSync(ctxPath, encodeCtx(context), 'utf8')
    writeFileSync(javaPath, JAVA_WRAPPER.replace('{SCRIPT}', script), 'utf8')

    const javac = await spawnCapture('javac', ['-encoding', 'UTF-8', '-source', '8', '-target', '8', 'ApiWebScript.java'], { cwd: dir })
    if (javac.spawnError) {
      return { status: 'ERROR', message: '未检测到 Java 编译环境（javac，请安装 JDK 并加入 PATH）', extracted: {} }
    }
    if (javac.code !== 0) {
      return { status: 'ERROR', message: javac.stderr.trim() || 'Java 编译失败', extracted: {} }
    }
    const java = await spawnCapture('java', ['-cp', dir, 'ApiWebScript'], { cwd: dir, env: { API_WEB_CTX: ctxPath, API_WEB_OUT: outPath } })
    if (java.code !== 0) {
      return { status: 'ERROR', message: java.stderr.trim() || 'Java 运行失败', extracted: {} }
    }
    if (!existsSync(outPath)) {
      return { status: 'ERROR', message: 'Java 脚本未正常产出结果', extracted: {} }
    }
    const { extracted, condition } = finalize(decodeCtx(readFileSync(outPath, 'utf8')))
    return { status: 'PASS', message: java.stdout.trim() || '脚本执行成功', extracted, condition }
  } catch (err) {
    return { status: 'ERROR', message: err instanceof Error ? err.message : String(err), extracted: {} }
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
}

// ---------------------------------------------------------------------------
// 统一入口
// ---------------------------------------------------------------------------
export async function runCustomScript(script: string, lang: ScriptLang, context: VariableContext): Promise<ScriptResult> {
  if (lang === 'python') return runPython(script, context)
  if (lang === 'java') return runJava(script, context)
  return runJavaScript(script, context)
}
