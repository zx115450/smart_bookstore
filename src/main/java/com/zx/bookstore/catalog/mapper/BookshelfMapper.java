package com.zx.bookstore.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.bookstore.catalog.entity.Bookshelf;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BookshelfMapper extends BaseMapper<Bookshelf> {
}
