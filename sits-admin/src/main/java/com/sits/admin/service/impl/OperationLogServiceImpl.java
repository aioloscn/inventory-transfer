package com.sits.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sits.admin.entity.OperationLog;
import com.sits.admin.mapper.OperationLogMapper;
import com.sits.admin.service.OperationLogService;
import org.springframework.stereotype.Service;

/**
 * 系统操作日志服务实现。
 */
@Service
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public OperationLogServiceImpl(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    public Page<OperationLog> page(int pageNum, int pageSize, String keyword, String moduleName, String requestMethod, String status) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(OperationLog::getOperationName, keyword)
                    .or()
                    .like(OperationLog::getRequestUri, keyword)
                    .or()
                    .like(OperationLog::getOperatorName, keyword));
        }
        if (moduleName != null && !moduleName.isBlank()) {
            wrapper.eq(OperationLog::getModuleName, moduleName);
        }
        if (requestMethod != null && !requestMethod.isBlank()) {
            wrapper.eq(OperationLog::getRequestMethod, requestMethod);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(OperationLog::getStatus, status);
        }
        wrapper.orderByDesc(OperationLog::getCreateTime).orderByDesc(OperationLog::getId);
        return operationLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public void save(OperationLog operationLog) {
        operationLogMapper.insert(operationLog);
    }
}
