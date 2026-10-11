import { describe, it, expect } from 'vitest';
import {
  EMPTY_PLACEHOLDER,
  DATETIME_FORMAT,
  formatDateTime,
  formatDate,
  fromNow,
  toLocalMillis,
  isExpired,
  formatDuration,
  formatNumber,
} from '@/utils/format';

describe('format 时间/数值格式化工具', () => {
  describe('formatDateTime', () => {
    it('空值返回占位符', () => {
      expect(formatDateTime(null)).toBe(EMPTY_PLACEHOLDER);
      expect(formatDateTime(undefined)).toBe(EMPTY_PLACEHOLDER);
      expect(formatDateTime('')).toBe(EMPTY_PLACEHOLDER);
    });

    it('合法 UTC 时间转换为本地时间并格式化', () => {
      // 固定 UTC 时间，验证输出格式正确（YYYY-MM-DD HH:mm:ss）
      const result = formatDateTime('2026-01-15T08:30:00Z');
      expect(result).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/);
    });

    it('自定义格式生效', () => {
      expect(formatDateTime('2026-01-15T08:30:00Z', 'YYYY-MM-DD')).toMatch(
        /^\d{4}-\d{2}-\d{2}$/,
      );
    });

    it('非法时间原样返回', () => {
      expect(formatDateTime('not-a-date')).toBe('not-a-date');
    });
  });

  describe('formatDate', () => {
    it('返回仅日期部分', () => {
      expect(formatDate('2026-01-15T08:30:00Z')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    });

    it('空值返回占位符', () => {
      expect(formatDate(null)).toBe(EMPTY_PLACEHOLDER);
    });
  });

  describe('fromNow', () => {
    it('空值返回占位符', () => {
      expect(fromNow(null)).toBe(EMPTY_PLACEHOLDER);
    });

    it('过去时间返回相对时间文本', () => {
      const past = new Date(Date.now() - 3 * 60 * 1000).toISOString();
      const result = fromNow(past);
      expect(typeof result).toBe('string');
      expect(result.length).toBeGreaterThan(0);
    });
  });

  describe('toLocalMillis', () => {
    it('空值返回 0', () => {
      expect(toLocalMillis(null)).toBe(0);
    });

    it('合法时间返回毫秒时间戳（正数）', () => {
      expect(toLocalMillis('2026-01-15T08:30:00Z')).toBeGreaterThan(0);
    });

    it('非法时间返回 0', () => {
      expect(toLocalMillis('invalid')).toBe(0);
    });
  });

  describe('isExpired', () => {
    it('空值不视为过期', () => {
      expect(isExpired(null)).toBe(false);
    });

    it('过去时间视为过期', () => {
      const past = new Date(Date.now() - 60 * 1000).toISOString();
      expect(isExpired(past)).toBe(true);
    });

    it('未来时间不视为过期', () => {
      const future = new Date(Date.now() + 60 * 1000).toISOString();
      expect(isExpired(future)).toBe(false);
    });
  });

  describe('formatDuration', () => {
    it('空值返回占位符', () => {
      expect(formatDuration(null)).toBe(EMPTY_PLACEHOLDER);
      expect(formatDuration(undefined)).toBe(EMPTY_PLACEHOLDER);
    });

    it('小于 1 秒显示毫秒', () => {
      expect(formatDuration(350)).toBe('350ms');
    });

    it('大于等于 1 秒显示秒', () => {
      expect(formatDuration(1200)).toBe('1.20s');
    });
  });

  describe('formatNumber', () => {
    it('空值返回占位符', () => {
      expect(formatNumber(null)).toBe(EMPTY_PLACEHOLDER);
    });

    it('大数字千分位', () => {
      expect(formatNumber(1234567)).toBe('1,234,567');
    });

    it('零值正常显示', () => {
      expect(formatNumber(0)).toBe('0');
    });
  });

  it('导出常量符合预期', () => {
    expect(EMPTY_PLACEHOLDER).toBe('-');
    expect(DATETIME_FORMAT).toBe('YYYY-MM-DD HH:mm:ss');
  });
});
