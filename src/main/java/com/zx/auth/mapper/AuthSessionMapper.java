package com.zx.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.auth.entity.AuthSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthSessionMapper extends BaseMapper<AuthSession> {
}
