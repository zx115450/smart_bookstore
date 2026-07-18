package com.zx.bookstore.borrow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zx.bookstore.borrow.entity.BorrowOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface BorrowOrderMapper extends BaseMapper<BorrowOrder> {

    @Select("""
            SELECT book_id AS bookId, COUNT(*) AS heat
            FROM borrow_order
            WHERE status IN ('APPLIED','BORROWED','OVERDUE','RETURNED')
            GROUP BY book_id
            ORDER BY heat DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> findHotBorrowBooks(@Param("limit") int limit);

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

    @Update("""
            UPDATE borrow_order
            SET status = 'OVERDUE', updated_at = NOW()
            WHERE id = #{id} AND status = 'BORROWED'
            """)
    int updateToOverdue(@Param("id") Long id);

    @Update("""
            UPDATE borrow_order
            SET status = 'OVERDUE', updated_at = NOW()
            WHERE status = 'BORROWED' AND due_at < NOW()
            LIMIT #{limit}
            """)
    int updateOverdueBatch(@Param("limit") int limit);

    /**
     * 简化共现召回：找到与目标用户借过相同书的其他用户，再聚合他们借过的其他书。
     * <p>
     * 用于个性化推荐（H 板块）：以「借过同样书的人还借过什么」作为共现信号，
     * 排除目标用户自己借过的书。返回 bookId + 共现次数（heat）。
     *
     * @param userId   目标用户 id（排除其自身借阅记录）
     * @param bookIds  目标用户已借过的书目 id 列表（非空）
     * @param limit    最多返回条数
     */
    @Select("""
            <script>
            SELECT b2.book_id AS bookId, COUNT(*) AS heat
            FROM borrow_order b1
            JOIN borrow_order b2
              ON b1.user_id = b2.user_id AND b2.book_id &lt;&gt; b1.book_id
            WHERE b1.user_id &lt;&gt; #{userId}
              AND b1.book_id IN
              <foreach collection='bookIds' item='id' open='(' separator=',' close=')'>
                #{id}
              </foreach>
              AND b2.status IN ('APPLIED','BORROWED','OVERDUE','RETURNED')
            GROUP BY b2.book_id
            ORDER BY heat DESC
            LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> findCoBorrowedBooks(@Param("userId") Long userId,
                                                   @Param("bookIds") List<Long> bookIds,
                                                   @Param("limit") int limit);
}
