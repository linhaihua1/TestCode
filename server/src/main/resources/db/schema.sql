-- =============================================================
-- Api-Web 自动化测试平台 MySQL 8.0 建表脚本
-- 约定：
--   * 所有时间字段使用 DATETIME，连接串 serverTimezone=UTC，统一按 UTC 存储
--   * 字符集 utf8mb4，JSON 字段用于步骤/断言/提取等半结构化数据
--   * 主键为 32 位 UUID 字符串（应用侧 MyBatis-Plus ASSIGN_UUID 生成）
--   * 大对象（>1MB 请求/响应体）存 MinIO，库内仅存对象引用路径（*_ref 字段）
-- =============================================================

CREATE DATABASE IF NOT EXISTS api_web DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE api_web;

CREATE TABLE IF NOT EXISTS t_recycle_bin_config (
    id         VARCHAR(32) NOT NULL PRIMARY KEY,
    project_id VARCHAR(32) NOT NULL,
    cleanup_days INT NOT NULL DEFAULT 30 COMMENT '0 = 不自动清理',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project (project_id)
) ENGINE = InnoDB COMMENT '回收站自动清理配置';

-- ---------------------------- 用户 ----------------------------
CREATE TABLE IF NOT EXISTS t_user (
    id            VARCHAR(32)  NOT NULL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    role          VARCHAR(16)  NOT NULL DEFAULT 'member' COMMENT 'admin/member/viewer',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB COMMENT '系统用户';

-- ---------------------------- 项目与环境 ----------------------------
CREATE TABLE IF NOT EXISTS t_project (
    id          VARCHAR(32) NOT NULL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB COMMENT '项目';

CREATE TABLE IF NOT EXISTS t_environment (
    id         VARCHAR(32) NOT NULL PRIMARY KEY,
    project_id VARCHAR(32) NOT NULL,
    name       VARCHAR(128) NOT NULL,
    base_url   VARCHAR(512),
    variables  JSON NOT NULL,
    headers    JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_project (project_id)
) ENGINE = InnoDB COMMENT '环境配置';

CREATE TABLE IF NOT EXISTS t_global_variable (
    id          VARCHAR(32) NOT NULL PRIMARY KEY,
    project_id  VARCHAR(32) NOT NULL,
    name        VARCHAR(128) NOT NULL,
    type        VARCHAR(16)  NOT NULL DEFAULT 'string',
    value       TEXT,
    encrypted   TINYINT(1) NOT NULL DEFAULT 0,
    description VARCHAR(512),
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_project (project_id)
) ENGINE = InnoDB COMMENT '全局变量';

-- ---------------------------- 模块树 / 用例库 ----------------------------
CREATE TABLE IF NOT EXISTS t_module (
    id         VARCHAR(32) NOT NULL PRIMARY KEY,
    project_id VARCHAR(32) NOT NULL,
    parent_id  VARCHAR(32),
    name       VARCHAR(128) NOT NULL,
    type       VARCHAR(16) NOT NULL DEFAULT 'case' COMMENT 'case/api/ui/perf',
    sort_order INT NOT NULL DEFAULT 0,
    deleted_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_project (project_id),
    KEY idx_parent (parent_id),
    KEY idx_deleted (deleted_at)
) ENGINE = InnoDB COMMENT '模块树（支持多级）';

CREATE TABLE IF NOT EXISTS t_case (
    id          VARCHAR(32) NOT NULL PRIMARY KEY,
    project_id  VARCHAR(32) NOT NULL,
    module_id   VARCHAR(32),
    name        VARCHAR(256) NOT NULL,
    description TEXT,
    status      VARCHAR(16) NOT NULL DEFAULT 'draft' COMMENT 'draft/reviewing/pass/fail/trash',
    priority    VARCHAR(4)  NOT NULL DEFAULT 'P2',
    tags        JSON NOT NULL,
    steps       JSON NOT NULL COMMENT '多步骤定义：每步引用接口+断言+提取+控制器',
    version     INT NOT NULL DEFAULT 1,
    sort_order  INT NOT NULL DEFAULT 0,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at  DATETIME,
    KEY idx_project (project_id),
    KEY idx_module (module_id),
    KEY idx_status (status)
) ENGINE = InnoDB COMMENT '测试用例';

CREATE TABLE IF NOT EXISTS t_case_version (
    id             VARCHAR(32) NOT NULL PRIMARY KEY,
    case_id        VARCHAR(32) NOT NULL,
    version        INT NOT NULL,
    snapshot       JSON NOT NULL,
    change_summary VARCHAR(512),
    created_by     VARCHAR(64),
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_case (case_id)
) ENGINE = InnoDB COMMENT '用例版本快照';

CREATE TABLE IF NOT EXISTS t_case_review (
    id            VARCHAR(32) NOT NULL PRIMARY KEY,
    case_id       VARCHAR(32) NOT NULL,
    reviewer_id   VARCHAR(32),
    reviewer_name VARCHAR(64),
    action        VARCHAR(32) NOT NULL COMMENT 'submit/approve/reject',
    comment       VARCHAR(1024),
    from_status   VARCHAR(16),
    to_status     VARCHAR(16),
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_case (case_id)
) ENGINE = InnoDB COMMENT '用例评审记录';

-- ---------------------------- 接口定义 ----------------------------
CREATE TABLE IF NOT EXISTS t_api_definition (
    id            VARCHAR(32) NOT NULL PRIMARY KEY,
    project_id    VARCHAR(32) NOT NULL,
    name          VARCHAR(256) NOT NULL,
    method        VARCHAR(8)  NOT NULL,
    path          VARCHAR(512) NOT NULL,
    headers       JSON NOT NULL,
    query         JSON NOT NULL,
    body          LONGTEXT,
    description   VARCHAR(1024),
    mock_enabled  TINYINT(1) NOT NULL DEFAULT 0,
    mock_response LONGTEXT,
    module_id     VARCHAR(32),
    tags          JSON NOT NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_project (project_id),
    KEY idx_module (module_id)
) ENGINE = InnoDB COMMENT '接口定义';

-- ---------------------------- 调试记录（大对象走 MinIO） ----------------------------
CREATE TABLE IF NOT EXISTS t_debug_record (
    id                  VARCHAR(32) NOT NULL PRIMARY KEY,
    case_id             VARCHAR(32) NOT NULL,
    case_name_snapshot  VARCHAR(256) NOT NULL,
    environment_id      VARCHAR(32),
    execute_mode        VARCHAR(16) NOT NULL DEFAULT 'local' COMMENT 'local/resource_pool',
    result              VARCHAR(16) NOT NULL DEFAULT 'pending',
    total_duration      INT NOT NULL DEFAULT 0,
    request_summary     JSON NOT NULL,
    response_summary    JSON NOT NULL,
    assertion_results   JSON NOT NULL,
    extracted_variables JSON NOT NULL,
    step_results        JSON NOT NULL,
    request_body_ref    VARCHAR(512) COMMENT 'MinIO 对象引用（超 1MB 时）',
    response_body_ref   VARCHAR(512) COMMENT 'MinIO 对象引用（超 1MB 时）',
    error_log           LONGTEXT,
    created_by          VARCHAR(64),
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_case (case_id),
    KEY idx_created (created_at)
) ENGINE = InnoDB COMMENT '调试记录';

-- ---------------------------- 场景 ----------------------------
CREATE TABLE IF NOT EXISTS t_scenario (
    id          VARCHAR(32) NOT NULL PRIMARY KEY,
    project_id  VARCHAR(32) NOT NULL,
    name        VARCHAR(256) NOT NULL,
    description VARCHAR(1024),
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_project (project_id)
) ENGINE = InnoDB COMMENT '接口场景';

-- ---------------------------- 用例步骤（独立表） ----------------------------
CREATE TABLE IF NOT EXISTS t_case_step (
    id           VARCHAR(32) NOT NULL PRIMARY KEY,
    case_id      VARCHAR(32) NOT NULL COMMENT '所属用例',
    parent_id    VARCHAR(32) COMMENT '父步骤 ID（控制器嵌套）',
    step_type    VARCHAR(32) NOT NULL COMMENT 'HTTP_REQUEST/SCRIPT/WAIT/VARIABLE_ASSIGN/IF/FOR/WHILE/TRANSACTION/ONCE/REF_PUBLIC_CASE',
    name         VARCHAR(256) NOT NULL COMMENT '步骤名称',
    position     VARCHAR(8) NOT NULL DEFAULT 'TEST' COMMENT 'PRE/TEST/POST',
    sort_order   INT NOT NULL DEFAULT 0 COMMENT '同 parent 内排序号',
    enabled      TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    remark       VARCHAR(1024) COMMENT '步骤备注',
    config       LONGTEXT NOT NULL COMMENT 'JSON 配置（按 stepType 不同 schema）',
    fail_strategy VARCHAR(16) NOT NULL DEFAULT 'stop' COMMENT 'stop/continue',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_case (case_id),
    KEY idx_parent (parent_id),
    KEY idx_position (case_id, position)
) ENGINE = InnoDB COMMENT '用例步骤（10 种类型,支持嵌套）';

CREATE TABLE IF NOT EXISTS t_scenario_step (
    id         VARCHAR(32) NOT NULL PRIMARY KEY,
    scenario_id VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL,
    api_case_id VARCHAR(32),
    name       VARCHAR(256),
    assertions JSON NOT NULL,
    extracts   JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_scenario (scenario_id)
) ENGINE = InnoDB COMMENT '场景步骤';

-- ---------------------------- 测试任务 / 执行记录 ----------------------------
CREATE TABLE IF NOT EXISTS t_test_task (
    id             VARCHAR(32) NOT NULL PRIMARY KEY,
    project_id     VARCHAR(32) NOT NULL,
    name           VARCHAR(256) NOT NULL,
    description    VARCHAR(1024),
    case_ids       JSON NOT NULL,
    environment_id VARCHAR(32),
    execute_mode   VARCHAR(16) NOT NULL DEFAULT 'sequential' COMMENT 'sequential/parallel',
    retry_count    INT NOT NULL DEFAULT 0,
    timeout_ms     INT NOT NULL DEFAULT 300000,
    cron_expr      VARCHAR(64),
    enabled        TINYINT(1) NOT NULL DEFAULT 1,
    notify_url     VARCHAR(512),
    variables      JSON NOT NULL,
    base_url       VARCHAR(512),
    created_by     VARCHAR(64),
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at     DATETIME,
    KEY idx_project (project_id)
) ENGINE = InnoDB COMMENT '测试任务';

CREATE TABLE IF NOT EXISTS t_test_task_run (
    id         VARCHAR(32) NOT NULL PRIMARY KEY,
    task_id    VARCHAR(32) NOT NULL,
    result     VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/running/success/failed/error',
    duration   INT NOT NULL DEFAULT 0,
    details    JSON NOT NULL,
    details_ref VARCHAR(512) COMMENT 'MinIO 对象引用（报告明细超 1MB 时）',
    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at   DATETIME,
    KEY idx_task (task_id)
) ENGINE = InnoDB COMMENT '测试任务执行记录';

-- ---------------------------- 报告 ----------------------------
CREATE TABLE IF NOT EXISTS t_report (
    id          VARCHAR(32) NOT NULL PRIMARY KEY,
    project_id  VARCHAR(32) NOT NULL,
    scenario_id VARCHAR(32),
    name        VARCHAR(256) NOT NULL,
    status      VARCHAR(16) NOT NULL,
    duration    INT NOT NULL DEFAULT 0,
    started_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_project (project_id)
) ENGINE = InnoDB COMMENT '接口测试报告';

CREATE TABLE IF NOT EXISTS t_report_detail (
    id         VARCHAR(32) NOT NULL PRIMARY KEY,
    report_id  VARCHAR(32) NOT NULL,
    step_name  VARCHAR(256) NOT NULL,
    status     VARCHAR(16) NOT NULL,
    error      TEXT,
    assertions JSON NOT NULL,
    extracts   JSON NOT NULL,
    KEY idx_report (report_id)
) ENGINE = InnoDB COMMENT '接口测试报告明细';

-- ---------------------------- UI 自动化 ----------------------------
CREATE TABLE IF NOT EXISTS t_ui_test_case (
    id             VARCHAR(32) NOT NULL PRIMARY KEY,
    project_id     VARCHAR(32) NOT NULL,
    name           VARCHAR(256) NOT NULL,
    description    TEXT,
    base_url       VARCHAR(512),
    setup_steps    JSON NOT NULL,
    steps          JSON NOT NULL,
    teardown_steps JSON NOT NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_project (project_id)
) ENGINE = InnoDB COMMENT 'UI 测试用例';

CREATE TABLE IF NOT EXISTS t_ui_scenario (
    id          VARCHAR(32) NOT NULL PRIMARY KEY,
    project_id  VARCHAR(32) NOT NULL,
    name        VARCHAR(256) NOT NULL,
    description VARCHAR(1024),
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_project (project_id)
) ENGINE = InnoDB COMMENT 'UI 执行场景';

CREATE TABLE IF NOT EXISTS t_ui_scenario_step (
    id             VARCHAR(32) NOT NULL PRIMARY KEY,
    scenario_id    VARCHAR(32) NOT NULL,
    sort_order     INT NOT NULL,
    ui_test_case_id VARCHAR(32),
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_scenario (scenario_id)
) ENGINE = InnoDB COMMENT 'UI 场景步骤';

CREATE TABLE IF NOT EXISTS t_ui_report (
    id          VARCHAR(32) NOT NULL PRIMARY KEY,
    project_id  VARCHAR(32) NOT NULL,
    test_case_id VARCHAR(32),
    name        VARCHAR(256) NOT NULL,
    status      VARCHAR(16) NOT NULL,
    duration    INT NOT NULL DEFAULT 0,
    started_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    details     JSON NOT NULL,
    details_ref VARCHAR(512) COMMENT 'MinIO 对象引用（含截图列表，超 1MB 时）',
    KEY idx_project (project_id)
) ENGINE = InnoDB COMMENT 'UI 测试报告';

-- ---------------------------- 性能测试 ----------------------------
CREATE TABLE IF NOT EXISTS t_perf_case (
    id              VARCHAR(32) NOT NULL PRIMARY KEY,
    project_id      VARCHAR(32) NOT NULL,
    name            VARCHAR(256) NOT NULL,
    description     VARCHAR(1024),
    threads         INT NOT NULL DEFAULT 1,
    ramp_up         INT NOT NULL DEFAULT 1,
    loops           INT NOT NULL DEFAULT 1,
    duration        INT NOT NULL DEFAULT 0,
    think_time      INT NOT NULL DEFAULT 0,
    on_sample_error VARCHAR(16) NOT NULL DEFAULT 'continue',
    variables       JSON NOT NULL,
    steps           JSON NOT NULL,
    profile         JSON NOT NULL COMMENT '{loadProfile, stepping?, concurrency?}',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      DATETIME,
    KEY idx_project (project_id)
) ENGINE = InnoDB COMMENT '性能测试用例';

CREATE TABLE IF NOT EXISTS t_perf_report (
    id         VARCHAR(32) NOT NULL PRIMARY KEY,
    project_id VARCHAR(32) NOT NULL,
    case_id    VARCHAR(32),
    name       VARCHAR(256) NOT NULL,
    status     VARCHAR(16) NOT NULL COMMENT 'success/failed/error',
    duration   INT NOT NULL DEFAULT 0,
    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    summary    JSON NOT NULL,
    series     JSON NOT NULL,
    labels     JSON NOT NULL,
    errors     JSON NOT NULL,
    message    TEXT,
    KEY idx_project (project_id)
) ENGINE = InnoDB COMMENT '性能测试报告';

-- ---------------------------- 审计日志 ----------------------------
CREATE TABLE IF NOT EXISTS t_audit_log (
    id         VARCHAR(32) NOT NULL PRIMARY KEY,
    user_id    VARCHAR(32),
    username   VARCHAR(64),
    action     VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id  VARCHAR(64),
    before_json JSON,
    after_json  JSON,
    ip         VARCHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_created (created_at),
    KEY idx_user (user_id)
) ENGINE = InnoDB COMMENT '操作审计日志';

-- ---------------------------- 执行机资源池（心跳注册） ----------------------------
CREATE TABLE IF NOT EXISTS t_executor_node (
    id            VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '执行机唯一标识（hostname:pid 或自定义）',
    name          VARCHAR(128),
    capabilities  JSON NOT NULL COMMENT '支持的能力：api/ui/perf',
    status        VARCHAR(16) NOT NULL DEFAULT 'online' COMMENT 'online/offline/busy',
    last_heartbeat DATETIME NOT NULL,
    current_task  VARCHAR(32),
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB COMMENT '执行机节点（心跳注册资源池）';
