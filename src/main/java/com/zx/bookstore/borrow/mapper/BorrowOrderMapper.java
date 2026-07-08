package com.zx.bookstore.borrow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.bookstore.borrow.entity.BorrowOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface BorrowOrderMapper extends BaseMapper<BorrowOrder> {

    @Update("""
            UPDATE borrow_order
            SET status = 'BORROWED', borrow_at = #{borrowAt}, due_at = #{dueAt}, updated_at = NOW()
            WHERE id = #{id} AND status = 'APPLIED'
            """)
    int updateToBorrowed(@Param("id") Long id,
                         @Param("borrowAt") LocalDateTime borrowAt,
                         @Param("dueAt") LocalDateTime dueAt);

    @Update("""
            UPDATE borrow_order
            SET status = 'CANCELLED', updated_at = NOW()
            WHERE id = #{id} AND status = 'APPLIED'
            """)
    int updateToCancelled(@Param("id") Long id);

    @Update("""
            UPDATE borrow_order
            SET status = 'RETURNED', return_at = #{returnAt}, updated_at = NOW()
            WHERE id = #{id} AND status IN ('BORROWED', 'OVERDUE')
            """)
    int updateToReturned(@Param("id") Long id, @Param("returnAt") LocalDateTime returnAt);
}
