package com.zx.bookstore.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.bookstore.catalog.entity.BookCategory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BookCategoryMapper extends BaseMapper<BookCategory> {
}
