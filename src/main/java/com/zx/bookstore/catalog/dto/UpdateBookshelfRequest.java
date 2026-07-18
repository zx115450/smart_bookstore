package com.zx.bookstore.catalog.dto;

public class UpdateBookshelfRequest {

    private Integer floor;
    private String code;
    private Integer status;

    public Integer getFloor() { return floor; }
    public void setFloor(Integer floor) { this.floor = floor; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
