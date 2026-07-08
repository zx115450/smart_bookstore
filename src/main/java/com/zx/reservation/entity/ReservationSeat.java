package com.zx.reservation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("reservation_seat")
public class ReservationSeat {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("resource_id")
    private Long resourceId;

    @TableField("seat_no")
    private String seatNo;

    @TableField("row_num")
    private Integer rowNum;

    @TableField("col_num")
    private Integer colNum;

    @TableField("status")
    private Integer status = 1;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
