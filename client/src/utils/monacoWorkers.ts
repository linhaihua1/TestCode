/**
 * Monaco 编辑器 Worker 配置（Vite 版）。
 *
 * <h3>为什么必须单独配置</h3>
 * <p>{@code monaco-editor} 的 ESM 包不会自带 Worker 加载逻辑，需要宿主通过
 * {@code self.MonacoEnvironment.getWorker} 告诉它「某种语言用哪个 worker」。
 * 未配置时 Monaco 会退到默认实现并抛
 * {@code Error: Unexpected usage}（见 defaultWorkerFactory 的 loadForeignModule），
 * 表现为控制台报错，且 JSON / TS 的语法校验、代码补全等能力不可用。</p>
 *
 * <h3>Vite 约定</h3>
 * <p>用 {@code ?worker} 后缀导入，Vite 会把每个 worker 单独打包并在 dev / build
 * 两种模式下都给到可用的 URL，无需手工处理 worker 路径。</p>
 *
 * <p>本模块只负责副作用（注册 MonacoEnvironment），在编辑器组件里 import 一次即可。</p>
 */
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import jsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker'
import cssWorker from 'monaco-editor/esm/vs/language/css/css.worker?worker'
import htmlWorker from 'monaco-editor/esm/vs/language/html/html.worker?worker'
import tsWorker from 'monaco-editor/esm/vs/language/typescript/ts.worker?worker'

/** 语言标签 → worker 构造器（同一 worker 可服务多个近似语言） */
const WORKER_BY_LABEL: Record<string, () => Worker> = {
  json: () => new jsonWorker(),
  css: () => new cssWorker(),
  scss: () => new cssWorker(),
  less: () => new cssWorker(),
  html: () => new htmlWorker(),
  handlebars: () => new htmlWorker(),
  razor: () => new htmlWorker(),
  typescript: () => new tsWorker(),
  javascript: () => new tsWorker()
}

self.MonacoEnvironment = {
  /**
   * 按语言标签返回对应 Worker。
   *
   * @param _workerId worker 实例 id（Monaco 内部使用，这里不需要）
   * @param label 语言标签（如 json / typescript / css）
   * @returns 该语言对应的 Worker，未登记的语言统一回退到基础编辑器 worker
   */
  getWorker(_workerId: string, label: string): Worker {
    const create = WORKER_BY_LABEL[label]
    return create ? create() : new editorWorker()
  }
}
