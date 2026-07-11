package com.zx.bookstore.cart.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.bookstore.cart.entity.CartItem;
import com.zx.bookstore.cart.mapper.CartItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CartRepository {

    private final CartItemMapper mapper;

    public List<CartItem> listByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return mapper.selectList(
                Wrappers.<CartItem>lambdaQuery()
                        .eq(CartItem::getUserId, userId)
                        .orderByDesc(CartItem::getId)
        );
    }

    public Optional<CartItem> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<CartItem> findByUserIdAndBookId(Long userId, Long bookId) {
        if (userId == null || bookId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<CartItem>lambdaQuery()
                        .eq(CartItem::getUserId, userId)
                        .eq(CartItem::getBookId, bookId)
        ));
    }

    public List<CartItem> findByIdsAndUserId(Collection<Long> ids, Long userId) {
        if (userId == null || ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(
                Wrappers.<CartItem>lambdaQuery()
                        .eq(CartItem::getUserId, userId)
                        .in(CartItem::getId, ids)
        );
    }

    public CartItem save(CartItem item) {
        LocalDateTime now = LocalDateTime.now();
        if (item.getId() == null) {
            if (item.getCreatedAt() == null) {
                item.setCreatedAt(now);
            }
            item.setUpdatedAt(now);
            mapper.insert(item);
            return item;
        }
        item.setUpdatedAt(now);
        mapper.updateById(item);
        return item;
    }

    public int deleteById(Long id) {
        if (id == null) {
            return 0;
        }
        return mapper.deleteById(id);
    }

    public int deleteByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return mapper.delete(
                Wrappers.<CartItem>lambdaQuery().in(CartItem::getId, ids)
        );
    }

    public int deleteByIdsAndUserId(Collection<Long> ids, Long userId) {
        if (userId == null || ids == null || ids.isEmpty()) {
            return 0;
        }
        return mapper.delete(
                Wrappers.<CartItem>lambdaQuery()
                        .eq(CartItem::getUserId, userId)
                        .in(CartItem::getId, ids)
        );
    }
}
