/**
 * 报告导出 PDF。
 *
 * 采用「浏览器打印为 PDF」的方式：把目标报告 DOM 克隆到一个新窗口，补上本应用样式表后调用
 * window.print()，用户选择「另存为 PDF」即可得到高保真 PDF（图表、表格、截图均按浏览器原生渲染）。
 * 相比 html2canvas 截图，图表（@ant-design/plots 的 canvas）与长表格在多页分页下更清晰。
 *
 * 关键点：canvas 位图不会被 cloneNode 序列化，因此克隆后要把可见 canvas 转成 <img> 再导出。
 */
import { message } from 'antd'

const PRINT_STYLE = `
  * { -webkit-print-color-adjust: exact !important; print-color-adjust: exact !important; }
  body { padding: 20px 24px; background: #fff; color: #000; font-size: 13px; }
  .ant-card { margin-bottom: 12px; border: 1px solid #eee; }
  .ant-statistic-content { font-size: 22px; }
  table { width: 100%; border-collapse: collapse; }
  .ant-table { overflow: visible; }
  .ant-table-container { overflow: visible; }
  tr { page-break-inside: avoid; }
  thead { display: table-header-group; }
  img { max-width: 100%; }
  .ant-btn, .ant-pagination, .ant-select-arrow, .ant-table-column-sorters { display: none !important; }
`

function stylesOf(): string {
  const nodes = Array.from(document.querySelectorAll('link[rel="stylesheet"], style'))
  return nodes.map((n) => n.outerHTML).join('\n')
}

/** 把目标节点里的可见 canvas（图表）替换为同尺寸 <img>，保证打印窗口能渲染出图表 */
function replaceCanvases(clone: HTMLElement, source: HTMLElement): void {
  const srcs = Array.from(source.querySelectorAll('canvas'))
  const dsts = Array.from(clone.querySelectorAll('canvas'))
  dsts.forEach((dst, i) => {
    const src = srcs[i]
    if (!src) return
    if (src.offsetWidth === 0 || src.offsetHeight === 0) {
      dst.remove()
      return
    }
    try {
      const dataUrl = src.toDataURL('image/png')
      const img = document.createElement('img')
      img.src = dataUrl
      const style = src.getAttribute('style')
      if (style) img.setAttribute('style', style)
      if (style && !style.includes('width')) img.style.width = `${src.offsetWidth}px`
      if (style && !style.includes('height')) img.style.height = `${src.offsetHeight}px`
      dst.replaceWith(img)
    } catch {
      /* 个别 canvas 无法导出（如被跨域污染）时保留原样 */
    }
  })
}

/** 导出指定 DOM 节点为 PDF（触发浏览器打印，可选择「另存为 PDF」） */
export function exportPdf(title: string, element: HTMLElement | null | undefined): void {
  if (!element) {
    message.warning('没有可导出的内容')
    return
  }
  const clone = element.cloneNode(true) as HTMLElement
  replaceCanvases(clone, element)

  const win = window.open('', '_blank', 'width=1100,height=820')
  if (!win) {
    message.warning('浏览器拦截了弹出窗口，请允许本站弹出窗口后再试')
    return
  }

  const bodyHtml = clone.outerHTML
  win.document.open()
  win.document.write(
    `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8" /><title>${escapeHtml(title)}</title>` +
      `${stylesOf()}<style>${PRINT_STYLE}</style></head>` +
      `<body>${bodyHtml}<div style="margin-top:16px;color:#999;font-size:12px;text-align:center">` +
      `导出时间：${new Date().toLocaleString()}　·　在打印对话框中选择「另存为 PDF」即可保存</div></body></html>`,
  )
  win.document.close()

  let printed = false
  const doPrint = () => {
    if (printed) return
    printed = true
    try {
      win.focus()
      win.print()
    } catch {
      /* ignore */
    }
  }
  // 等待样式表与内联图片就绪后再打印
  if (win.document.readyState === 'complete') {
    setTimeout(doPrint, 250)
  } else {
    win.addEventListener('load', () => setTimeout(doPrint, 250), { once: true })
  }
  // 兜底：某些浏览器 load 不触发时也能打印
  setTimeout(doPrint, 1200)
}

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] as string)
}
