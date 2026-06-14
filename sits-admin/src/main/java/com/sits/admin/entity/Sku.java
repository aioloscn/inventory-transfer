package com.sits.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sits.common.base.BaseEntity;

import java.math.BigDecimal;

/**
 * SKU entity.
 */
@TableName("sku")
public class Sku extends BaseEntity {

    private String skuCode;
    private String skuName;
    private Long categoryId;
    private String brand;
    private String unit;
    private BigDecimal costPrice;
    private BigDecimal salePrice;
    private BigDecimal weight;
    private BigDecimal volume;
    private Integer status;

    // -- getters / setters --

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public String getSkuName() { return skuName; }
    public void setSkuName(String skuName) { this.skuName = skuName; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }

    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }

    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }

    public BigDecimal getVolume() { return volume; }
    public void setVolume(BigDecimal volume) { this.volume = volume; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
