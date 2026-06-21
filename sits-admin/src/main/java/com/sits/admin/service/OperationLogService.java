package com.sits.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sits.admin.entity.OperationLog;

/**
 * 系统操作日志服务。
 */
public interface OperationLogService {

    /**
     * 分页查询操作日志。
     *
     * @param pageNum       页码
     * @param pageSize      每页条数
     * @param keyword       关键词，匹配操作名称、路径、操作人
     * @param moduleName    模块名称
     * @param requestMethod 请求方式
     * @param status        执行状态
     * @return 分页结果
     */
    Page<OperationLog> page(int pageNum, int pageSize, String keyword, String moduleName, String requestMethod, String status);

    /**
     * 保存一条操作日志。
     *
     * @param operationLog 日志实体
     */
    void save(OperationLog operationLog);
}
