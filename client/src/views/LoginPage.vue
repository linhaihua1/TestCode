<template>
  <div class="login-bg">
    <a-card class="login-card" :bordered="false">
      <div class="logo">⚡ Api-Web</div>
      <div class="subtitle">接口 · UI · 性能 自动化测试平台</div>
      <a-form
        layout="vertical"
        :model="form"
        @finish="onLogin"
        style="margin-top: 24px"
      >
        <a-form-item label="用户名" required>
          <a-input v-model:value="form.username" size="large" placeholder="admin">
            <template #prefix><user-outlined /></template>
          </a-input>
        </a-form-item>
        <a-form-item label="密码" required>
          <a-input-password v-model:value="form.password" size="large" placeholder="admin@123">
            <template #prefix><lock-outlined /></template>
          </a-input-password>
        </a-form-item>
        <a-button
          type="primary"
          html-type="submit"
          size="large"
          block
          :loading="loading"
        >
          登录
        </a-button>
      </a-form>
      <div class="tip">默认管理员：admin / admin@123</div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const form = ref({ username: 'admin', password: 'admin@123' })
const loading = ref(false)

async function onLogin() {
  loading.value = true
  try {
    await auth.login(form.value.username, form.value.password)
    message.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch (e: any) {
    message.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-bg {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e3a8a, #2563eb, #06b6d4);
}
.login-card {
  width: 400px;
  padding: 24px 16px;
}
.logo {
  font-size: 28px;
  font-weight: bold;
  text-align: center;
}
.subtitle {
  text-align: center;
  color: #666;
  margin-top: 4px;
}
.tip {
  margin-top: 12px;
  text-align: center;
  font-size: 12px;
  color: #999;
}
</style>