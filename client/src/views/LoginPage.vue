<template>
  <div class="login">
    <!-- 背景光晕装饰层 -->
    <div class="login__bg" aria-hidden="true">
      <div class="login__glow login__glow--1" />
      <div class="login__glow login__glow--2" />
      <div class="login__grid" />
    </div>

    <!-- 中央登录卡片 -->
    <main class="login__main">
      <section class="login__card">
        <header class="login__brand">
          <div class="login__logo">⚡</div>
          <h1 class="login__title">Api-Web</h1>
          <p class="login__subtitle">接口 · UI · 性能 自动化测试平台</p>
        </header>

        <a-form
          layout="vertical"
          :model="form"
          @finish="onLogin"
          class="login__form"
        >
          <a-form-item label="用户名" required>
            <a-input
              v-model:value="form.username"
              size="large"
              placeholder="admin"
              autocomplete="username"
            >
              <template #prefix><user-outlined /></template>
            </a-input>
          </a-form-item>
          <a-form-item label="密码" required>
            <a-input-password
              v-model:value="form.password"
              size="large"
              placeholder="admin@123"
              autocomplete="current-password"
            >
              <template #prefix><lock-outlined /></template>
            </a-input-password>
          </a-form-item>
          <a-button
            type="primary"
            html-type="submit"
            size="large"
            block
            :loading="loading"
            class="login__submit"
          >
            登录
          </a-button>
        </a-form>

        <footer class="login__footer">
          <div class="login__tip">默认管理员：admin / admin@123</div>
          <div class="login__copyright">© {{ year }} Api-Web · v1.0</div>
        </footer>
      </section>
    </main>
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

const year = new Date().getFullYear()

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
/* ============================================================
   全屏容器：深色背景 + 径向渐变光晕
   ============================================================ */
.login {
  position: relative;
  min-height: 100vh;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--nav-bg);
  overflow: hidden;
  isolation: isolate;
}

/* 背景装饰层 */
.login__bg {
  position: absolute;
  inset: 0;
  z-index: -1;
  pointer-events: none;
}

/* 径向渐变光晕：主色 + 信息色叠加 */
.login__glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(96px);
  opacity: 0.45;
  animation: drift 18s var(--ease) infinite alternate;
}

.login__glow--1 {
  top: -120px;
  left: -80px;
  width: 480px;
  height: 480px;
  background: radial-gradient(circle, var(--c-primary) 0%, transparent 70%);
}

.login__glow--2 {
  bottom: -160px;
  right: -120px;
  width: 520px;
  height: 520px;
  background: radial-gradient(circle, var(--c-info) 0%, transparent 70%);
  animation-delay: -6s;
}

@keyframes drift {
  from { transform: translate3d(0, 0, 0) scale(1); }
  to   { transform: translate3d(40px, -30px, 0) scale(1.08); }
}

/* 极淡的网格底纹，加强科技感 */
.login__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(circle at center, black 0%, transparent 75%);
  -webkit-mask-image: radial-gradient(circle at center, black 0%, transparent 75%);
}

/* ============================================================
   登录卡片
   ============================================================ */
.login__main {
  position: relative;
  width: 100%;
  padding: var(--sp-5);
  display: flex;
  justify-content: center;
}

.login__card {
  width: 100%;
  max-width: 400px;
  padding: var(--sp-8) var(--sp-6) var(--sp-6);
  background: var(--bg-card);
  border-radius: var(--rd-xl);
  box-shadow: var(--sd-xl);
}

/* ============================================================
   品牌头部
   ============================================================ */
.login__brand {
  text-align: center;
  margin-bottom: var(--sp-6);
}

.login__logo {
  font-size: 44px;
  line-height: 1;
  color: var(--c-primary);
  text-shadow: 0 0 24px rgba(37, 99, 235, 0.35);
  margin-bottom: var(--sp-2);
}

.login__title {
  font-size: var(--fs-2xl);
  font-weight: 700;
  color: var(--tx-1);
  letter-spacing: 0.3px;
  margin: 0 0 var(--sp-1);
}

.login__subtitle {
  font-size: var(--fs-sm);
  color: var(--tx-3);
  margin: 0;
}

/* ============================================================
   表单
   ============================================================ */
.login__form {
  margin-top: var(--sp-4);
}

.login__form :deep(.ant-form-item) {
  margin-bottom: var(--sp-4);
}

.login__form :deep(.ant-form-item-label) {
  padding-bottom: var(--sp-1);
  font-weight: 500;
  color: var(--tx-2);
}

.login__submit {
  margin-top: var(--sp-2);
  height: 44px;
  font-size: var(--fs-md);
  font-weight: 500;
  border-radius: var(--rd-md);
}

/* ============================================================
   卡片底部：默认账号提示 + 版权
   ============================================================ */
.login__footer {
  margin-top: var(--sp-6);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--bd-subtle);
  text-align: center;
}

.login__tip {
  font-size: var(--fs-xs);
  color: var(--tx-4);
  font-family: var(--font-mono);
}

.login__copyright {
  margin-top: var(--sp-2);
  font-size: var(--fs-xs);
  color: var(--tx-4);
  letter-spacing: 0.3px;
}

/* ============================================================
   响应式：小屏适配
   ============================================================ */
@media (max-width: 480px) {
  .login__card {
    max-width: 100%;
    padding: var(--sp-6) var(--sp-4) var(--sp-4);
    border-radius: var(--rd-lg);
  }
  .login__logo {
    font-size: 36px;
  }
  .login__title {
    font-size: var(--fs-xl);
  }
}

/* 减速动效偏好 */
@media (prefers-reduced-motion: reduce) {
  .login__glow {
    animation: none;
  }
}
</style>
