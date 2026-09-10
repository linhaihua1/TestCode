<!--
  应用根组件。

  职责：把 Ant Design Vue 的 ConfigProvider 提到最顶层，注入全局主题
  （见 styles/antTheme.ts）。这样所有路由页面内的 antd 组件都会自动
  应用统一的品牌色、圆角、字体与阴影，无需逐页配置。
-->
<template>
  <a-config-provider
    :theme="antTheme"
    :locale="zhCN"
    :wave="{ disabled: false }"
  >
    <router-view />
  </a-config-provider>
</template>

<script setup lang="ts">
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { antTheme } from '@/styles/antTheme'
</script>
