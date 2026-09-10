-- ============================================================================
-- Api-Web 增量迁移脚本（幂等，可重复执行）
-- ============================================================================
-- 适用场景：数据库已按旧版 schema.sql 初始化过，需要补齐后续版本新增的字段/表。
-- 执行方式：
--   mysql -h127.0.0.1 -P3306 -uapiweb -papiweb123 api_web < migrate.sql
--
-- 设计说明：
--   schema.sql 用 CREATE TABLE IF NOT EXISTS，对已存在的表不会补列，
--   因此每次结构变更都要在这里追加"补列/补表"语句。本脚本可反复执行。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 补表（P1/P3 新增表，旧库缺失）
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS t_recycle_bin_config (
    id           VARCHAR(32) NOT NULL PRIMARY KEY,
    project_id   VARCHAR(32) NOT NULL,
    cleanup_days INT NOT NULL DEFAULT 30 COMMENT '0 = 不自动清理',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project (project_id)
) ENGINE = InnoDB COMMENT '回收站自动清理配置';

CREATE TABLE IF NOT EXISTS t_case_step (
    id            VARCHAR(32) NOT NULL PRIMARY KEY,
    case_id       VARCHAR(32) NOT NULL COMMENT '所属用例',
    parent_id     VARCHAR(32) COMMENT '父步骤 ID（控制器嵌套）',
    step_type     VARCHAR(32) NOT NULL COMMENT 'HTTP_REQUEST/SCRIPT/WAIT/VARIABLE_ASSIGN/IF/FOR/WHILE/TRANSACTION/ONCE/REF_PUBLIC_CASE',
    name          VARCHAR(256) NOT NULL COMMENT '步骤名称',
    position      VARCHAR(8) NOT NULL DEFAULT 'TEST' COMMENT 'PRE/TEST/POST',
    sort_order    INT NOT NULL DEFAULT 0 COMMENT '同 parent 内排序号',
    enabled       TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    remark        VARCHAR(1024) COMMENT '步骤备注',
    config        LONGTEXT NOT NULL COMMENT 'JSON 配置（按 stepType 不同 schema）',
    fail_strategy VARCHAR(16) NOT NULL DEFAULT 'stop' COMMENT 'stop/continue',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_case (case_id),
    KEY idx_parent (parent_id),
    KEY idx_position (case_id, position)
) ENGINE = InnoDB COMMENT '用例步骤（10 种类型,支持嵌套）';

CREATE TABLE IF NOT EXISTS t_report_share (
    id               VARCHAR(32) NOT NULL PRIMARY KEY,
    token            VARCHAR(64) NOT NULL UNIQUE COMMENT 'URL 分享 token,32 位随机串',
    report_id        VARCHAR(32) NOT NULL,
    created_by       VARCHAR(64),
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at       DATETIME NOT NULL COMMENT '过期时间',
    password         VARCHAR(128) COMMENT '可选,BCrypt 哈希;null 表示无密码',
    revoked          TINYINT(1) NOT NULL DEFAULT 0,
    access_count     INT NOT NULL DEFAULT 0,
    last_accessed_at DATETIME,
    max_access_count INT COMMENT '可选,达到上限禁止访问',
    allowed_ips      VARCHAR(1024) COMMENT '可选,允许的客户端 IP/CIDR,逗号分隔',
    KEY idx_report (report_id),
    KEY idx_token (token)
) ENGINE = InnoDB COMMENT '报告分享链接';

CREATE TABLE IF NOT EXISTS t_report_share_log (
    id          VARCHAR(32) NOT NULL PRIMARY KEY,
    share_id    VARCHAR(32) NOT NULL,
    report_id   VARCHAR(32) NOT NULL,
    access_ip   VARCHAR(64),
    user_agent  VARCHAR(512),
    accessed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status      VARCHAR(32) NOT NULL COMMENT 'success/expired/revoked/wrong_password/rate_limited',
    KEY idx_share (share_id),
    KEY idx_accessed (accessed_at)
) ENGINE = InnoDB COMMENT '报告分享访问日志（审计）';

-- ----------------------------------------------------------------------------
-- 2. 补列（幂等：已存在则跳过）
-- ----------------------------------------------------------------------------

DELIMITER $$

DROP PROCEDURE IF EXISTS add_column_if_missing $$

CREATE PROCEDURE add_column_if_missing(
    IN p_table  VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_ddl    TEXT
)
BEGIN
    DECLARE cnt INT DEFAULT 0;
    SELECT COUNT(*) INTO cnt
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table
      AND COLUMN_NAME = p_column;
    IF cnt = 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

-- P0：模块表 / 接口表增加软删除字段（回收站功能依赖）
CALL add_column_if_missing('t_module', 'deleted_at',
    '`deleted_at` DATETIME NULL DEFAULT NULL COMMENT ''软删除时间（进回收站）''');

CALL add_column_if_missing('t_api_definition', 'deleted_at',
    '`deleted_at` DATETIME NULL DEFAULT NULL COMMENT ''软删除时间（进回收站）''');

-- P2：接口定义表补齐 Swagger 导入 / Mock 相关字段
CALL add_column_if_missing('t_api_definition', 'path_params',
    '`path_params` JSON NULL COMMENT ''路径参数 [{key,value,description}]''');
CALL add_column_if_missing('t_api_definition', 'body_type',
    '`body_type` VARCHAR(32) NOT NULL DEFAULT ''NONE'' COMMENT ''NONE/FORM_DATA/X_WWW_FORM_URLENCODED/JSON/XML/BINARY''');
CALL add_column_if_missing('t_api_definition', 'body_schema',
    '`body_schema` JSON NULL COMMENT ''请求体 Schema''');
CALL add_column_if_missing('t_api_definition', 'response_examples',
    '`response_examples` JSON NULL COMMENT ''按状态码 { "200": {...} }''');
CALL add_column_if_missing('t_api_definition', 'response_headers',
    '`response_headers` JSON NULL COMMENT ''响应头''');
CALL add_column_if_missing('t_api_definition', 'mock_type',
    '`mock_type` VARCHAR(16) NOT NULL DEFAULT ''BASIC'' COMMENT ''BASIC/CONDITIONAL/DYNAMIC/SMART''');
CALL add_column_if_missing('t_api_definition', 'mock_status_code',
    '`mock_status_code` INT NOT NULL DEFAULT 200 COMMENT ''Mock 返回状态码''');
CALL add_column_if_missing('t_api_definition', 'mock_rules',
    '`mock_rules` JSON NULL COMMENT ''CONDITIONAL 规则 [{when,response,status}]''');
CALL add_column_if_missing('t_api_definition', 'mock_script',
    '`mock_script` LONGTEXT NULL COMMENT ''DYNAMIC 类型 JS 脚本''');
CALL add_column_if_missing('t_api_definition', 'source_url',
    '`source_url` VARCHAR(512) NULL COMMENT ''Swagger 来源 URL''');
CALL add_column_if_missing('t_api_definition', 'source_hash',
    '`source_hash` VARCHAR(64) NULL COMMENT ''来源内容 SHA-256''');

-- P3：任务表补齐失败策略 / 并行池 / webhook 字段
CALL add_column_if_missing('t_test_task', 'fail_strategy',
    '`fail_strategy` VARCHAR(32) NOT NULL DEFAULT ''continue_all'' COMMENT ''stop_on_fail/continue_all/retry_then_stop''');
CALL add_column_if_missing('t_test_task', 'parallel_pool_size',
    '`parallel_pool_size` INT NOT NULL DEFAULT 1 COMMENT ''并行执行线程数''');
CALL add_column_if_missing('t_test_task', 'webhook_token',
    '`webhook_token` VARCHAR(64) NULL COMMENT ''CI/CD 触发令牌''');
CALL add_column_if_missing('t_test_task', 'webhook_enabled',
    '`webhook_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否开启 webhook 触发''');
CALL add_column_if_missing('t_test_task', 'webhook_auto_execute',
    '`webhook_auto_execute` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''webhook 触发后是否自动执行''');
CALL add_column_if_missing('t_test_task', 'notify_channels',
    '`notify_channels` JSON NULL COMMENT ''通知渠道配置（钉钉/企微/飞书/webhook）''');

-- P3：报告表补齐任务关联 / 触发方式 / 统计明细字段
CALL add_column_if_missing('t_report', 'task_id',
    '`task_id` VARCHAR(32) NULL COMMENT ''关联任务 ID,可空''');
CALL add_column_if_missing('t_report', 'finished_at',
    '`finished_at` DATETIME NULL COMMENT ''结束时间''');
CALL add_column_if_missing('t_report', 'trigger_type',
    '`trigger_type` VARCHAR(16) NOT NULL DEFAULT ''manual'' COMMENT ''manual/schedule/webhook/api''');
CALL add_column_if_missing('t_report', 'trigger_by',
    '`trigger_by` VARCHAR(64) NULL COMMENT ''触发人''');
CALL add_column_if_missing('t_report', 'total_cases',
    '`total_cases` INT NOT NULL DEFAULT 0 COMMENT ''用例总数''');
CALL add_column_if_missing('t_report', 'passed_cases',
    '`passed_cases` INT NOT NULL DEFAULT 0 COMMENT ''通过数''');
CALL add_column_if_missing('t_report', 'failed_cases',
    '`failed_cases` INT NOT NULL DEFAULT 0 COMMENT ''失败数''');
CALL add_column_if_missing('t_report', 'error_cases',
    '`error_cases` INT NOT NULL DEFAULT 0 COMMENT ''异常数''');
CALL add_column_if_missing('t_report', 'skipped_cases',
    '`skipped_cases` INT NOT NULL DEFAULT 0 COMMENT ''跳过数''');
CALL add_column_if_missing('t_report', 'total_assertions',
    '`total_assertions` INT NOT NULL DEFAULT 0 COMMENT ''断言总数''');
CALL add_column_if_missing('t_report', 'passed_assertions',
    '`passed_assertions` INT NOT NULL DEFAULT 0 COMMENT ''断言通过数''');
CALL add_column_if_missing('t_report', 'failed_assertions',
    '`failed_assertions` INT NOT NULL DEFAULT 0 COMMENT ''断言失败数''');
CALL add_column_if_missing('t_report', 'avg_response_time',
    '`avg_response_time` INT NOT NULL DEFAULT 0 COMMENT ''平均响应耗时(ms)''');
CALL add_column_if_missing('t_report', 'p95_response_time',
    '`p95_response_time` INT NOT NULL DEFAULT 0 COMMENT ''P95 响应耗时(ms)''');
CALL add_column_if_missing('t_report', 'environment_snapshot',
    '`environment_snapshot` VARCHAR(512) NULL COMMENT ''环境快照(JSON)''');

-- ----------------------------------------------------------------------------
-- 3. 收尾
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS add_column_if_missing;

SELECT 'migrate.sql 执行完成' AS result;
