<!--
  应用主框架 —— 左侧侧边栏布局。

  <h3>布局结构</h3>
  <ul>
    <li>左侧深色侧边栏：logo + 竖向导航菜单（6 个一级菜单）</li>
    <li>顶部 header：项目切换 + 快捷键帮助 + 用户菜单</li>
    <li>内容区：路由页面</li>
  </ul>

  <h3>菜单结构（一级 → 二级）</h3>
  <ul>
    <li>接口自动化：接口用例 / 接口定义 / 场景编排 / 测试执行 / 测试报告 / 趋势统计</li>
    <li>UI 自动化：UI 用例 / UI 用例执行 / 测试报告</li>
    <li>性能测试：创建用例 / 测试报告</li>
    <li>环境配置：环境管理 / 全局变量 / 变量与模块 / 回收站</li>
    <li>用户管理（管理员）</li>
    <li>操作日志（管理员）</li>
  </ul>
-->
<template>
  <a-layout class="app-layout">
    <!-- ============ 左侧侧边栏 ============ -->
    <a-layout-sider class="sider" width="224" theme="dark">
      <div class="sider__logo" @click="goTo('projects')" title="回到项目列表">
        <span class="sider__logo-icon">⚡</span>
        <span class="sider__logo-name">Api-Web</span>
      </div>

      <a-menu
        class="sider__menu"
        mode="inline"
        theme="dark"
        :selected-keys="selectedKeys"
        v-model:open-keys="openKeys"
        :items="menuItems"
        @select="onMenuSelect"
      />
    </a-layout-sider>

    <!-- ============ 右侧主区域 ============ -->
    <a-layout class="main">
      <!-- 顶部 header -->
      <a-layout-header class="header">
        <div class="header__left">
          <a-select
            v-if="projectStore.projects.length > 0"
            :value="projectStore.currentProjectId"
            :options="projectOptions"
            class="header__project"
            size="middle"
            :dropdown-match-select-width="240"
            @change="onProjectChange"
          >
            <template #prefixIcon><ProjectOutlined /></template>
            <template #suffixIcon><SwapOutlined /></template>
          </a-select>
        </div>

        <div class="header__right">
          <!-- 快捷键帮助 -->
          <a-popover placement="bottomRight" trigger="click">
            <template #content>
              <div class="shortcut-pop">
                <div class="shortcut-pop__title">快捷键</div>
                <div v-for="s in shortcuts" :key="s.k" class="shortcut-pop__row">
                  <span class="shortcut-pop__desc">{{ s.desc }}</span>
                  <kbd class="kbd">{{ s.k }}</kbd>
                </div>
              </div>
            </template>
            <a-button type="text" class="header__icon-btn" title="快捷键">
              <QuestionCircleOutlined />
            </a-button>
          </a-popover>

          <!-- 用户菜单 -->
          <a-dropdown placement="bottomRight">
            <button class="header__user">
              <a-avatar :size="28" class="header__avatar">
                {{ auth.user?.username?.[0]?.toUpperCase() }}
              </a-avatar>
              <span class="header__username">{{ auth.user?.username }}</span>
              <DownOutlined class="header__caret" />
            </button>
            <template #overlay>
              <a-menu class="user-menu">
                <div class="user-menu__head">
                  <div class="user-menu__name">{{ auth.user?.username }}</div>
                  <a-tag :color="auth.isAdmin() ? 'blue' : auth.isMember() ? 'green' : 'default'" class="user-menu__role">
                    {{ auth.roleLabel }}
                  </a-tag>
                </div>
                <a-menu-divider />
                <a-menu-item key="pwd" @click="showChangePassword">
                  <lock-outlined /> 修改密码
                </a-menu-item>
                <a-menu-item key="logout" danger @click="logout">
                  <logout-outlined /> 退出登录
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </a-layout-header>

      <!-- 内容区 -->
      <a-layout-content class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </a-layout-content>
    </a-layout>
  </a-layout>

  <!-- ============ 修改密码 ============ -->
  <a-modal
    v-model:open="passwordModal"
    title="修改密码"
    :confirm-loading="changingPassword"
    width="440"
    ok-text="确认修改"
    cancel-text="取消"
    @ok="changePassword"
  >
    <a-form layout="vertical" class="pwd-form">
      <a-form-item label="原密码" required>
        <a-input-password v-model:value="passwordForm.oldPassword" placeholder="请输入当前密码" />
      </a-form-item>
      <a-form-item label="新密码" required>
        <a-input-password
          v-model:value="passwordForm.newPassword"
          placeholder="至少 6 位"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ApiOutlined, RobotOutlined, ThunderboltOutlined, SettingOutlined,
  UserOutlined, AuditOutlined, ProjectOutlined, DownOutlined,
  LockOutlined, LogoutOutlined, SwapOutlined, QuestionCircleOutlined
} from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useProjectStore } from '@/stores/project'
import { AuthApi } from '@/api'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const projectStore = useProjectStore()

const projectOptions = computed(() =>
  projectStore.projects.map((p) => ({ value: p.id, label: p.name }))
)

/* ============================================================
   菜单数据：叶子 key → 路由 name（+可选 query）
   ============================================================ */

/** 叶子菜单 key → 路由目标 */
const ROUTE_MAP: Record<string, { name: string; query?: Record<string, string> }> = {
  // 接口自动化
  apis: { name: 'apis' },
  workbench: { name: 'workbench' },
  tasks: { name: 'tasks' },
  reports: { name: 'reports' },
  // UI 自动化
  'ui-tests': { name: 'ui-tests' },
  'ui-run': { name: 'ui-tests', query: { tab: 'scenarios' } },
  'ui-reports': { name: 'ui-reports' },
  // 性能测试
  perf: { name: 'perf' },
  'perf-reports': { name: 'perf', query: { tab: 'reports' } },
  // 环境配置
  environments: { name: 'environments' },
  'global-variables': { name: 'global-variables' },
  'environment-config': { name: 'environment-config' },
  'recycle-bin': { name: 'recycle-bin' },
  // 顶层叶子
  users: { name: 'users' },
  'audit-logs': { name: 'audit-logs' }
}

/** 叶子 key → 所属一级分组 key（用于自动展开） */
const GROUP_OF: Record<string, string> = {
  workbench: 'g-api', apis: 'g-api',
  tasks: 'g-api', reports: 'g-api',
  'ui-tests': 'g-ui', 'ui-run': 'g-ui', 'ui-reports': 'g-ui',
  perf: 'g-perf', 'perf-reports': 'g-perf',
  environments: 'g-env', 'global-variables': 'g-env',
  'environment-config': 'g-env', 'recycle-bin': 'g-env'
}

const menuItems = computed(() => {
  // 查看者：仅接口自动化 + UI 自动化（只读，操作按钮由各页面禁用）
  const isViewer = auth.isViewer()
  const items: any[] = [
    {
      key: 'g-api',
      icon: () => h(ApiOutlined),
      label: '接口自动化',
      children: [
        { key: 'apis', label: '接口定义' },
        { key: 'workbench', label: '接口用例' },
        { key: 'tasks', label: '测试执行' },
        { key: 'reports', label: '测试报告' }
      ]
    },
    {
      key: 'g-ui',
      icon: () => h(RobotOutlined),
      label: 'UI 自动化',
      children: [
        { key: 'ui-tests', label: 'UI 用例' },
        { key: 'ui-run', label: 'UI 用例执行' },
        { key: 'ui-reports', label: '测试报告' }
      ]
    }
  ]
  // 成员/管理员：额外显示性能测试 + 环境配置
  if (!isViewer) {
    items.push(
      {
        key: 'g-perf',
        icon: () => h(ThunderboltOutlined),
        label: '性能测试',
        children: [
          { key: 'perf', label: '创建用例' },
          { key: 'perf-reports', label: '测试报告' }
        ]
      },
      {
        key: 'g-env',
        icon: () => h(SettingOutlined),
        label: '环境配置',
        children: [
          { key: 'environments', label: '环境管理' },
          { key: 'global-variables', label: '全局变量' },
          { key: 'environment-config', label: '变量与模块' },
          { key: 'recycle-bin', label: '回收站' }
        ]
      }
    )
  }
  // 仅管理员：用户管理 + 操作日志
  if (auth.isAdmin()) {
    items.push(
      { key: 'users', icon: () => h(UserOutlined), label: '用户管理' },
      { key: 'audit-logs', icon: () => h(AuditOutlined), label: '操作日志' }
    )
  }
  return items
})

/* ============================================================
   选中态与展开态：根据当前路由反查
   ============================================================ */

const selectedKeys = computed(() => {
  const name = String(route.name)
  const tab = route.query.tab as string | undefined
  // 带 query 的菜单项反查
  if (name === 'ui-tests' && tab === 'scenarios') return ['ui-run']
  if (name === 'perf' && tab === 'reports') return ['perf-reports']
  // 普通叶子菜单
  for (const [key, target] of Object.entries(ROUTE_MAP)) {
    if (target.name === name && !target.query) return [key]
  }
  return []
})

const openKeys = ref<string[]>(['g-api'])

// 选中某个叶子时，自动展开其所属一级分组
watch(selectedKeys, (keys) => {
  if (keys.length) {
    const group = GROUP_OF[keys[0]]
    if (group && !openKeys.value.includes(group)) {
      openKeys.value = [...openKeys.value, group]
    }
  }
}, { immediate: true })

/* ============================================================
   事件
   ============================================================ */

function onMenuSelect({ key }: { key: string }) {
  const target = ROUTE_MAP[key]
  if (!target) return
  router.push({ name: target.name, query: target.query })
}

function goTo(name: string) {
  router.push({ name })
}

function onProjectChange(id: string) {
  projectStore.switchProject(id)
  router.go(0)
}

function logout() {
  auth.logout()
  router.push({ name: 'login' })
}

/* ============================================================
   快捷键 / 修改密码
   ============================================================ */

const shortcuts = [
  { k: 'Ctrl + S', desc: '保存当前用例' },
  { k: 'Ctrl + Enter', desc: '调试当前用例' },
  { k: 'Ctrl + F', desc: '搜索当前面板' }
]

const passwordModal = ref(false)
const changingPassword = ref(false)
const passwordForm = ref({ oldPassword: '', newPassword: '' })

function showChangePassword() {
  passwordForm.value = { oldPassword: '', newPassword: '' }
  passwordModal.value = true
}

async function changePassword() {
  if (!passwordForm.value.oldPassword || passwordForm.value.newPassword.length < 6) {
    message.warning('请填写完整；新密码至少 6 位')
    return
  }
  changingPassword.value = true
  try {
    await AuthApi.changePassword(passwordForm.value.oldPassword, passwordForm.value.newPassword)
    message.success('密码已修改')
    passwordModal.value = false
  } finally {
    changingPassword.value = false
  }
}

onMounted(async () => {
  await projectStore.fetchAll()
})
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
  background: var(--bg-page);
}

/* ============================================================
   左侧侧边栏（深色）
   ============================================================ */
.sider {
  background: var(--nav-bg);
  position: sticky;
  top: 0;
  height: 100vh;
  overflow: hidden;
}

/* logo 固定顶部，菜单区域在下方可滚动 */
.sider :deep(.ant-layout-sider-children) {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.sider__logo {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  height: 56px;
  padding: 0 var(--sp-5);
  cursor: pointer;
  user-select: none;
  border-bottom: 1px solid var(--nav-border);
  flex-shrink: 0;
}

.sider__logo-icon {
  font-size: 20px;
  line-height: 1;
  filter: drop-shadow(0 0 6px rgba(37, 99, 235, 0.6));
}

.sider__logo-name {
  font-size: var(--fs-lg);
  font-weight: 700;
  letter-spacing: 0.3px;
  color: var(--nav-text-strong);
  white-space: nowrap;
}

.sider__menu {
  background: transparent;
  border-inline-end: none;
  padding: var(--sp-2) var(--sp-2);
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

/* 菜单滚动条样式（深色底 + 细滚动条） */
.sider__menu::-webkit-scrollbar {
  width: 6px;
}

.sider__menu::-webkit-scrollbar-thumb {
  background: var(--nav-bg-hover);
  border-radius: var(--rd-full);
}

.sider__menu::-webkit-scrollbar-thumb:hover {
  background: var(--nav-text-dim);
}

.sider__menu :deep(.ant-menu-item),
.sider__menu :deep(.ant-menu-submenu-title) {
  border-radius: var(--rd-md);
  margin-block: 2px;
  font-size: var(--fs-base);
}

.sider__menu :deep(.ant-menu-item-selected) {
  background: var(--nav-bg-active) !important;
  color: var(--tx-inverse) !important;
}

/* 去掉 inline 菜单选中项右侧的强调条，改为主色块 */
.sider__menu :deep(.ant-menu-item-selected::after) {
  display: none;
}

/* 二级菜单项整体缩进更紧凑 */
.sider__menu :deep(.ant-menu-sub .ant-menu-item) {
  padding-inline-start: 46px !important;
}

/* ============================================================
   主区域
   ============================================================ */
.main {
  min-width: 0;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  height: 56px;
  padding: 0 var(--sp-5);
  background: var(--bg-card);
  border-bottom: 1px solid var(--bd-base);
  position: sticky;
  top: 0;
  z-index: var(--z-nav);
}

.header__left {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  min-width: 0;
}

.header__project {
  width: 240px;
}

.header__right {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.header__icon-btn {
  color: var(--tx-3);
}

.header__user {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: 4px 10px 4px 4px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--rd-full);
  cursor: pointer;
  color: var(--tx-2);
  transition: background var(--dur-fast) var(--ease),
              border-color var(--dur-fast) var(--ease);
}

.header__user:hover {
  background: var(--bg-hover);
  border-color: var(--bd-base);
}

.header__avatar {
  background: var(--c-primary);
  color: #fff;
  font-weight: 600;
  flex-shrink: 0;
}

.header__username {
  font-size: var(--fs-sm);
  color: var(--tx-1);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header__caret {
  font-size: 10px;
  color: var(--tx-4);
}

/* 用户下拉头部 */
.user-menu {
  min-width: 200px;
  padding-top: 0;
}

.user-menu__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-3) var(--sp-2);
}

.user-menu__name {
  font-weight: 600;
  color: var(--tx-1);
}

.user-menu__role {
  margin: 0;
}

/* ============================================================
   内容区
   ============================================================ */
.content {
  padding: var(--sp-5);
  background: var(--bg-page);
  min-height: calc(100vh - 56px);
  overflow: auto;
}

/* 快捷键弹层 */
.shortcut-pop {
  min-width: 210px;
  padding: var(--sp-1);
}

.shortcut-pop__title {
  font-size: var(--fs-xs);
  color: var(--tx-4);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: var(--sp-2);
  padding: 0 var(--sp-2);
}

.shortcut-pop__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  padding: var(--sp-1) var(--sp-2);
}

.shortcut-pop__desc {
  font-size: var(--fs-sm);
  color: var(--tx-2);
}

.kbd {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--tx-3);
  background: var(--bg-subtle);
  border: 1px solid var(--bd-base);
  border-bottom-width: 2px;
  border-radius: var(--rd-sm);
  padding: 1px 6px;
  white-space: nowrap;
}

/* 路由切换过渡 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity var(--dur-base) var(--ease),
              transform var(--dur-base) var(--ease);
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@media (prefers-reduced-motion: reduce) {
  .fade-slide-enter-active,
  .fade-slide-leave-active {
    transition: none;
  }
}

.pwd-form :deep(.ant-form-item:last-child) {
  margin-bottom: 0;
}
</style>
