package com.zx.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.auth.entity.AuthUserIdentity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthUserIdentityMapper extends BaseMapper<AuthUserIdentity> {
}
