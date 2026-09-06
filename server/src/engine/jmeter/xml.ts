/**
 * 极简 XML 解析器（零依赖），仅用于解析 JMeter 的 .jmx 文件。
 * 支持：元素/属性/文本/CDATA/注释/声明/DOCTYPE，以及常见实体转义。
 */

export interface XmlNode {
  name: string
  attrs: Record<string, string>
  children: XmlNode[]
  text: string
}

/** 解码 XML 实体 */
export function decodeEntities(s: string): string {
  return s
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&#(\d+);/g, (_m, d: string) => String.fromCodePoint(Number(d)))
    .replace(/&#x([0-9a-fA-F]+);/g, (_m, h: string) => String.fromCodePoint(parseInt(h, 16)))
    .replace(/&amp;/g, '&')
}

/** 转义 XML 文本/属性值 */
export function escapeXml(s: string): string {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function makeNode(name: string): XmlNode {
  return { name, attrs: {}, children: [], text: '' }
}

/** 解析 XML 文本为节点树（根节点为文档虚拟根） */
export function parseXml(input: string): XmlNode {
  const root = makeNode('#document')
  const stack: XmlNode[] = [root]
  const src = input.replace(/^\uFEFF/, '')
  let i = 0

  const top = () => stack[stack.length - 1]

  while (i < src.length) {
    const lt = src.indexOf('<', i)
    if (lt === -1) {
      const tail = src.slice(i)
      if (tail.trim()) top().text += decodeEntities(tail)
      break
    }
    if (lt > i) {
      const chunk = src.slice(i, lt)
      if (chunk.trim()) top().text += decodeEntities(chunk)
    }

    // 注释
    if (src.startsWith('<!--', lt)) {
      const end = src.indexOf('-->', lt)
      i = end === -1 ? src.length : end + 3
      continue
    }
    // CDATA
    if (src.startsWith('<![CDATA[', lt)) {
      const end = src.indexOf(']]>', lt)
      const content = src.slice(lt + 9, end === -1 ? src.length : end)
      top().text += content
      i = end === -1 ? src.length : end + 3
      continue
    }
    // 声明 / DOCTYPE / 处理指令
    if (src.startsWith('<?', lt) || src.startsWith('<!', lt)) {
      const end = src.indexOf('>', lt)
      i = end === -1 ? src.length : end + 1
      continue
    }
    // 结束标签
    if (src[lt + 1] === '/') {
      const end = src.indexOf('>', lt)
      const name = src.slice(lt + 2, end === -1 ? src.length : end).trim()
      for (let k = stack.length - 1; k > 0; k--) {
        if (stack[k].name === name) {
          stack.length = k
          break
        }
      }
      i = end === -1 ? src.length : end + 1
      continue
    }

    // 开始标签
    const end = findTagEnd(src, lt)
    if (end === -1) {
      i = src.length
      continue
    }
    const rawTag = src.slice(lt + 1, end)
    const selfClosing = rawTag.endsWith('/')
    const body = selfClosing ? rawTag.slice(0, -1) : rawTag
    const nameMatch = /^([\w:.\-]+)/.exec(body.trimStart())
    if (!nameMatch) {
      i = end + 1
      continue
    }
    const node = makeNode(nameMatch[1])
    const attrRe = /([\w:.\-]+)\s*=\s*"([^"]*)"|([\w:.\-]+)\s*=\s*'([^']*)'/g
    const attrSource = body.slice(nameMatch[1].length)
    let m: RegExpExecArray | null
    while ((m = attrRe.exec(attrSource)) !== null) {
      if (m[1] !== undefined) node.attrs[m[1]] = decodeEntities(m[2])
      else node.attrs[m[3]] = decodeEntities(m[4])
    }
    top().children.push(node)
    if (!selfClosing) stack.push(node)
    i = end + 1
  }

  return root
}

/** 找到标签结束 '>' 的位置（跳过属性值内部的 '>'） */
function findTagEnd(src: string, from: number): number {
  let quote: string | null = null
  for (let i = from + 1; i < src.length; i++) {
    const c = src[i]
    if (quote) {
      if (c === quote) quote = null
      continue
    }
    if (c === '"' || c === "'") quote = c
    else if (c === '>') return i
  }
  return -1
}

/** 递归收集指定名称的后代节点 */
export function findAll(node: XmlNode, name: string): XmlNode[] {
  const out: XmlNode[] = []
  const walk = (n: XmlNode) => {
    for (const c of n.children) {
      if (c.name === name) out.push(c)
      walk(c)
    }
  }
  walk(node)
  return out
}

/** 取直接子节点中指定属性名的 JMeter 属性值（stringProp/boolProp/intProp...） */
export function getProp(node: XmlNode, propName: string): string | undefined {
  for (const c of node.children) {
    if (/(stringProp|boolProp|intProp|longProp|doubleProp)/.test(c.name) && c.attrs.name === propName) {
      return (c.text ?? '').trim()
    }
  }
  return undefined
}

/** 取直接子节点中指定 name 的 elementProp */
export function getElementProp(node: XmlNode, propName: string): XmlNode | undefined {
  return node.children.find((c) => c.name === 'elementProp' && c.attrs.name === propName)
}

/** 取 collectionProp 内的元素列表 */
export function getCollection(node: XmlNode, propName: string): XmlNode[] {
  const col = node.children.find((c) => c.name === 'collectionProp' && c.attrs.name === propName)
  return col ? col.children : []
}
