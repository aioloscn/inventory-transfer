package com.sits.admin.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sits.admin.entity.OperationLog;
import com.sits.admin.entity.SysUser;
import com.sits.admin.mapper.SysUserMapper;
import com.sits.admin.service.OperationLogService;
import com.sits.common.base.Result;
import com.sits.common.dto.LoginRequest;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 控制器操作日志切面。
 *
 * <p>统一记录非查询型接口的调用情况，覆盖登录、登出、增删改、审批等写操作。
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    private static final List<String> WRITE_METHODS = List.of("POST", "PUT", "DELETE", "PATCH");
    private static final List<String> SENSITIVE_KEYS = List.of("password", "newPassword", "token", "tokenValue");

    private final OperationLogService operationLogService;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;

    public OperationLogAspect(OperationLogService operationLogService,
                              SysUserMapper sysUserMapper,
                              ObjectMapper objectMapper) {
        this.operationLogService = operationLogService;
        this.sysUserMapper = sysUserMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 拦截所有控制器公开方法。
     */
    @Around("execution(public * com.sits..controller..*(..))")
    public Object aroundController(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        String requestMethod = attributes.getRequest().getMethod();
        if (!WRITE_METHODS.contains(requestMethod.toUpperCase(Locale.ROOT))) {
            return joinPoint.proceed();
        }

        String requestUri = attributes.getRequest().getRequestURI();
        long start = System.currentTimeMillis();
        Long beforeLoginId = getCurrentLoginId();
        String requestParams = buildRequestParams(joinPoint.getArgs());
        String loginUsername = extractLoginUsername(joinPoint.getArgs());

        try {
            Object result = joinPoint.proceed();
            Long afterLoginId = getCurrentLoginId();
            String status = "SUCCESS";
            String errorMessage = null;
            if (result instanceof Result<?> apiResult && !apiResult.isSuccess()) {
                status = "FAIL";
                errorMessage = apiResult.getMessage();
            }
            saveLog(attributes, requestMethod, requestUri, requestParams, beforeLoginId, afterLoginId,
                    loginUsername, status, errorMessage, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable ex) {
            saveLog(attributes, requestMethod, requestUri, requestParams, beforeLoginId, beforeLoginId,
                    loginUsername, "FAIL", ex.getMessage(), System.currentTimeMillis() - start);
            throw ex;
        }
    }

    private void saveLog(ServletRequestAttributes attributes,
                         String requestMethod,
                         String requestUri,
                         String requestParams,
                         Long beforeLoginId,
                         Long afterLoginId,
                         String loginUsername,
                         String status,
                         String errorMessage,
                         long durationMs) {
        try {
            OperationLog operationLog = new OperationLog();
            operationLog.setModuleName(resolveModuleName(requestUri));
            operationLog.setOperationName(resolveOperationName(requestMethod, requestUri));
            operationLog.setRequestMethod(requestMethod);
            operationLog.setRequestUri(requestUri);
            operationLog.setRequestParams(requestParams);
            operationLog.setIpAddress(resolveIpAddress(attributes));
            operationLog.setStatus(status);
            operationLog.setErrorMessage(truncate(errorMessage, 1000));
            operationLog.setDurationMs(durationMs);

            Long operatorId = afterLoginId != null ? afterLoginId : beforeLoginId;
            operationLog.setOperatorId(operatorId);
            operationLog.setOperatorName(resolveOperatorName(operatorId, loginUsername));

            operationLogService.save(operationLog);
        } catch (Exception e) {
            log.warn("保存操作日志失败: {}", e.getMessage());
        }
    }

    private ServletRequestAttributes getRequestAttributes() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes;
        }
        return null;
    }

    private Long getCurrentLoginId() {
        if (!StpUtil.isLogin()) {
            return null;
        }
        Object loginId = StpUtil.getLoginId();
        if (loginId == null) {
            return null;
        }
        if (loginId instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(loginId));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveOperatorName(Long operatorId, String loginUsername) {
        if (operatorId != null) {
            SysUser user = sysUserMapper.selectById(operatorId);
            if (user != null) {
                return user.getRealName() != null && !user.getRealName().isBlank()
                        ? user.getRealName()
                        : user.getUsername();
            }
        }
        return loginUsername;
    }

    private String buildRequestParams(Object[] args) {
        List<Object> sanitizedArgs = new ArrayList<>();
        for (Object arg : args) {
            if (shouldSkipArg(arg)) {
                continue;
            }
            sanitizedArgs.add(maskSensitiveFields(arg));
        }
        try {
            return truncate(objectMapper.writeValueAsString(sanitizedArgs), 4000);
        } catch (Exception e) {
            return "[unserializable]";
        }
    }

    private boolean shouldSkipArg(Object arg) {
        return arg == null
                || arg instanceof ServletRequest
                || arg instanceof ServletResponse
                || arg instanceof MultipartFile;
    }

    private Object maskSensitiveFields(Object source) {
        JsonNode node = objectMapper.valueToTree(source);
        sanitizeNode(node);
        return node;
    }

    private void sanitizeNode(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node instanceof ObjectNode objectNode) {
            objectNode.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode child = entry.getValue();
                if (isSensitiveKey(fieldName)) {
                    objectNode.put(fieldName, "***");
                } else {
                    sanitizeNode(child);
                }
            });
            return;
        }
        if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::sanitizeNode);
        }
    }

    private boolean isSensitiveKey(String fieldName) {
        for (String key : SENSITIVE_KEYS) {
            if (key.equalsIgnoreCase(fieldName)) {
                return true;
            }
        }
        return false;
    }

    private String extractLoginUsername(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof LoginRequest loginRequest) {
                return loginRequest.getUserId();
            }
            if (arg instanceof Map<?, ?> map) {
                Object userId = map.get("userId");
                if (userId != null) {
                    return String.valueOf(userId);
                }
                Object username = map.get("username");
                if (username != null) {
                    return String.valueOf(username);
                }
            }
        }
        return null;
    }

    private String resolveModuleName(String requestUri) {
        if (requestUri.startsWith("/api/auth")) {
            return "认证中心";
        }
        if (requestUri.startsWith("/api/users") || requestUri.startsWith("/api/roles") || requestUri.startsWith("/api/operation-logs")) {
            return "系统管理";
        }
        if (requestUri.startsWith("/api/warehouses") || requestUri.startsWith("/api/skus")) {
            return "基础资料";
        }
        if (requestUri.startsWith("/api/inventories")) {
            return "库存中心";
        }
        if (requestUri.startsWith("/api/risks") || requestUri.startsWith("/api/rules")) {
            return "风控中心";
        }
        if (requestUri.startsWith("/api/transfer-orders")) {
            return "调拨中心";
        }
        if (requestUri.startsWith("/api/approvals")) {
            return "审批中心";
        }
        if (requestUri.startsWith("/api/ai")) {
            return "AI助手";
        }
        return "其他模块";
    }

    private String resolveOperationName(String requestMethod, String requestUri) {
        if ("/api/auth/login".equals(requestUri)) {
            return "用户登录";
        }
        if ("/api/auth/logout".equals(requestUri)) {
            return "用户登出";
        }
        if (requestUri.endsWith("/password")) {
            return "修改密码";
        }
        if (requestUri.endsWith("/status")) {
            return "切换状态";
        }
        if (requestUri.endsWith("/submit-approval")) {
            return "提交审批";
        }
        if (requestUri.endsWith("/approve")) {
            return "审批通过";
        }
        if (requestUri.endsWith("/reject")) {
            return "审批驳回";
        }
        if (requestUri.endsWith("/outbound")) {
            return "确认出库";
        }
        if (requestUri.endsWith("/ship")) {
            return "确认发运";
        }
        if (requestUri.endsWith("/inbound")) {
            return "确认入库";
        }
        if (requestUri.endsWith("/cancel")) {
            return "取消单据";
        }
        if ("POST".equalsIgnoreCase(requestMethod)) {
            return "新增记录";
        }
        if ("PUT".equalsIgnoreCase(requestMethod) || "PATCH".equalsIgnoreCase(requestMethod)) {
            return "更新记录";
        }
        if ("DELETE".equalsIgnoreCase(requestMethod)) {
            return "删除记录";
        }
        return requestMethod + " " + requestUri;
    }

    private String resolveIpAddress(ServletRequestAttributes attributes) {
        String forwarded = attributes.getRequest().getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return attributes.getRequest().getRemoteAddr();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
