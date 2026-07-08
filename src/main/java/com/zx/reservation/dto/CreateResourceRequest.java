package com.zx.reservation.dto;

public class CreateResourceRequest {
    private String name;
    private String location;
    private Integer totalCapacity;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Integer getTotalCapacity() { return totalCapacity; }
    public void setTotalCapacity(Integer totalCapacity) { this.totalCapacity = totalCapacity; }
}
