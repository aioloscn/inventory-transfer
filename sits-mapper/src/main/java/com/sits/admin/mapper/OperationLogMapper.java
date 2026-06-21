package com.sits.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sits.admin.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统操作日志 Mapper。
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
