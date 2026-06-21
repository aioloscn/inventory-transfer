package com.sits.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sits.admin.entity.OperationLog;
import com.sits.admin.service.OperationLogService;
import com.sits.common.base.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统操作日志查询 API。
 */
@RestController
@RequestMapping("/api/operation-logs")
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /**
     * 分页查询操作日志。
     */
    @GetMapping
    public Result<Page<OperationLog>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String moduleName,
            @RequestParam(required = false) String requestMethod,
            @RequestParam(required = false) String status) {
        return Result.success(operationLogService.page(pageNum, pageSize, keyword, moduleName, requestMethod, status));
    }
}
