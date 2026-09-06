/**
 * 极简 ZIP/JAR 条目读取（零依赖）。
 *
 * 用途：JMeter 插件是否可用，取决于 `lib/ext/*.jar` 里是否存在对应元件类。这里只解析
 * ZIP 的「中央目录」拿到条目名，不解压内容，因此对几十 MB 的 jar 也很快。
 * 同时提供按名提取单条目的能力（安装脚本解 zip 包时使用）。
 *
 * 参考 PKZIP APPNOTE 结构：Local Header / Central Directory / End Of Central Directory。
 */
import { closeSync, fstatSync, openSync, readSync } from 'node:fs'
import { inflateRawSync } from 'node:zlib'

const EOCD_SIG = 0x06054b50 // "PK\x05\x06"
const CEN_SIG = 0x02014b50 // "PK\x01\x02"
const LOC_SIG = 0x04034b50 // "PK\x03\x04"
const EOCD_MIN = 22
const MAX_comment = 0xffff

function readUInt16(buf: Buffer, off: number): number {
  return buf.readUInt16LE(off)
}
function readUInt32(buf: Buffer, off: number): number {
  return buf.readUInt32LE(off)
}

/** 列出 zip/jar 内的全部条目名（目录条目以 / 结尾） */
export function listZipEntries(path: string): string[] {
  const out: string[] = []
  let fd = -1
  try {
    fd = openSync(path, 'r')
    const size = fstatSync(fd).size
    if (size < EOCD_MIN) return out
    fd = openSync(path, 'r')
    const tailLen = Math.min(size, EOCD_MIN + MAX_comment)
    const tail = Buffer.alloc(tailLen)
    readSync(fd, tail, 0, tailLen, size - tailLen)
    let pos = -1
    for (let i = tail.length - EOCD_MIN; i >= 0; i--) {
      if (readUInt32(tail, i) === EOCD_SIG) {
        const commentLen = readUInt16(tail, i + 20)
        if (i + EOCD_MIN + commentLen === tailLen) {
          pos = i
          break
        }
      }
    }
    if (pos < 0) return out
    const entries = readUInt16(tail, pos + 10)
    const cdSize = readUInt32(tail, pos + 12)
    const cdOffset = readUInt32(tail, pos + 16)
    if (cdOffset + cdSize > size) return out
    const cd = Buffer.alloc(cdSize)
    readSync(fd, cd, 0, cdSize, cdOffset)
    let off = 0
    for (let n = 0; n < entries && off + 46 <= cd.length; n++) {
      if (readUInt32(cd, off) !== CEN_SIG) break
      const nameLen = readUInt16(cd, off + 28)
      const extraLen = readUInt16(cd, off + 30)
      const commentLen = readUInt16(cd, off + 32)
      out.push(cd.toString('utf8', off + 46, off + 46 + nameLen))
      off += 46 + nameLen + extraLen + commentLen
    }
    return out
  } catch {
    return out
  } finally {
    if (fd >= 0) closeSync(fd)
  }
}

/**
 * 提取单个条目内容（store / deflate 两种压缩方式）。找不到返回 undefined。
 * 只用于安装小体积的 zip 包（插件包 / ServerAgent）。
 */
export function extractZipEntry(path: string, entryName: string): Buffer | undefined {
  let fd = -1
  try {
    fd = openSync(path, 'r')
    const size = fstatSync(fd).size
    if (size < EOCD_MIN) return undefined
    const tailLen = Math.min(size, EOCD_MIN + MAX_comment)
    const tail = Buffer.alloc(tailLen)
    readSync(fd, tail, 0, tailLen, size - tailLen)
    let pos = -1
    for (let i = tail.length - EOCD_MIN; i >= 0; i--) {
      if (readUInt32(tail, i) === EOCD_SIG) {
        const commentLen = readUInt16(tail, i + 20)
        if (i + EOCD_MIN + commentLen === tailLen) {
          pos = i
          break
        }
      }
    }
    if (pos < 0) return undefined
    const entries = readUInt16(tail, pos + 10)
    const cdSize = readUInt32(tail, pos + 12)
    const cdOffset = readUInt32(tail, pos + 16)
    const cd = Buffer.alloc(cdSize)
    readSync(fd, cd, 0, cdSize, cdOffset)
    let off = 0
    for (let n = 0; n < entries && off + 46 <= cd.length; n++) {
      if (readUInt32(cd, off) !== CEN_SIG) break
      const method = readUInt16(cd, off + 10)
      const compSize = readUInt32(cd, off + 20)
      const nameLen = readUInt16(cd, off + 28)
      const extraLen = readUInt16(cd, off + 30)
      const commentLen = readUInt16(cd, off + 32)
      const localOff = readUInt32(cd, off + 42)
      const name = cd.toString('utf8', off + 46, off + 46 + nameLen)
      if (name === entryName) {
        // 局部头的 name/extra 长度与中央目录可能不同，需按局部头再算数据起点
        const lh = Buffer.alloc(30)
        readSync(fd, lh, 0, 30, localOff)
        if (readUInt32(lh, 0) !== LOC_SIG) return undefined
        const lhNameLen = readUInt16(lh, 26)
        const lhExtraLen = readUInt16(lh, 28)
        const data = Buffer.alloc(compSize)
        readSync(fd, data, 0, compSize, localOff + 30 + lhNameLen + lhExtraLen)
        return method === 0 ? data : inflateRawSync(data)
      }
      off += 46 + nameLen + extraLen + commentLen
    }
    return undefined
  } catch {
    return undefined
  } finally {
    if (fd >= 0) closeSync(fd)
  }
}
