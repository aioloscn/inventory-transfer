package com.sits.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sits.common.base.BaseEntity;
import com.sits.common.enums.WarehouseStatus;

/**
 * Warehouse entity.
 */
@TableName("warehouse")
public class Warehouse extends BaseEntity {

    private String warehouseCode;
    private String warehouseName;
    private String region;
    private String province;
    private String city;
    private String address;
    private Integer capacity;
    private Integer priority;
    private Integer status;

    // -- getters / setters --

    public String getWarehouseCode() { return warehouseCode; }
    public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }

    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public boolean isNormal() {
        return status != null && status == WarehouseStatus.NORMAL.getCode();
    }
}
