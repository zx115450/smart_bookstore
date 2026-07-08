package com.zx.bookstore.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.bookstore.catalog.entity.Book;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BookMapper extends BaseMapper<Book> {

    @Select("SELECT * FROM book WHERE id = #{id} FOR UPDATE")
    Book selectByIdForUpdate(@Param("id") Long id);

    @org.apache.ibatis.annotations.Update("""
            UPDATE book
            SET sale_stock = sale_stock - #{qty}, updated_at = NOW()
            WHERE id = #{id} AND sale_stock >= #{qty}
            """)
    int deductSaleStock(@Param("id") Long id, @Param("qty") int qty);
}
