package com.zx.bookstore.seckill.controller;

import com.zx.auth.dto.ApiResponse;
import com.zx.bookstore.seckill.dto.CreateSeckillActivityRequest;
import com.zx.bookstore.seckill.dto.SeckillActivityResponse;
import com.zx.bookstore.seckill.dto.UpdateSeckillActivityRequest;
import com.zx.bookstore.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** P4/P5 管理端：秒杀活动配置（含 Redis 库存预热）。 */
@RestController
@RequestMapping("/api/admin/seckill/activities")
@RequiredArgsConstructor
public class SeckillAdminController {

    private final SeckillService seckillService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ApiResponse<SeckillActivityResponse> create(@RequestBody CreateSeckillActivityRequest req) {
        return ApiResponse.ok(seckillService.createActivity(req));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ApiResponse<List<SeckillActivityResponse>> list() {
        return ApiResponse.ok(seckillService.listAllActivitiesAdmin());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ApiResponse<SeckillActivityResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(seckillService.getActivityAdmin(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<SeckillActivityResponse> update(
            @PathVariable Long id,
            @RequestBody UpdateSeckillActivityRequest req
    ) {
        return ApiResponse.ok(seckillService.updateActivity(id, req));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        seckillService.disableActivity(id);
        return ApiResponse.ok(null);
    }
}
