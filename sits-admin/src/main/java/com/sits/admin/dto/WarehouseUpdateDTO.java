package com.sits.admin.dto;

/**
 * Update warehouse request.
 */
public class WarehouseUpdateDTO {

    private String warehouseName;
    private String region;
    private String province;
    private String city;
    private String address;
    private Integer capacity;
    private Integer priority;

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
}
