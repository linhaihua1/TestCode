/**
 * 时间 / 数值格式化工具（统一 UTC → 本地时区转换，避免各页面各写一套）。
 *
 * <p>后端所有时间字段以 UTC 存储（{@code serverTimezone=UTC}），
 * 前端展示前必须用 {@code dayjs.utc(x).local()} 转成本地时间。
 */
import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/zh-cn';

dayjs.extend(utc);
dayjs.extend(relativeTime);
dayjs.locale('zh-cn');

/** 空值占位符 */
export const EMPTY_PLACEHOLDER = '-';

/** 默认日期时间格式 */
export const DATETIME_FORMAT = 'YYYY-MM-DD HH:mm:ss';

/** 分钟精度格式：列表页空间紧张时使用 */
export const DATETIME_FORMAT_MINUTE = 'YYYY-MM-DD HH:mm';

/**
 * UTC 时间字符串 → 本地时间文本。
 *
 * @param iso UTC ISO 字符串；为空返回占位符
 * @param format 输出格式，默认 YYYY-MM-DD HH:mm:ss
 */
export function formatDateTime(iso?: string | null, format: string = DATETIME_FORMAT): string {
  if (!iso) return EMPTY_PLACEHOLDER;
  const d = dayjs.utc(iso).local();
  return d.isValid() ? d.format(format) : String(iso);
}

/**
 * 仅日期部分（YYYY-MM-DD）。
 */
export function formatDate(iso?: string | null): string {
  return formatDateTime(iso, 'YYYY-MM-DD');
}

/**
 * 相对时间（如「3 分钟前」）。
 */
export function fromNow(iso?: string | null): string {
  if (!iso) return EMPTY_PLACEHOLDER;
  const d = dayjs.utc(iso).local();
  return d.isValid() ? d.fromNow() : String(iso);
}

/**
 * UTC 时间字符串 → 本地时间毫秒时间戳。
 *
 * <p>统一入口，避免直接 {@code new Date(iso)}（无时区后缀会被当成本地时间，导致偏差）。
 */
export function toLocalMillis(iso?: string | null): number {
  if (!iso) return 0;
  const d = dayjs.utc(iso).local();
  return d.isValid() ? d.valueOf() : 0;
}

/**
 * 是否已过期（早于当前时间）。
 */
export function isExpired(iso?: string | null): boolean {
  const ms = toLocalMillis(iso);
  return ms > 0 && ms < Date.now();
}

/**
 * 毫秒数 → 可读耗时（如 1.2s / 350ms）。
 */
export function formatDuration(ms?: number | null): string {
  if (ms == null) return EMPTY_PLACEHOLDER;
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
}

/**
 * 大数字千分位。
 */
export function formatNumber(n?: number | null): string {
  if (n == null) return EMPTY_PLACEHOLDER;
  return n.toLocaleString('zh-CN');
}
