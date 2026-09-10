/**
 * Ant Design Vue 主题配置。
 *
 * <p>与 {@code styles/tokens.css} 中的 CSS 变量保持同步：这里管 antd 组件内部的
 * 计算样式（颜色、圆角、字体的派生色阶），tokens.css 管我们自己写的样式。
 * 两者数值必须一致，改一处记得同步另一处。
 *
 * <p>在 App.vue 中通过 {@code <a-config-provider :theme="antTheme">} 注入。
 *
 * <p><b>关于 token 命名：</b>antd v4 的组件级 token 名与 v3 less 变量不同，
 * 也与 antd(React) 文档偶有差异。这里使用的都是本仓库 node_modules 中
 * 类型定义里实际存在的字段（见 {@code es/<component>/style/index.d.ts}），
 * 若升级 antd 需重新核对。
 */
import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'

/** 品牌主色（与 tokens.css 的 --c-primary 一致） */
export const PRIMARY = '#2563eb'

/** 深色导航基色（与 tokens.css 的 --nav-bg 一致） */
export const NAV_BG = '#0f172a'

/**
 * 组件级 token 覆盖。
 *
 * <p><b>为什么这里用 as 断言：</b>antd v4 的 {@code OverrideToken} 是按
 * {@code ComponentTokenMap} 映射的，而部分组件的公开 {@code ComponentToken}
 * 接口只声明了 less→css 迁移期的少量字段，真正生效的运行时 token
 * （如 layout 的 layoutHeaderHeight、table 的 tableHeaderBg）定义在内部的
 * {@code XxxToken extends FullToken<'Xxx'>} 里，未对外导出。
 * 因此类型上会报 "does not exist"，但运行时完全生效。
 * 这些字段名均来自 {@code es/<component>/style/index.d.ts} 的实际声明。
 */
const componentOverrides = {
  /* 布局：深色导航 + 浅灰内容底 */
  Layout: {
    colorBgHeader: NAV_BG,
    colorBgBody: '#f1f5f9',
    colorBgTrigger: '#1e293b',
    layoutHeaderHeight: 52,
    layoutHeaderPaddingInline: 20,
    layoutHeaderColor: '#cbd5e1'
  },

  /* 水平菜单：深色底导航项，选中态用主色块、去掉底部下划线 */
  Menu: {
    colorItemBg: 'transparent',
    colorSubItemBg: 'transparent',
    colorItemText: '#cbd5e1',
    colorItemTextHover: '#f8fafc',
    colorItemBgHover: '#1e293b',
    colorItemTextSelected: '#ffffff',
    colorItemBgSelected: '#1e40af',
    colorItemTextSelectedHorizontal: '#ffffff',
    colorItemBgSelectedHorizontal: '#1e40af',
    colorActiveBarWidth: 0,
    colorActiveBarHeight: 0,
    colorActiveBarBorderSize: 0,
    radiusItem: 8,
    itemMarginInline: 4,
    menuItemHeight: 38
  },

  Table: {
    tableHeaderBg: '#f8fafc',
    tableHeaderTextColor: '#64748b',
    tableHeaderCellSplitColor: 'transparent',
    tableRowHoverBg: '#f1f5f9',
    tableBorderColor: '#f1f5f9',
    tablePaddingVertical: 12,
    tablePaddingHorizontal: 16,
    tableRadius: 8
  },

  Tabs: {
    tabsHoverColor: PRIMARY,
    tabsActiveColor: PRIMARY
  },

  Modal: {
    modalHeaderBg: '#ffffff',
    modalContentBg: '#ffffff',
    modalHeadingColor: '#0f172a',
    modalHeaderTitleFontSize: 16,
    modalBodyPadding: 24,
    modalFooterBg: '#ffffff'
  },

  Input: {
    inputPaddingVertical: 6,
    inputPaddingHorizontal: 11
  },

  Tooltip: {
    colorBgDefault: NAV_BG
  }
} as NonNullable<ThemeConfig['components']>

export const antTheme: ThemeConfig = {
  token: {
    // ---- 品牌色 ----
    colorPrimary: PRIMARY,
    colorSuccess: '#16a34a',
    colorWarning: '#ea580c',
    colorError: '#dc2626',
    colorInfo: '#0891b2',

    // ---- 文字 ----
    colorText: '#334155',
    colorTextHeading: '#0f172a',
    colorTextSecondary: '#64748b',
    colorTextTertiary: '#94a3b8',
    colorTextQuaternary: '#cbd5e1',

    // ---- 背景与边框 ----
    colorBgLayout: '#f1f5f9',
    colorBgContainer: '#ffffff',
    colorBorder: '#e2e8f0',
    colorBorderSecondary: '#f1f5f9',

    // ---- 圆角 ----
    borderRadius: 8,
    borderRadiusSM: 4,
    borderRadiusLG: 12,
    borderRadiusXS: 4,

    // ---- 字体 ----
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', " +
      "'Hiragino Sans GB', 'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial, sans-serif",
    fontSize: 14,
    fontSizeHeading1: 30,
    fontSizeHeading2: 24,
    fontSizeHeading3: 20,
    fontSizeHeading4: 16,
    fontSizeHeading5: 15,

    // ---- 行高与控件高度 ----
    lineHeight: 1.6,
    controlHeight: 34,
    controlHeightLG: 40,
    controlHeightSM: 28,

    // ---- 阴影（用柔和阴影替代 antd 默认） ----
    boxShadow: '0 1px 3px rgba(15, 23, 42, 0.06), 0 1px 2px rgba(15, 23, 42, 0.04)',
    boxShadowSecondary: '0 4px 12px rgba(15, 23, 42, 0.08)',
    boxShadowTertiary: '0 1px 2px rgba(15, 23, 42, 0.04)',

    // ---- 动效 ----
    motionDurationFast: '0.12s',
    motionDurationMid: '0.2s',
    motionDurationSlow: '0.3s',

    // ---- 线宽 ----
    lineWidth: 1,
    lineWidthBold: 2,
    wireframe: false
  },

  components: componentOverrides
}
