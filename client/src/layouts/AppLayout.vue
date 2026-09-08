<!--
  应用主框架（需求文档 §1 + 开发文档 §5.1）。

  <h3>布局</h3>
  <ul>
    <li>顶部主导航栏（logo + 菜单 + 项目选择 + 用户菜单）</li>
    <li>顶部全局配置栏（全局变量 / 调试记录 / 回收站 / 快捷键提示）</li>
    <li>分割线（隔离顶部配置区与下方业务）</li>
    <li>下方路由内容</li>
  </ul>
-->
<template>
  <a-layout style="min-height: 100vh">
    <!-- 主导航栏 -->
    <a-layout-header class="header">
      <div class="logo">⚡ Api-Web</div>
      <a-menu
        v-model:selectedKeys="selectedKeys"
        mode="horizontal"
        :items="menuItems"
        @select="onMenuSelect"
        style="flex: 1; min-width: 0"
      />
      <a-space>
        <a-select
          v-if="projectStore.projects.length > 0"
          :value="projectStore.currentProjectId"
          :options="projectOptions"
          style="width: 220px"
          @change="onProjectChange"
        />
        <a-dropdown>
          <a-space class="user-info">
            <a-avatar size="small">{{ auth.user?.username?.[0]?.toUpperCase() }}</a-avatar>
            <span>{{ auth.user?.username }}</span>
            <a-tag v-if="auth.user?.role" color="cyan" size="small">{{ auth.user.role }}</a-tag>
            <down-outlined />
          </a-space>
          <template #overlay>
            <a-menu>
              <a-menu-item @click="showChangePassword">
                <lock-outlined />修改密码
              </a-menu-item>
              <a-menu-divider />
              <a-menu-item @click="logout">
                <logout-outlined />退出登录
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </a-space>
    </a-layout-header>

    <!-- 顶部全局配置栏（需求文档 §2） -->
    <div class="global-config-bar">
      <a-space>
        <a-tooltip title="全局变量管理（支持密钥脱敏）">
          <a-button type="text" @click="goTo('global-variables')">
            <SettingOutlined />全局变量
          </a-button>
        </a-tooltip>
        <a-tooltip title="查看全局调试记录">
          <a-button type="text" @click="goTo('debug-records')">
            <FileSearchOutlined />调试记录
          </a-button>
        </a-tooltip>
        <a-tooltip title="回收站（模块/接口/用例/任务）">
          <a-button type="text" @click="goTo('recycle-bin')">
            <DeleteOutlined />回收站
          </a-button>
        </a-tooltip>
      </a-space>
      <a-space size="small" class="shortcut-hint">
        <a-tag>Ctrl+S 保存</a-tag>
        <a-tag>Ctrl+Enter 调试</a-tag>
        <a-tag>Ctrl+F 搜索</a-tag>
      </a-space>
    </div>

    <!-- 分割线（需求文档 §1） -->
    <div class="divider" />

    <a-layout-content class="content">
      <router-view v-slot="{ Component }">
        <component :is="Component" />
      </router-view>
    </a-layout-content>
  </a-layout>

  <a-modal
    v-model:open="passwordModal"
    title="修改密码"
    @ok="changePassword"
    :confirm-loading="changingPassword"
    width="420"
  >
    <a-form layout="vertical">
      <a-form-item label="原密码" required>
        <a-input-password v-model:value="passwordForm.oldPassword" />
      </a-form-item>
      <a-form-item label="新密码（至少 6 位）" required>
        <a-input-password v-model:value="passwordForm.newPassword" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  AppstoreOutlined, ProjectOutlined, CodeOutlined, ThunderboltOutlined,
  RobotOutlined, UserOutlined, FileTextOutlined, AuditOutlined, EnvironmentOutlined,
  SettingOutlined, DownOutlined, LockOutlined, LogoutOutlined,
  FileSearchOutlined, DeleteOutlined
} from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useProjectStore } from '@/stores/project'
import { AuthApi } from '@/api'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const projectStore = useProjectStore()

const selectedKeys = computed(() => [route.path.split('/')[1] || 'projects'])

const projectOptions = computed(() =>
  projectStore.projects.map((p) => ({ value: p.id, label: p.name }))
)

const menuItems = computed(() => [
  { key: 'projects', icon: () => h(ProjectOutlined), label: '项目' },
  { key: 'workbench', icon: () => h(AppstoreOutlined), label: '工作台' },
  { key: 'apis', icon: () => h(CodeOutlined), label: '接口管理' },
  { key: 'scenarios', icon: () => h(FileTextOutlined), label: '场景' },
  { key: 'tasks', icon: () => h(ThunderboltOutlined), label: '测试任务' },
  { key: 'reports', icon: () => h(FileTextOutlined), label: '接口报告' },
  { key: 'perf', icon: () => h(ThunderboltOutlined), label: '性能测试' },
  { key: 'ui', icon: () => h(RobotOutlined), label: 'UI 自动化' },
  { key: 'environments', icon: () => h(EnvironmentOutlined), label: '环境' },
  { key: 'environment-config', icon: () => h(SettingOutlined), label: '变量/模块' },
  ...(auth.isAdmin()
    ? [
        { key: 'users', icon: () => h(UserOutlined), label: '用户管理' },
        { key: 'audit-logs', icon: () => h(AuditOutlined), label: '审计日志' }
      ]
    : [])
])

function onMenuSelect({ key }: { key: string }) {
  router.push({ name: key })
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
.header {
  display: flex;
  align-items: center;
  background: #001529;
  padding: 0 24px;
  color: white;
  height: 48px;
  line-height: 48px;
}
.logo {
  font-size: 20px;
  font-weight: bold;
  margin-right: 32px;
  color: #fff;
}
.user-info {
  color: white;
  cursor: pointer;
}
.global-config-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 24px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}
.divider {
  height: 1px;
  background: #e8e8e8;
}
.content {
  padding: 16px;
  background: #f0f2f5;
  flex: 1;
}
.shortcut-hint {
  font-size: 12px;
  color: #999;
}
</style>