package com.zx.bookstore.catalog.dto;

public class UpdateBookCategoryRequest {
    private String name;
    private Integer sort;
    private Integer status;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
