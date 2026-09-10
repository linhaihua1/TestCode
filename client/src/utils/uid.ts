/**
 * 拖拽排序用唯一标识生成器。
 *
 * <p>vuedraggable 的 {@code item-key} 需要一个稳定且唯一的字段来追踪每个元素，
 * 但断言/提取/步骤等数据对象本身没有 id。这里提供一个轻量 uid 生成器，
 * 在新增行时写入 {@code _id} 字段，供拖拽库稳定追踪（不参与业务序列化语义）。
 */
let seq = 0

export function uid(): string {
  seq += 1
  return `d${Date.now().toString(36)}${seq.toString(36)}`
}

/**
 * 确保数组每个元素都有 {@code _id}（用于从后端加载的旧数据补齐）。
 * 只对缺失 {@code _id} 的元素写入，幂等。
 */
export function ensureIds<T>(list: T[]): void {
  for (const item of list as any[]) {
    if (!item._id) item._id = uid()
  }
}
