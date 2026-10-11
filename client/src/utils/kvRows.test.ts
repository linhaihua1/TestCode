import { describe, it, expect } from 'vitest';
import {
  normalizeRows,
  appendEmptyRow,
  removeRowByKey,
  toKeyValueItems,
  type KeyValueRow,
} from '@/utils/kvRows';

/**
 * 键值对行操作纯函数测试（KeyValueEditor 组件的核心逻辑）。
 *
 * <p>这些函数承载了键值对编辑器的增删行与数据清洗契约：
 * 空列表补空行、删除最后一行自动补空行、输出剔除内部 rowKey。
 */
describe('kvRows 键值对行操作', () => {
  describe('normalizeRows', () => {
    it('空列表自动补一空行', () => {
      const rows = normalizeRows([]);
      expect(rows).toHaveLength(1);
      expect(rows[0]).toMatchObject({ key: '', value: '', enabled: true });
      expect(rows[0].rowKey).toBeTruthy();
    });

    it('null/undefined 输入同样补一空行', () => {
      expect(normalizeRows(null)).toHaveLength(1);
      expect(normalizeRows(undefined)).toHaveLength(1);
    });

    it('非空列表逐行生成唯一 rowKey', () => {
      const rows = normalizeRows([
        { key: 'a', value: '1' },
        { key: 'b', value: '2' },
      ]);
      expect(rows).toHaveLength(2);
      expect(rows[0].rowKey).toBeTruthy();
      expect(rows[0].rowKey).not.toBe(rows[1].rowKey);
    });

    it('不修改原始输入（不可变）', () => {
      const input = [{ key: 'a', value: '1' }];
      normalizeRows(input);
      expect(input).toEqual([{ key: 'a', value: '1' }]);
      expect(input[0]).not.toHaveProperty('rowKey');
    });
  });

  describe('appendEmptyRow', () => {
    it('追加一空键值对', () => {
      const rows = normalizeRows([{ key: 'a', value: '1' }]);
      const next = appendEmptyRow(rows);
      expect(next).toHaveLength(2);
      expect(next[1]).toMatchObject({ key: '', value: '', enabled: true });
    });
  });

  describe('removeRowByKey', () => {
    it('删除指定行', () => {
      const rows = normalizeRows([
        { key: 'a', value: '1' },
        { key: 'b', value: '2' },
      ]);
      const next = removeRowByKey(rows, rows[1].rowKey);
      expect(next).toHaveLength(1);
      expect(next[0].key).toBe('a');
    });

    it('删除最后一行后自动补空行', () => {
      const rows = normalizeRows([{ key: 'a', value: '1' }]);
      const next = removeRowByKey(rows, rows[0].rowKey);
      expect(next).toHaveLength(1);
      expect(next[0]).toMatchObject({ key: '', value: '', enabled: true });
    });
  });

  describe('toKeyValueItems', () => {
    it('剔除内部 rowKey，输出纯净键值对', () => {
      const rows = normalizeRows([{ key: 'a', value: '1', enabled: false }]);
      const items = toKeyValueItems(rows);
      expect(items).toHaveLength(1);
      expect(items[0]).toEqual({ key: 'a', value: '1', enabled: false });
      expect(items[0]).not.toHaveProperty('rowKey');
    });

    it('补齐缺失的 enabled 字段为 true', () => {
      const rows: KeyValueRow[] = [{ key: 'x', value: '', rowKey: 'rk' }];
      const items = toKeyValueItems(rows);
      expect(items[0].enabled).toBe(true);
    });
  });
});
