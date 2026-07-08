package com.zx.reservation.dto;

public class GenerateSeatsRequest {
    private Integer count;
    private String prefix = "A";
    private Integer rows;
    private Integer cols;

    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
    public Integer getRows() { return rows; }
    public void setRows(Integer rows) { this.rows = rows; }
    public Integer getCols() { return cols; }
    public void setCols(Integer cols) { this.cols = cols; }
}
