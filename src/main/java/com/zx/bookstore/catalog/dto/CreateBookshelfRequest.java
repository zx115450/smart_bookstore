package com.zx.bookstore.catalog.dto;

public class CreateBookshelfRequest {

    private Integer floor;
    private String code;

    public Integer getFloor() { return floor; }
    public void setFloor(Integer floor) { this.floor = floor; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
