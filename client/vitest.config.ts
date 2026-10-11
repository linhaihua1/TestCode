import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'url';

/**
 * Vitest 测试配置。
 *
 * <p>与 vite.config.ts 分离，避免测试环境与构建环境互相干扰。
 * 纯函数（utils）用默认 node 环境；组件测试可在用例文件顶部用
 * {@code // @vitest-environment jsdom} 单独切换 DOM 环境。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    environment: 'node',
    globals: true,
    // 串行执行测试文件，规避沙箱环境临时目录并发写入冲突
    fileParallelism: false,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      include: ['src/utils/**', 'src/stores/**'],
    },
  },
});
