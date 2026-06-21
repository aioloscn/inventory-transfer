package com.sits.approval.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sits.common.entity.ApprovalRecord;
import com.sits.common.mapper.ApprovalRecordMapper;
import com.sits.approval.service.ApprovalService;
import com.sits.common.base.PageQuery;
import com.sits.common.base.PageResult;
import com.sits.common.exception.BusinessException;
import com.sits.transfer.service.TransferOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ApprovalServiceImpl implements ApprovalService {

    private final ApprovalRecordMapper approvalRecordMapper;
    private final TransferOrderService transferOrderService;

    public ApprovalServiceImpl(ApprovalRecordMapper approvalRecordMapper,
                                TransferOrderService transferOrderService) {
        this.approvalRecordMapper = approvalRecordMapper;
        this.transferOrderService = transferOrderService;
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

    @Override
    public PageResult<ApprovalRecord> page(PageQuery query, String status) {
        LambdaQueryWrapper<ApprovalRecord> wrapper = new LambdaQueryWrapper<ApprovalRecord>()
                .orderByDesc(ApprovalRecord::getCreateTime);
        if (status != null && !status.isBlank()) {
            if ("PENDING".equals(status)) {
                wrapper.and(w -> w.isNull(ApprovalRecord::getApproveResult)
                        .or()
                        .eq(ApprovalRecord::getApproveResult, "PENDING"));
            } else {
                wrapper.eq(ApprovalRecord::getApproveResult, status);
            }
        }
        Page<ApprovalRecord> page = approvalRecordMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page);
    }

    @Override
    @Transactional
    public void approve(Long id, String comment) {
        ApprovalRecord record = approvalRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("审批记录不存在: " + id);
        }

        // 实际审批人
        String approver = StpUtil.getLoginIdAsString();

        transferOrderService.approve(record.getBizNo(), approver, comment);

        record.setApproverId(parseLong(approver));
        record.setApproveResult("APPROVED");
        record.setApproveComment(comment);
        record.setApproveTime(LocalDateTime.now());
        approvalRecordMapper.updateById(record);
    }

    @Override
    @Transactional
    public void reject(Long id, String comment) {
        ApprovalRecord record = approvalRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("审批记录不存在: " + id);
        }

        // 实际审批人
        String approver = StpUtil.getLoginIdAsString();

        transferOrderService.reject(record.getBizNo(), approver, comment);

        record.setApproverId(parseLong(approver));
        record.setApproveResult("REJECTED");
        record.setApproveComment(comment);
        record.setApproveTime(LocalDateTime.now());
        approvalRecordMapper.updateById(record);
    }

    private Long parseLong(String s) {
        if (s == null) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
