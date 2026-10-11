import type { KeyValueItem } from '@/types';

/** 带内部 rowKey 的键值行（仅组件内部使用，不对外暴露） */
export interface KeyValueRow extends KeyValueItem {
  rowKey: string;
}

/** 生成行唯一标识（时间戳 + 随机数，避免同一 tick 内碰撞） */
function makeRowKey(): string {
  return String(Date.now()) + '-' + Math.random();
}

/**
 * 归一化输入键值对列表为内部行结构，并为每行生成 rowKey。
 *
 * <p>空列表自动补一个空行，保证用户始终能继续输入。
 * 这是键值对编辑组件的核心逻辑，抽离为纯函数以便单元测试。</p>
 *
 * @param list 外部传入的键值对列表（可能为 undefined/null）
 * @returns 带 rowKey 的内部行数组，至少一行
 */
export function normalizeRows(list: KeyValueItem[] | null | undefined): KeyValueRow[] {
  const base = list && list.length > 0 ? [...list] : [{ key: '', value: '', enabled: true }];
  return base.map((r) => ({ ...r, rowKey: makeRowKey() }));
}

/**
 * 追加一空行。
 *
 * @param rows 当前行数组
 * @returns 追加后的新数组（不修改入参，保持响应式友好）
 */
export function appendEmptyRow(rows: KeyValueRow[]): KeyValueRow[] {
  return [...rows, { key: '', value: '', enabled: true, rowKey: makeRowKey() }];
}

/**
 * 删除指定 rowKey 的行；删除后若为空则自动补一空行。
 *
 * @param rows 当前行数组
 * @param rowKey 待删除行的标识
 * @returns 删除后的新数组（至少保留一空行）
 */
export function removeRowByKey(rows: KeyValueRow[], rowKey: string): KeyValueRow[] {
  const next = rows.filter((r) => r.rowKey !== rowKey);
  if (next.length === 0) {
    next.push({ key: '', value: '', enabled: true, rowKey: makeRowKey() });
  }
  return next;
}

/**
 * 把内部行数组转换为对外输出的纯净键值对（剔除 rowKey 等内部字段）。
 *
 * @param rows 内部行数组
 * @returns 纯净的 {@link KeyValueItem} 列表
 */
export function toKeyValueItems(rows: KeyValueRow[]): KeyValueItem[] {
  return rows.map(({ key, value, enabled }) => ({
    key: key ?? '',
    value: value ?? '',
    enabled: enabled ?? true,
  }));
}
