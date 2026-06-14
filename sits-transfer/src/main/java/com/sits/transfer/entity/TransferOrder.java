package com.sits.transfer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.sits.common.base.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transfer order entity.
 */
@TableName("transfer_order")
public class TransferOrder extends BaseEntity {

    private String transferNo;
    private Long suggestionId;
    private Long skuId;
    private Long sourceWarehouseId;
    private Long targetWarehouseId;
    private Integer transferQuantity;
    private BigDecimal transferAmount;
    private String status;
    private Long applicantId;
    private Long approverId;

    @Version
    private Integer version;

    public String getTransferNo() { return transferNo; }
    public void setTransferNo(String transferNo) { this.transferNo = transferNo; }
    public Long getSuggestionId() { return suggestionId; }
    public void setSuggestionId(Long suggestionId) { this.suggestionId = suggestionId; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public Long getSourceWarehouseId() { return sourceWarehouseId; }
    public void setSourceWarehouseId(Long sourceWarehouseId) { this.sourceWarehouseId = sourceWarehouseId; }
    public Long getTargetWarehouseId() { return targetWarehouseId; }
    public void setTargetWarehouseId(Long targetWarehouseId) { this.targetWarehouseId = targetWarehouseId; }
    public Integer getTransferQuantity() { return transferQuantity; }
    public void setTransferQuantity(Integer transferQuantity) { this.transferQuantity = transferQuantity; }
    public BigDecimal getTransferAmount() { return transferAmount; }
    public void setTransferAmount(BigDecimal transferAmount) { this.transferAmount = transferAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
    public Long getApproverId() { return approverId; }
    public void setApproverId(Long approverId) { this.approverId = approverId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
