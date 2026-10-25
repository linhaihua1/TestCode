# AGENTS.md

## 注意事项

1. 每次改动完成后，都必须创建一个对应的 git commit，以便后续追踪和回滚。（提交信息用中文）
2. 每次改动后，都必须编写或更新相关测试，并在交付给用户前，确保所有测试和验证全部通过。

## 工程约定速查

### 构建与验证

- 后端编译：`cd server && mvn -DskipTests compile`
- 后端测试：`cd server && mvn test`（当前 37 个测试文件 / 40 个测试类 / 201 用例，须全绿）
- 前端类型检查：`cd client && npm run type-check`
- 前端构建：`cd client && npm run build`
- 前端规范 / 单测：`cd client && npm run lint`、`npm run test`（当前 6 文件，须全绿）

### 后端

- 分层：Controller → Service → Mapper 严格单向依赖，**Controller 禁止注入 Mapper**（`architecture/LayeredArchitectureTest` 强制）
- API 出入参用 `dto/`（`XxxReq`）与 `vo/`（`XxxVO`），经 MapStruct `converter/` 转换，Entity 不直接出入参
- 路径前缀统一 `/api/v1`（`MockController` 例外，走 `/api/mock`）；响应统一 `Result<T>`；错误码集中在 `common/ErrorCode.java`
- 实体里以 `String` 存 JSON 快照的字段：入参 DTO 需 `@JsonDeserialize(JsonStringDeserializer.class)`，出参 VO 需声明 `Object` 且在 `@AfterMapping` 中解析（否则前端会拿到字符串、按数组使用时崩溃）
- 数据库结构变更走 **Flyway**：新增 `db/migration/V{n}__xxx.sql`，勿手改已发布的迁移
- 配置外置：可变参数走 `application.yml` 的 `apiweb.*` 段，勿写魔法值
- 新增/修改枚举或错误码：同步前端 `client/src/constants/index.ts`，并补一条 `*Test` 锁死 `getCode()`

### 前端

- 拖拽统一用 **vuedraggable@4**（`#item` 插槽 + `item-key`，数据需带 `_id`，用 `utils/uid.ts` 生成）
- 样式统一使用 `styles/tokens.css` 的 CSS 变量，禁止硬编码色值
- 时间格式化统一走 `utils/format.ts`，禁止在页面内直接 `dayjs.utc(...)`
- 枚举 / 标签 / 颜色 / 分页 / HTTP 方法等常量集中在 `constants/index.ts`
- 页面组件必须单根节点（否则 `AppLayout` 的 `<transition>` 会导致离开后内容区空白）

### 文档

改动涉及功能 / 菜单 / API / 结构时，同步更新 `README.md` 与 `docs/`（USER_GUIDE.md、DEVELOPER.md、开发文档.md 等），保持文档与代码一致。
