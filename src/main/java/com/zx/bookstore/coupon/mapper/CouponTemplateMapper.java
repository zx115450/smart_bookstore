package com.zx.bookstore.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.bookstore.coupon.entity.CouponTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CouponTemplateMapper extends BaseMapper<CouponTemplate> {

    @Update("""
            UPDATE coupon_template
            SET issued_count = issued_count + 1, updated_at = NOW()
            WHERE id = #{id}
              AND status = 1
              AND (total_count = 0 OR issued_count < total_count)
            """)
    int incrementIssuedCount(@Param("id") Long id);
}
