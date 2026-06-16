package com.sits.admin.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * 创建 SKU 请求 DTO。
 */
public class SkuCreateDTO {

    /** SKU 编码 */
    @NotBlank(message = "SKU编码不能为空")
    private String skuCode;

    /** SKU 名称 */
    @NotBlank(message = "SKU名称不能为空")
    private String skuName;

    /** 分类 ID */
    private Long categoryId;

    /** 品牌 */
    private String brand;

    /** 单位 */
    private String unit;

    /** 成本价 */
    private BigDecimal costPrice;

    /** 销售价 */
    private BigDecimal salePrice;

    /** 重量 */
    private BigDecimal weight;

    /** 体积 */
    private BigDecimal volume;

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
}
