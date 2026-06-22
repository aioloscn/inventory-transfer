package com.sits.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 模型配置实体。
 */
@TableName("ai_model_config")
@Data
public class AiModelConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String provider;
    private String modelName;
    private String displayName;
    private String apiKey;
    private String baseUrl;
    private Integer enabled;
    private Integer defaultFlag;
    private Integer currentFlag;
    private Integer supportStream;
    private Integer supportToolCall;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
