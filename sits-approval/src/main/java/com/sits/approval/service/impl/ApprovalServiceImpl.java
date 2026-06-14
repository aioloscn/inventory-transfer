package com.sits.approval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sits.approval.entity.ApprovalRecord;
import com.sits.approval.mapper.ApprovalRecordMapper;
import com.sits.approval.service.ApprovalService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ApprovalServiceImpl implements ApprovalService {

    private final ApprovalRecordMapper approvalRecordMapper;

    public ApprovalServiceImpl(ApprovalRecordMapper approvalRecordMapper) {
        this.approvalRecordMapper = approvalRecordMapper;
    }

    @Override
    public ApprovalRecord record(String bizType, String bizNo, Long approverId,
                                  String result, String comment) {
        ApprovalRecord record = new ApprovalRecord();
        record.setBizType(bizType);
        record.setBizNo(bizNo);
        record.setApproverId(approverId);
        record.setApproveResult(result);
        record.setApproveComment(comment);
        record.setApproveTime(LocalDateTime.now());
        approvalRecordMapper.insert(record);
        return record;
    }

    @Override
    public List<ApprovalRecord> listByBizNo(String bizNo) {
        return approvalRecordMapper.selectList(
                new LambdaQueryWrapper<ApprovalRecord>()
                        .eq(ApprovalRecord::getBizNo, bizNo)
                        .orderByDesc(ApprovalRecord::getCreateTime)
        );
    }
}
