package com.zx.reservation.dto;

public class ResourceResponse {
    private Long id;
    private String name;
    private String location;
    private Integer totalCapacity;
    private Integer status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Integer getTotalCapacity() { return totalCapacity; }
    public void setTotalCapacity(Integer totalCapacity) { this.totalCapacity = totalCapacity; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
