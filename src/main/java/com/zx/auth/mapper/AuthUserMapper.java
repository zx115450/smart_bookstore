package com.zx.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.auth.entity.AuthUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface AuthUserMapper extends BaseMapper<AuthUser> {

    @Select("SELECT * FROM auth_user WHERE id = #{id} FOR UPDATE")
    AuthUser selectByIdForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE auth_user
            SET balance = balance - #{amount}, updated_at = NOW()
            WHERE id = #{id} AND balance >= #{amount}
            """)
    int deductBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);
}
