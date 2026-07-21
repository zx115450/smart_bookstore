package com.zx.bookstore.cart.controller;

import com.zx.common.dto.ApiResponse;
import com.zx.auth.security.AuthAttributes;
import com.zx.auth.security.AuthPrincipal;
import com.zx.bookstore.cart.dto.AddCartItemRequest;
import com.zx.bookstore.cart.dto.CartItemResponse;
import com.zx.bookstore.cart.dto.UpdateCartItemRequest;
import com.zx.bookstore.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/items")
    public ApiResponse<List<CartItemResponse>> listItems(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal
    ) {
        return ApiResponse.ok(cartService.listItems(principal));
    }

    @PreAuthorize("hasRole('USER')")
    @PostMapping("/items")
    public ApiResponse<CartItemResponse> addItem(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @RequestBody AddCartItemRequest req
    ) {
        return ApiResponse.ok(cartService.addItem(principal, req));
    }

    @PreAuthorize("hasRole('USER')")
    @PutMapping("/items/{id}")
    public ApiResponse<CartItemResponse> updateItem(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody UpdateCartItemRequest req
    ) {
        return ApiResponse.ok(cartService.updateItem(principal, id, req));
    }

    @PreAuthorize("hasRole('USER')")
    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> removeItem(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long id
    ) {
        cartService.removeItem(principal, id);
        return ApiResponse.ok(null);
    }
}
