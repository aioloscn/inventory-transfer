package com.sits.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sits.common.base.BaseEntity;

/**
 * 系统操作日志实体。
 *
 * <p>记录控制器写操作的留痕信息，便于后台统一查询和审计。
 */
@TableName("operation_log")
public class OperationLog extends BaseEntity {

    /** 模块名称 */
    private String moduleName;

    /** 操作名称 */
    private String operationName;

    /** 请求方式 */
    private String requestMethod;

    /** 请求路径 */
    private String requestUri;

    /** 请求参数快照 */
    private String requestParams;

    /** 操作人 ID */
    private Long operatorId;

    /** 操作人名称 */
    private String operatorName;

    /** 来源 IP */
    private String ipAddress;

    /** 执行状态：SUCCESS / FAIL */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 执行耗时（毫秒） */
    private Long durationMs;

    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }

    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }

    public String getRequestMethod() { return requestMethod; }
    public void setRequestMethod(String requestMethod) { this.requestMethod = requestMethod; }

    public String getRequestUri() { return requestUri; }
    public void setRequestUri(String requestUri) { this.requestUri = requestUri; }

    public String getRequestParams() { return requestParams; }
    public void setRequestParams(String requestParams) { this.requestParams = requestParams; }

    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }

    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
}
