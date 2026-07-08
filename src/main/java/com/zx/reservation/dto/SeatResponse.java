package com.zx.reservation.dto;

public class SeatResponse {
    private Long id;
    private Long resourceId;
    private String seatNo;
    private Integer rowNum;
    private Integer colNum;
    private Integer status;
    private Boolean available;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public String getSeatNo() { return seatNo; }
    public void setSeatNo(String seatNo) { this.seatNo = seatNo; }
    public Integer getRowNum() { return rowNum; }
    public void setRowNum(Integer rowNum) { this.rowNum = rowNum; }
    public Integer getColNum() { return colNum; }
    public void setColNum(Integer colNum) { this.colNum = colNum; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean available) { this.available = available; }
}
