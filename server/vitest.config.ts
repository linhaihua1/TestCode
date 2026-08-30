import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    // 测试使用独立的 SQLite 数据库，避免污染 dev.db
    env: {
      DATABASE_URL: 'file:./test.db',
    },
    // 测试共享同一个 test.db，串行执行避免数据冲突
    fileParallelism: false,
  },
})
