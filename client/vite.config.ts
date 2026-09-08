import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'url'

export default defineConfig({
  plugins: [
    vue(),
    // ant-design-vue v4 使用 CSS-in-JS,不需要按需注入样式文件,
    // 所以这里 resolver 关闭 importStyle,避免生成 v3 风格的 es/xxx/style/css 引用
    AutoImport({ resolvers: [AntDesignVueResolver({ importStyle: false })] }),
    Components({ resolvers: [AntDesignVueResolver({ importStyle: false })] })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    target: 'es2020',
    rollupOptions: {
      output: {
        manualChunks: {
          'monaco-editor': ['monaco-editor'],
          'echarts': ['echarts', 'vue-echarts'],
          'ant-design-vue': ['ant-design-vue']
        }
      }
    }
  },
  optimizeDeps: {
    include: ['monaco-editor/esm/vs/editor/editor.api']
  }
})