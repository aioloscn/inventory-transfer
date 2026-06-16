-- ============================================================
-- 15. sys_role — 系统角色表
-- ============================================================
CREATE TABLE sys_role (
    id              BIGINT          NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
    role_code       VARCHAR(32)     NOT NULL                       COMMENT '角色编码',
    role_name       VARCHAR(64)     NOT NULL                       COMMENT '角色名称',
    description     VARCHAR(255)    DEFAULT NULL                   COMMENT '角色说明',
    status          VARCHAR(16)     NOT NULL DEFAULT 'ENABLED'     COMMENT '状态：ENABLED/DISABLED',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

-- 初始化 4 个系统角色
INSERT INTO sys_role (role_code, role_name, description) VALUES
('ADMIN',      '系统管理员',   '拥有全部权限，可管理仓库、SKU、用户、角色、规则配置，查看所有业务数据'),
('OPERATOR',   '库存运营',     '查看库存/风险数据，确认调拨建议，创建和取消调拨单，查看AI分析结果'),
('WAREHOUSE',  '仓库管理员',   '查看待出/入库调拨单，确认出库和入库操作，查看本仓库库存数据'),
('SUPERVISOR', '供应链主管',   '审批调拨单，查看调拨报表和库存风险分析报告');
