-- ============================================================
-- 16. operation_log — 系统操作日志表
-- ============================================================
CREATE TABLE operation_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
    module_name     VARCHAR(64)     NOT NULL                       COMMENT '模块名称',
    operation_name  VARCHAR(128)    NOT NULL                       COMMENT '操作名称',
    request_method  VARCHAR(16)     NOT NULL                       COMMENT '请求方式',
    request_uri     VARCHAR(255)    NOT NULL                       COMMENT '请求路径',
    request_params  TEXT            DEFAULT NULL                   COMMENT '请求参数快照',
    operator_id     BIGINT          DEFAULT NULL                   COMMENT '操作人ID',
    operator_name   VARCHAR(64)     DEFAULT NULL                   COMMENT '操作人名称',
    ip_address      VARCHAR(64)     DEFAULT NULL                   COMMENT '来源IP',
    status          VARCHAR(16)     NOT NULL DEFAULT 'SUCCESS'     COMMENT '执行状态：SUCCESS/FAIL',
    error_message   VARCHAR(1000)   DEFAULT NULL                   COMMENT '错误信息',
    duration_ms     BIGINT          NOT NULL DEFAULT 0             COMMENT '执行耗时（毫秒）',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_module_create_time (module_name, create_time),
    KEY idx_operator_create_time (operator_id, create_time),
    KEY idx_status_create_time (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统操作日志表';
