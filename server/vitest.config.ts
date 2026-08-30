import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    // 测试使用独立的 SQLite 数据库，避免污染 dev.db
    env: {
      DATABASE_URL: 'file:./test.db',
    },
  },
})
