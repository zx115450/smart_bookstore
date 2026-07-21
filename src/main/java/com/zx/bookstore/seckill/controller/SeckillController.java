package com.zx.bookstore.seckill.controller;

import com.zx.common.dto.ApiResponse;
import com.zx.auth.security.AuthAttributes;
import com.zx.auth.security.AuthPrincipal;
import com.zx.bookstore.seckill.dto.SeckillActivityResponse;
import com.zx.bookstore.seckill.dto.SeckillGrabRequest;
import com.zx.bookstore.seckill.dto.SeckillGrabResponse;
import com.zx.bookstore.seckill.dto.SeckillResultResponse;
import com.zx.bookstore.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** P4 用户端秒杀 API：活动浏览、抢券、结果轮询。 */
@RestController
@RequestMapping("/api/seckill/activities")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    @PreAuthorize("hasRole('USER')")
    @GetMapping
    public ApiResponse<List<SeckillActivityResponse>> listActivities() {
        return ApiResponse.ok(seckillService.listActivities());
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/{id}")
    public ApiResponse<SeckillActivityResponse> getActivity(@PathVariable Long id) {
        return ApiResponse.ok(seckillService.getActivity(id));
    }

    /** 抢券：需传 idempotencyKey，前端重试时复用同一 key。 */
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/{id}/grab")
    public ApiResponse<SeckillGrabResponse> grab(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody SeckillGrabRequest req
    ) {
        return ApiResponse.ok(seckillService.grab(principal, id, req));
    }

    /** 轮询结果：PROCESSING → SUCCESS / FAILED。 */
    @PreAuthorize("hasRole('USER')")
    @GetMapping("/{id}/result")
    public ApiResponse<SeckillResultResponse> getResult(
            @RequestAttribute(AuthAttributes.AUTH_USER) AuthPrincipal principal,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(seckillService.getResult(principal, id));
    }
}
