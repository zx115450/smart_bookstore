package com.zx.bookstore.seckill.service;

import com.zx.auth.security.AuthPrincipal;
import com.zx.bookstore.coupon.repository.CouponRepository;
import com.zx.bookstore.coupon.service.CouponService;
import com.zx.bookstore.seckill.dto.SeckillGrabRequest;
import com.zx.bookstore.seckill.dto.SeckillGrabResponse;
import com.zx.bookstore.seckill.dto.SeckillOrderMessage;
import com.zx.bookstore.seckill.entity.SeckillActivity;
import com.zx.bookstore.seckill.entity.SeckillOrder;
import com.zx.bookstore.seckill.enums.SeckillOrderStatus;
import com.zx.bookstore.seckill.exception.SeckillException;
import com.zx.bookstore.seckill.repository.SeckillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 秒杀 grab 热路径单测：售罄、已参与、幂等重试、MQ 发送失败回滚。
 * <p>
 * Mock Redis / MQ / 仓储，不启动 Spring，不连真实中间件。
 */
@ExtendWith(MockitoExtension.class)
class SeckillServiceTest {

    @Mock
    private SeckillRepository seckillRepository;
    @Mock
    private SeckillRedisService seckillRedisService;
    @Mock
    private SeckillRateLimitService seckillRateLimitService;
    @Mock
    private SeckillMqProducer seckillMqProducer;
    @Mock
    private CouponRepository couponRepository;
    @Mock
    private CouponService couponService;

    @InjectMocks
    private SeckillService seckillService;

    private final AuthPrincipal user = new AuthPrincipal(1L, "user", 100L, List.of("USER"));
    private SeckillActivity activity;

    @BeforeEach
    void setUp() {
        activity = new SeckillActivity();
        activity.setId(10L);
        activity.setName("测试秒杀");
        activity.setTemplateId(1L);
        activity.setSeckillStock(50);
        activity.setStatus(1);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
    }

    @Test
    void grab_shouldThrowSoldOut_whenLuaReturns0() {
        when(seckillRepository.findActivityById(10L)).thenReturn(Optional.of(activity));
        when(seckillRepository.findOrderByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(seckillRepository.findOrderByUserAndActivity(1L, 10L)).thenReturn(Optional.empty());
        when(seckillRedisService.grab(10L, 1L)).thenReturn(0L);

        SeckillGrabRequest req = new SeckillGrabRequest();
        req.setIdempotencyKey("key-1");

        SeckillException ex = assertThrows(SeckillException.class,
                () -> seckillService.grab(user, 10L, req));
        assertTrue(ex.getMessage().contains("售罄"));
        verify(seckillMqProducer, never()).publishSeckillOrder(any());
        verify(seckillRedisService, never()).rollbackGrab(anyLong(), anyLong());
        verify(seckillRateLimitService).assertAllowed(10L, 1L);
    }

    @Test
    void grab_shouldThrowRateLimited_whenTokenBucketDenies() {
        when(seckillRepository.findActivityById(10L)).thenReturn(Optional.of(activity));
        when(seckillRepository.findOrderByIdempotencyKey("key-rl")).thenReturn(Optional.empty());
        when(seckillRepository.findOrderByUserAndActivity(1L, 10L)).thenReturn(Optional.empty());
        doThrow(SeckillException.rateLimited())
                .when(seckillRateLimitService).assertAllowed(10L, 1L);

        SeckillGrabRequest req = new SeckillGrabRequest();
        req.setIdempotencyKey("key-rl");

        SeckillException ex = assertThrows(SeckillException.class,
                () -> seckillService.grab(user, 10L, req));
        assertTrue(ex.getMessage().contains("火爆"));
        verify(seckillRedisService, never()).grab(anyLong(), anyLong());
        verify(seckillMqProducer, never()).publishSeckillOrder(any());
    }

    @Test
    void grab_shouldThrowAlreadyParticipated_whenLuaReturns2() {
        when(seckillRepository.findActivityById(10L)).thenReturn(Optional.of(activity));
        when(seckillRepository.findOrderByIdempotencyKey("key-2")).thenReturn(Optional.empty());
        when(seckillRepository.findOrderByUserAndActivity(1L, 10L)).thenReturn(Optional.empty());
        when(seckillRedisService.grab(10L, 1L)).thenReturn(2L);

        SeckillGrabRequest req = new SeckillGrabRequest();
        req.setIdempotencyKey("key-2");

        SeckillException ex = assertThrows(SeckillException.class,
                () -> seckillService.grab(user, 10L, req));
        assertTrue(ex.getMessage().contains("已参与"));
        verify(seckillMqProducer, never()).publishSeckillOrder(any());
    }

    @Test
    void grab_shouldReturnExistingOrder_whenSameIdempotencyKey() {
        when(seckillRepository.findActivityById(10L)).thenReturn(Optional.of(activity));

        SeckillOrder existing = new SeckillOrder();
        existing.setId(99L);
        existing.setUserId(1L);
        existing.setActivityId(10L);
        existing.setStatus(SeckillOrderStatus.SUCCESS.name());
        existing.setIdempotencyKey("same-key");
        when(seckillRepository.findOrderByIdempotencyKey("same-key")).thenReturn(Optional.of(existing));

        SeckillGrabRequest req = new SeckillGrabRequest();
        req.setIdempotencyKey("same-key");

        SeckillGrabResponse response = seckillService.grab(user, 10L, req);

        assertEquals(SeckillOrderStatus.SUCCESS.name(), response.getStatus());
        verify(seckillRedisService, never()).grab(anyLong(), anyLong());
        verify(seckillMqProducer, never()).publishSeckillOrder(any());
    }

    @Test
    void grab_shouldPublishAndReturnProcessing_whenLuaReturns1() {
        when(seckillRepository.findActivityById(10L)).thenReturn(Optional.of(activity));
        when(seckillRepository.findOrderByIdempotencyKey("key-ok")).thenReturn(Optional.empty());
        when(seckillRepository.findOrderByUserAndActivity(1L, 10L)).thenReturn(Optional.empty());
        when(seckillRedisService.grab(10L, 1L)).thenReturn(1L);

        SeckillGrabRequest req = new SeckillGrabRequest();
        req.setIdempotencyKey("key-ok");

        SeckillGrabResponse response = seckillService.grab(user, 10L, req);

        assertEquals(SeckillOrderStatus.PROCESSING.name(), response.getStatus());
        ArgumentCaptor<SeckillOrderMessage> captor = ArgumentCaptor.forClass(SeckillOrderMessage.class);
        verify(seckillMqProducer).publishSeckillOrder(captor.capture());
        assertEquals(1L, captor.getValue().getUserId());
        assertEquals(10L, captor.getValue().getActivityId());
        assertEquals("key-ok", captor.getValue().getIdempotencyKey());
    }

    @Test
    void grab_shouldRollbackRedis_whenMqPublishFails() {
        when(seckillRepository.findActivityById(10L)).thenReturn(Optional.of(activity));
        when(seckillRepository.findOrderByIdempotencyKey("key-mq")).thenReturn(Optional.empty());
        when(seckillRepository.findOrderByUserAndActivity(1L, 10L)).thenReturn(Optional.empty());
        when(seckillRedisService.grab(10L, 1L)).thenReturn(1L);
        doThrow(new RuntimeException("broker down"))
                .when(seckillMqProducer).publishSeckillOrder(any());

        SeckillGrabRequest req = new SeckillGrabRequest();
        req.setIdempotencyKey("key-mq");

        SeckillException ex = assertThrows(SeckillException.class,
                () -> seckillService.grab(user, 10L, req));
        assertTrue(ex.getMessage().contains("繁忙"));
        verify(seckillRedisService).rollbackGrab(10L, 1L);
    }

    @Test
    void getResult_shouldReturnProcessing_whenNoDbRowButRedisHasUser() {
        when(seckillRepository.findActivityById(10L)).thenReturn(Optional.of(activity));
        when(seckillRepository.findOrderByUserAndActivity(1L, 10L)).thenReturn(Optional.empty());
        when(seckillRedisService.hasGrabbedUser(10L, 1L)).thenReturn(true);

        var result = seckillService.getResult(user, 10L);

        assertEquals(SeckillOrderStatus.PROCESSING.name(), result.getStatus());
    }
}
