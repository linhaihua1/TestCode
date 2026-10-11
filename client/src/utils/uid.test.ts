import { describe, it, expect } from 'vitest';
import { uid, ensureIds } from '@/utils/uid';

describe('uid 拖拽唯一标识生成器', () => {
  it('每次生成唯一且非空', () => {
    const a = uid();
    const b = uid();
    const c = uid();
    expect(a).toBeTruthy();
    expect(b).toBeTruthy();
    expect(c).toBeTruthy();
    expect(new Set([a, b, c]).size).toBe(3);
  });

  it('生成值以 d 开头（36 进制时间戳约定）', () => {
    expect(uid().startsWith('d')).toBe(true);
  });

  describe('ensureIds', () => {
    // ensureIds 动态注入 _id，测试用显式类型标注可选 _id 字段
    type Row = { name: string; _id?: string };

    it('为缺失 _id 的元素补齐唯一 id', () => {
      const list: Row[] = [{ name: 'a' }, { name: 'b' }];
      ensureIds(list);
      expect(list[0]._id).toBeTruthy();
      expect(list[1]._id).toBeTruthy();
      expect(list[0]._id).not.toBe(list[1]._id);
    });

    it('已有 _id 的元素保持不变（幂等）', () => {
      const list: Row[] = [{ _id: 'existing', name: 'a' }, { name: 'b' }];
      ensureIds(list);
      expect(list[0]._id).toBe('existing');
      expect(list[1]._id).toBeTruthy();
    });

    it('重复调用不改变已有 id', () => {
      const list: Row[] = [{ name: 'a' }];
      ensureIds(list);
      const first = list[0]._id;
      ensureIds(list);
      expect(list[0]._id).toBe(first);
    });

    it('空数组不报错', () => {
      const list: unknown[] = [];
      expect(() => ensureIds(list)).not.toThrow();
    });
  });
});
