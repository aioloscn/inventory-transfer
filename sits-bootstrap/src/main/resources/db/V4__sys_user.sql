-- ============================================================
-- 14. sys_user — 系统用户表
-- ============================================================
CREATE TABLE sys_user (
    id              BIGINT          NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
    username        VARCHAR(64)     NOT NULL                       COMMENT '登录用户名',
    password        VARCHAR(255)    NOT NULL                       COMMENT 'BCrypt 加密密码',
    real_name       VARCHAR(64)     DEFAULT NULL                   COMMENT '真实姓名',
    phone           VARCHAR(20)     DEFAULT NULL                   COMMENT '联系电话',
    email           VARCHAR(128)    DEFAULT NULL                   COMMENT '邮箱',
    role_code       VARCHAR(32)     NOT NULL                       COMMENT '角色编码：ADMIN/OPERATOR/WAREHOUSE/SUPERVISOR',
    status          VARCHAR(16)     NOT NULL DEFAULT 'ENABLED'     COMMENT '状态：ENABLED/DISABLED',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- 初始化 4 个角色用户，默认密码均为 123456（BCrypt 加密）
INSERT INTO sys_user (username, password, real_name, role_code, status) VALUES
('admin',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '系统管理员',   'ADMIN',      'ENABLED'),
('operator',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '库存运营',     'OPERATOR',   'ENABLED'),
('warehouse', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '仓库管理员',   'WAREHOUSE',  'ENABLED'),
('supervisor','$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '供应链主管',   'SUPERVISOR', 'ENABLED');
