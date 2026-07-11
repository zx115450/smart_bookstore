package com.zx.bookstore.cart.service;

import com.zx.auth.security.AuthPrincipal;
import com.zx.bookstore.cart.dto.AddCartItemRequest;
import com.zx.bookstore.cart.dto.CartItemResponse;
import com.zx.bookstore.cart.dto.UpdateCartItemRequest;
import com.zx.bookstore.cart.entity.CartItem;
import com.zx.bookstore.cart.exception.CartException;
import com.zx.bookstore.cart.repository.CartRepository;
import com.zx.bookstore.catalog.entity.Book;
import com.zx.bookstore.catalog.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final BookRepository bookRepository;

    public List<CartItemResponse> listItems(AuthPrincipal principal) {
        return cartRepository.listByUserId(principal.userId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CartItemResponse addItem(AuthPrincipal principal, AddCartItemRequest req) {
        if (req == null || req.getBookId() == null) {
            throw new IllegalArgumentException("bookId 不能为空");
        }
        int quantity = req.getQuantity() == null || req.getQuantity() <= 0 ? 1 : req.getQuantity();

        Book book = bookRepository.findEnabledById(req.getBookId())
                .orElseThrow(CartException::bookNotFound);
        validateStock(book, quantity);

        CartItem item = cartRepository.findByUserIdAndBookId(principal.userId(), req.getBookId())
                .orElseGet(() -> {
                    CartItem created = new CartItem();
                    created.setUserId(principal.userId());
                    created.setBookId(req.getBookId());
                    created.setQuantity(0);
                    return created;
                });
        int newQuantity = item.getQuantity() + quantity;
        validateStock(book, newQuantity);
        item.setQuantity(newQuantity);
        cartRepository.save(item);
        return toResponse(item);
    }

    @Transactional
    public CartItemResponse updateItem(AuthPrincipal principal, Long itemId, UpdateCartItemRequest req) {
        if (req == null || req.getQuantity() == null) {
            throw new IllegalArgumentException("quantity 不能为空");
        }
        CartItem item = loadOwnedItem(principal, itemId);
        if (req.getQuantity() <= 0) {
            cartRepository.deleteById(itemId);
            return toResponse(item);
        }
        Book book = bookRepository.findEnabledById(item.getBookId())
                .orElseThrow(CartException::bookNotFound);
        validateStock(book, req.getQuantity());
        item.setQuantity(req.getQuantity());
        cartRepository.save(item);
        return toResponse(item);
    }

    @Transactional
    public void removeItem(AuthPrincipal principal, Long itemId) {
        CartItem item = loadOwnedItem(principal, itemId);
        cartRepository.deleteById(item.getId());
    }

    public List<CartItem> loadItemsForCheckout(Long userId, List<Long> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            throw CartException.emptyCart();
        }
        List<CartItem> items = cartRepository.findByIdsAndUserId(cartItemIds, userId);
        if (items.size() != cartItemIds.size()) {
            throw CartException.itemNotFound();
        }
        return items;
    }

    @Transactional
    public void removeItems(Long userId, List<Long> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            return;
        }
        cartRepository.deleteByIdsAndUserId(cartItemIds, userId);
    }

    private CartItem loadOwnedItem(AuthPrincipal principal, Long itemId) {
        CartItem item = cartRepository.findById(itemId)
                .orElseThrow(CartException::itemNotFound);
        if (!principal.userId().equals(item.getUserId())) {
            throw CartException.forbidden();
        }
        return item;
    }

    private void validateStock(Book book, int quantity) {
        int stock = book.getSaleStock() == null ? 0 : book.getSaleStock();
        if (stock < quantity) {
            throw CartException.outOfStock();
        }
    }

    private CartItemResponse toResponse(CartItem item) {
        CartItemResponse resp = new CartItemResponse();
        resp.setId(item.getId());
        resp.setBookId(item.getBookId());
        resp.setQuantity(item.getQuantity());
        bookRepository.findEnabledById(item.getBookId()).ifPresent(book -> {
            resp.setBookTitle(book.getTitle());
            resp.setCoverUrl(book.getCoverUrl());
            resp.setPrice(book.getPrice());
            resp.setSaleStock(book.getSaleStock());
            if (book.getPrice() != null && item.getQuantity() != null) {
                resp.setSubtotal(book.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        });
        return resp;
    }
}
