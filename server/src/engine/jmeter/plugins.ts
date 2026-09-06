/**
 * JMeter 第三方插件目录与可用性探测。
 *
 * 平台不"内嵌"插件逻辑，而是让 .jmx 里用到的插件元件在运行期被 JMeter 加载：
 * 因此必须知道 lib/ext 下到底有哪些元件，才能
 *   1) 决定平台可以生成/接受哪些元件（如阶梯加压线程组）；
 *   2) 在缺失时给出准确的安装指引，而不是运行到一半报错；
 *   3) 区分"只有 GUI 监听器"与"CLI 下真正可用"的能力。
 *
 * 探测方式：扫描 lib/ext/*.jar 的 ZIP 中央目录条目名（见 zip.ts），匹配 marker 类。
 * 结果按 jar 的规模+修改时间签名缓存，插件增删后自动失效。
 */
import { existsSync, readdirSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { locateJmeter } from './runner.js'
import { listZipEntries } from './zip.js'

export type PerfPluginState = 'installed' | 'partial' | 'missing'

export interface PerfPluginDef {
  /** 稳定 id，供前端与安装脚本使用 */
  id: string
  name: string
  /** 分类：load 负载模型 / monitor 监控图表 / protocol 协议 / data 数据处理 / tool 工具 */
  category: 'load' | 'monitor' | 'protocol' | 'data' | 'tool'
  /** 上游安装包名（jmeter-plugins.org 的包 id），用于安装脚本 */
  packageId?: string
  /** Maven 坐标（不在 jmeter-plugins.org 仓库里的第三方插件） */
  maven?: { groupId: string; artifactId: string }
  /** 判定依据：以 .class 结尾按条目精确匹配，否则按条目名子串匹配 */
  markers: string[]
  /** 完整可用还需要的额外标记（缺失则状态降级为 partial） */
  requires?: string[]
  /** 需要的目录（相对 JMeter home），如报告模板 */
  requiresDir?: string
  /** 平台已把该插件的哪一项能力做成了功能 */
  capabilities: string[]
  /** 是否只在 JMeter GUI 下生效（CLI 无界面模式不渲染） */
  guiOnly?: boolean
  note: string
}

export interface PerfPluginStatus extends Omit<PerfPluginDef, 'markers'> {
  state: PerfPluginState
  jars: string[]
}

/**
 * 插件目录。覆盖常见 JMeter 第三方插件（jmeter-plugins.org / Maven Central）。
 * markers 取自本机 jar 实测类名，避免臆测。
 */
export const PERF_PLUGIN_CATALOG: PerfPluginDef[] = [
  {
    id: 'thread-stepping',
    name: 'Stepping / Ultimate Thread Group（阶梯加压）',
    category: 'load',
    packageId: 'jpgc-casutg',
    markers: ['kg/apc/jmeter/threads/SteppingThreadGroup.class', 'kg/apc/jmeter/threads/UltimateThreadGroup.class'],
    capabilities: ['加压方式=阶梯加压：按批次递增并发并可选突增减'],
    note: '平台「创建用例 → 加压方式」已支持阶梯加压，生成与回读都会使用该元件。',
  },
  {
    id: 'thread-concurrency',
    name: 'Concurrency / Arrivals Thread Group（目标并发·到达率）',
    category: 'load',
    packageId: 'jpgc-casutg',
    markers: [
      'com/blazemeter/jmeter/threads/concurrency/ConcurrencyThreadGroup.class',
      'com/blazemeter/jmeter/threads/arrivals/ArrivalsThreadGroup.class',
    ],
    capabilities: ['加压方式=目标并发：按 RPS/到达率自动调节线程数逼近目标'],
    note: '平台「创建用例 → 加压方式」已支持目标并发（阶梯逼近）。',
  },
  {
    id: 'graph-basic',
    name: '3 Basic Graphs（活跃线程数 / 响应时间 / TPS）',
    category: 'monitor',
    packageId: 'jpgc-graphs-basic',
    markers: ['kg/apc/jmeter/vizualizers/ThreadsStateOverTimeGui.class'],
    guiOnly: true,
    capabilities: ['平台报告页已用 JTL 原生重绘这三张图（活跃线程数曲线、响应时间趋势、TPS 趋势）'],
    note: '该插件是 JMeter GUI 实时监听器；无界面执行不渲染。平台直接从 JTL 计算同样指标，无需打开 GUI。',
  },
  {
    id: 'graph-additional',
    name: '5 Additional Graphs（响应码分布 / 百分位 / 带宽等）',
    category: 'monitor',
    packageId: 'jpgc-graphs-additional',
    markers: ['kg/apc/jmeter/vizualizers/ResponseCodesPerSecondGui.class', 'kg/apc/jmeter/vizualizers/ResponseTimesPercentilesGui.class'],
    guiOnly: true,
    capabilities: ['平台报告页已提供每秒响应码分布与 P90/P95/P99 百分位'],
    note: '同上：GUI 监听器。平台以 JTL 计算 + 官方 HTML 报告覆盖其全部图表内容。',
  },
  {
    id: 'html-dashboard',
    name: 'JMeter 官方 HTML 报告（APDEX / Over Time / Percentiles / Response Codes）',
    category: 'monitor',
    markers: ['org/apache/jmeter/report/processor/graph/impl/TransactionsPerSecondGraphConsumer.class'],
    requiresDir: join('bin', 'report-template'),
    capabilities: ['每次压测自动生成官方 HTML 报告，报告页可直接打开'],
    note: 'CLI 下获得那一整套图表的官方途径（jmeter -e -o report），已由执行器默认开启。',
  },
  {
    id: 'perfmon',
    name: 'PerfMon Metrics Collector（被压机 CPU / 内存 / 磁盘 / 网络）',
    category: 'monitor',
    packageId: 'jpgc-perfmon',
    markers: ['kg/apc/jmeter/perfmon/PerfMonCollector.class'],
    requires: ['kg/apc/jmeter/perfmon/measurements/'],
    capabilities: [],
    note: '需完整安装 jpgc-perfmon（采集器 + 各类测量元件），并在被监控主机上运行 ServerAgent（默认端口 4444）。平台尚未把监控项纳入用例模型。',
  },
  {
    id: 'inter-thread-comm',
    name: 'Inter-Thread Communication（FIFO 跨线程传值）',
    category: 'data',
    packageId: 'jpgc-fifo',
    markers: ['kg/apc/jmeter/modifiers/FifoPutPostProcessor.class', 'kg/apc/jmeter/modifiers/FifoPopPreProcessor.class'],
    capabilities: [],
    note: '已可随 .jmx 执行（JMeter 自动加载）；平台结构化步骤暂未提供 FIFO 元件编辑。',
  },
  {
    id: 'functions',
    name: 'Additional Functions（__StrLen / __ChooseRandom / __Fifo* 等）',
    category: 'data',
    packageId: 'jpgc-functions',
    markers: ['kg/apc/jmeter/functions/StrLen.class'],
    capabilities: ['URL / 参数 / 请求体中可直接使用这些函数'],
    note: '函数由 JMeter 在执行期求值，平台无需额外支持即可使用。',
  },
  {
    id: 'json-extractor',
    name: 'JSON / JMESPath Extractor',
    category: 'data',
    markers: ['org/apache/jmeter/extractor/json/jsonpath/JSONPostProcessor.class'],
    capabilities: [],
    note: 'JMeter 5.x 核心已内置 JSONPath 与 JMESPath 提取器，无需额外插件。',
  },
  {
    id: 'plugin-manager',
    name: 'Plugins Manager（插件包内安装器）',
    category: 'tool',
    markers: ['org/jmeterplugins/repository/'],
    capabilities: [],
    note: '在 JMeter GUI 里 Options → Plugins Manager 可继续安装其它插件；安装后需重启后端以重新探测。',
  },
  {
    id: 'mqtt',
    name: 'MQTT Sampler（物联网消息协议）',
    category: 'protocol',
    markers: ['mqtt/'],
    capabilities: [],
    note: '未安装。安装后可在 JMeter 中编辑 MQTT 计划；平台结构化编辑器目前只覆盖 HTTP 请求。',
  },
  {
    id: 'amqp',
    name: 'AMQP Sampler（RabbitMQ 等消息队列）',
    category: 'protocol',
    markers: ['amqp/'],
    capabilities: [],
    note: '未安装。同上，属第三方协议取样器。',
  },
  {
    id: 'dubbo',
    name: 'Dubbo Sampler（RPC 接口压测）',
    category: 'protocol',
    maven: { groupId: 'io.metersphere', artifactId: 'jmeter-plugins-dubbo' },
    markers: ['dubbo/'],
    capabilities: [],
    note: '未安装。MeterSphere 官方同款 Dubbo 取样器已在 Maven Central 发布，可用安装脚本放入 lib/ext。',
  },
]

/** jar 条目名集合缓存 */
let cache: { key: string; entries: Set<string>; jars: Map<string, string> } | undefined

function extJars(home: string): string[] {
  const dir = join(home, 'lib', 'ext')
  try {
    return readdirSync(dir)
      .filter((f) => f.toLowerCase().endsWith('.jar'))
      .map((f) => join(dir, f))
  } catch {
    return []
  }
}

function scanEntries(home: string): { entries: Set<string>; jars: Map<string, string> } {
  const jars = extJars(home)
  const key = jars
    .map((p) => {
      try {
        const s = statSync(p)
        return `${p}:${s.size}:${Math.round(s.mtimeMs)}`
      } catch {
        return p
      }
    })
    .join('|')
  if (cache && cache.key === key) return cache
  const entries = new Set<string>()
  const owner = new Map<string, string>()
  for (const jar of jars) {
    const name = jar.split(/[\\/]/).pop() ?? jar
    for (const e of listZipEntries(jar)) {
      entries.add(e)
      if (!owner.has(e)) owner.set(e, name)
    }
  }
  cache = { key, entries, jars: owner }
  return cache
}

function matchMarker(entries: Set<string>, marker: string): string | undefined {
  if (marker.endsWith('.class')) return entries.has(marker) ? marker : undefined
  // 目录/前缀或子串匹配
  for (const e of entries) if (e.includes(marker)) return e
  return undefined
}

export interface PerfPluginReport {
  available: boolean
  home?: string
  source?: 'embedded' | 'env' | 'path'
  plugins: PerfPluginStatus[]
  counts: { installed: number; partial: number; missing: number }
}

/** 探测当前 JMeter 运行时里可用的插件（结果缓存，插件变化自动失效） */
export function detectPlugins(): PerfPluginReport {
  const located = locateJmeter()
  const build = (
    entries?: Set<string>,
    jars?: Map<string, string>,
  ): PerfPluginStatus[] =>
    PERF_PLUGIN_CATALOG.map((def) => {
      let state: PerfPluginState = 'missing'
      const hitJars = new Set<string>()
      if (entries) {
        const hits = def.markers.map((m) => matchMarker(entries, m)).filter(Boolean) as string[]
        const anyHit = def.markers.length === 0 ? true : hits.length > 0
        if (anyHit) {
          const missingRequires = (def.requires ?? []).filter((r) => !matchMarker(entries, r)).length > 0
          const missingDir = def.requiresDir ? !existsSync(resolve(located?.home ?? '', def.requiresDir)) : false
          state = missingRequires || missingDir ? 'partial' : 'installed'
        }
        for (const h of hits) {
          const jar = jars?.get(h)
          if (jar) hitJars.add(jar)
        }
      }
      const { markers: _markers, ...rest } = def
      return { ...rest, state, jars: [...hitJars].sort() }
    })

  if (!located) {
    const plugins = build()
    return {
      available: false,
      plugins,
      counts: {
        installed: plugins.filter((p) => p.state === 'installed').length,
        partial: plugins.filter((p) => p.state === 'partial').length,
        missing: plugins.filter((p) => p.state === 'missing').length,
      },
    }
  }

  const scanned = scanEntries(located.home)
  const plugins = build(scanned.entries, scanned.jars)
  return {
    available: true,
    home: located.home,
    source: located.source,
    plugins,
    counts: {
      installed: plugins.filter((p) => p.state === 'installed').length,
      partial: plugins.filter((p) => p.state === 'partial').length,
      missing: plugins.filter((p) => p.state === 'missing').length,
    },
  }
}

/** 某插件是否完整可用（生成 .jmx 前的能力门控） */
export function isPluginReady(id: string): boolean {
  return detectPlugins().plugins.find((p) => p.id === id)?.state === 'installed'
}
