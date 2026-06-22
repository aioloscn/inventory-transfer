-- V8: AI 模型配置表

-- AI 模型配置表
CREATE TABLE IF NOT EXISTS ai_model_config (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    provider        VARCHAR(32)     NOT NULL COMMENT '模型供应商：DEEPSEEK / VOLCENGINE_ARK',
    model_name      VARCHAR(128)    NOT NULL COMMENT '模型名称，如 deepseek-v4-pro、doubao-seed-2-0-pro',
    display_name    VARCHAR(128)    DEFAULT '' COMMENT '前端展示名称',
    api_key         VARCHAR(512)    DEFAULT '' COMMENT '加密后的 API Key',
    base_url        VARCHAR(255)    DEFAULT '' COMMENT 'API Base URL',
    enabled         TINYINT         DEFAULT 1 COMMENT '是否启用：1启用 0禁用',
    default_flag    TINYINT         DEFAULT 0 COMMENT '是否默认模型：1是 0否',
    current_flag    TINYINT         DEFAULT 0 COMMENT '是否当前正在使用：1是 0否',
    support_stream  TINYINT         DEFAULT 1 COMMENT '是否支持 SSE 流式输出：1支持 0不支持',
    support_tool_call TINYINT       DEFAULT 1 COMMENT '是否支持 Tool Calling：1支持 0不支持',
    remark          VARCHAR(255)    DEFAULT '' COMMENT '备注',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_provider_model (provider, model_name),
    INDEX idx_current_flag (current_flag),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 模型配置表';

-- 默认插入 DeepSeek 配置
INSERT INTO ai_model_config (provider, model_name, display_name, api_key, base_url, enabled, default_flag, current_flag, support_stream, support_tool_call, remark)
VALUES ('DEEPSEEK', 'deepseek-v4-pro', 'DeepSeek V4 Pro', '', 'https://api.deepseek.com', 1, 1, 1, 1, 1, '系统默认模型，请在模型管理中配置 API Key');

-- 插入火山方舟示例配置（启用但非当前模型）
INSERT INTO ai_model_config (provider, model_name, display_name, api_key, base_url, enabled, default_flag, current_flag, support_stream, support_tool_call, remark)
VALUES ('VOLCENGINE_ARK', 'doubao-seed-2-0-pro', '火山方舟 Doubao Pro', '', 'https://ark.cn-beijing.volces.com', 1, 0, 0, 1, 1, '火山方舟 Pro 模型，请在模型管理中配置 API Key 并确认模型名称');
