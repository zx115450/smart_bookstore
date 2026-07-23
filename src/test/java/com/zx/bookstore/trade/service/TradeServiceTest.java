package com.zx.bookstore.trade.service;

import com.zx.auth.repository.AuthUserRepository;
import com.zx.auth.security.AuthPrincipal;
import com.zx.bookstore.cart.service.CartService;
import com.zx.bookstore.catalog.entity.Book;
import com.zx.bookstore.catalog.repository.BookRepository;
import com.zx.bookstore.catalog.service.BookStockLogService;
import com.zx.bookstore.coupon.service.CouponService;
import com.zx.bookstore.trade.dto.CreateTradeOrderRequest;
import com.zx.bookstore.trade.dto.TradeOrderResponse;
import com.zx.bookstore.trade.entity.TradeOrder;
import com.zx.bookstore.trade.dto.TradeOrderTimeoutMessage;
import com.zx.bookstore.trade.enums.TradeOrderStatus;
import com.zx.bookstore.trade.exception.TradeException;
import com.zx.bookstore.trade.repository.TradeOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import com.zx.bookstore.trade.entity.TradeOrderItem;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TradeService 的单元测试：验证订单创建、支付、取消的核心分支。
 * <p>
 * 这里用 Mockito 替换所有外部依赖：仓储、券、购物车、MQ、库存流水。
 * 注意：createOrder 内部通过 TransactionSynchronization 在事务提交后发 MQ；
 * 单测中事务同步管理器默认不激活，所以走 else 分支直接调用 tradeMqProducer.publishOrderTimeout。
 */
@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    @Mock
    private TradeOrderRepository tradeOrderRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private AuthUserRepository authUserRepository;
    @Mock
    private CouponService couponService;
    @Mock
    private CartService cartService;
    @Mock
    private TradeMqProducer tradeMqProducer;
    @Mock
    private BookStockLogService bookStockLogService;

    @InjectMocks
    private TradeService tradeService;

    private final AuthPrincipal user = new AuthPrincipal(1L, "user", 100L, java.util.List.of("USER"));

    /**
     * 直接按 bookId 创建订单：库存充足，无券，应生成 PENDING_PAY 订单并触发超时消息。
     * 验证：
     * 1. 状态为 PENDING_PAY
     * 2. 总价 = 单价 * 数量
     * 3. 保存订单和订单项
     * 4. 发布超时取消消息
     */
    @Test
    void createOrder_shouldCreatePendingPayOrderAndScheduleTimeout() {
        CreateTradeOrderRequest req = new CreateTradeOrderRequest();
        req.setBookId(10L);
        req.setQuantity(2);

        Book book = new Book();
        book.setId(10L);
        book.setTitle("Redis 实战");
        book.setPrice(new BigDecimal("50"));
        book.setSaleStock(10);
        book.setStatus(1);

        lenient().when(tradeOrderRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(bookRepository.findEnabledById(10L)).thenReturn(Optional.of(book));
        when(couponService.calculateDiscount(user.userId(), null, new BigDecimal("100"))).thenReturn(BigDecimal.ZERO);
        when(tradeOrderRepository.save(any(TradeOrder.class))).thenAnswer(invocation -> {
            TradeOrder o = invocation.getArgument(0);
            o.setId(1L);
            return o;
        });

        TradeOrderResponse resp = tradeService.createOrder(user, req);

        assertEquals(TradeOrderStatus.PENDING_PAY.name(), resp.getStatus());
        assertEquals(new BigDecimal("100"), resp.getTotalAmount());
        assertEquals(new BigDecimal("100"), resp.getPayAmount());
        verify(tradeOrderRepository).save(any(TradeOrder.class));
        verify(tradeOrderRepository).saveItem(any());
        verify(tradeMqProducer).publishOrderTimeout(any());
    }

    /**
     * 幂等创建：相同 idempotencyKey 已存在订单时直接返回，不重复创建。
     */
    @Test
    void createOrder_shouldReturnExistingOrderForSameIdempotencyKey() {
        CreateTradeOrderRequest req = new CreateTradeOrderRequest();
        req.setIdempotencyKey("key-123");
        req.setBookId(10L);
        req.setQuantity(1);

        TradeOrder existing = new TradeOrder();
        existing.setId(1L);
        existing.setOrderNo("TO123");
        existing.setUserId(user.userId());
        existing.setStatus(TradeOrderStatus.PENDING_PAY.name());
        existing.setTotalAmount(new BigDecimal("50"));

        when(tradeOrderRepository.findByIdempotencyKey("key-123")).thenReturn(Optional.of(existing));
        when(tradeOrderRepository.findItemsByOrderId(1L)).thenReturn(java.util.List.of());

        TradeOrderResponse resp = tradeService.createOrder(user, req);

        assertEquals("TO123", resp.getOrderNo());
        verify(tradeOrderRepository, never()).save(any(TradeOrder.class));
        verify(tradeMqProducer, never()).publishOrderTimeout(any());
    }

    /**
     * 使用优惠券创建订单：券抵扣后 payAmount = total - discount。
     */
    @Test
    void createOrder_shouldApplyCouponDiscount() {
        CreateTradeOrderRequest req = new CreateTradeOrderRequest();
        req.setBookId(10L);
        req.setQuantity(1);
        req.setUserCouponId(99L);

        Book book = new Book();
        book.setId(10L);
        book.setTitle("Java");
        book.setPrice(new BigDecimal("100"));
        book.setSaleStock(10);
        book.setStatus(1);

        lenient().when(tradeOrderRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(bookRepository.findEnabledById(10L)).thenReturn(Optional.of(book));
        when(couponService.calculateDiscount(user.userId(), 99L, new BigDecimal("100"))).thenReturn(new BigDecimal("20"));
        when(tradeOrderRepository.save(any(TradeOrder.class))).thenAnswer(invocation -> {
            TradeOrder o = invocation.getArgument(0);
            o.setId(2L);
            return o;
        });

        TradeOrderResponse resp = tradeService.createOrder(user, req);

        assertEquals(new BigDecimal("100"), resp.getTotalAmount());
        assertEquals(new BigDecimal("20"), resp.getDiscountAmount());
        assertEquals(new BigDecimal("80"), resp.getPayAmount());
    }

    /**
     * 库存不足时创建订单应抛出 outOfStock 异常。
     */
    @Test
    void createOrder_shouldThrowWhenSaleStockInsufficient() {
        CreateTradeOrderRequest req = new CreateTradeOrderRequest();
        req.setBookId(10L);
        req.setQuantity(5);

        Book book = new Book();
        book.setId(10L);
        book.setPrice(new BigDecimal("10"));
        book.setSaleStock(3); // 想买 5，只有 3
        book.setStatus(1);

        lenient().when(tradeOrderRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(bookRepository.findEnabledById(10L)).thenReturn(Optional.of(book));

        assertThrows(TradeException.class, () -> tradeService.createOrder(user, req));
        verify(tradeOrderRepository, never()).save(any(TradeOrder.class));
    }

    /**
     * 支付成功：PENDING_PAY → PAID，扣减库存、扣余额、核销券。
     */
    @Test
    void pay_shouldTransitionToPaidAndDeductStockAndBalance() {
        TradeOrder order = new TradeOrder();
        order.setId(1L);
        order.setUserId(user.userId());
        order.setStatus(TradeOrderStatus.PENDING_PAY.name());
        order.setPayAmount(new BigDecimal("80"));
        order.setCouponId(99L);

        TradeOrderItem item = new TradeOrderItem();
        item.setOrderId(1L);
        item.setBookId(10L);
        item.setBookTitle("Java");
        item.setPrice(new BigDecimal("100"));
        item.setQuantity(1);

        Book book = new Book();
        book.setId(10L);
        book.setSaleStock(10);

        when(tradeOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(tradeOrderRepository.findItemsByOrderId(1L)).thenReturn(java.util.List.of(item));
        when(bookRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(book));
        when(bookRepository.deductSaleStock(10L, 1)).thenReturn(true);
        when(authUserRepository.findByIdForUpdate(user.userId())).thenReturn(Optional.of(new com.zx.auth.entity.AuthUser()));
        when(authUserRepository.deductBalance(user.userId(), new BigDecimal("80"))).thenReturn(true);
        when(tradeOrderRepository.markPaid(1L, user.userId())).thenReturn(true);

        TradeOrderResponse resp = tradeService.pay(user, 1L);

        assertEquals(TradeOrderStatus.PAID.name(), resp.getStatus());
        verify(couponService).markUsed(user.userId(), 99L, 1L);
        verify(authUserRepository).deductBalance(user.userId(), new BigDecimal("80"));
        verify(bookRepository).deductSaleStock(10L, 1);
        verify(bookStockLogService).recordSaleOut(10L, 1, 1L, user.userId());
    }

    /**
     * 非 PENDING_PAY 状态不能支付。
     */
    @Test
    void pay_shouldRejectNonPendingPayOrder() {
        TradeOrder order = new TradeOrder();
        order.setId(1L);
        order.setUserId(user.userId());
        order.setStatus(TradeOrderStatus.PAID.name());

        when(tradeOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(TradeException.class, () -> tradeService.pay(user, 1L));
        verify(bookRepository, never()).findByIdForUpdate(anyLong());
    }

    /**
     * 取消订单：PENDING_PAY → CANCELLED。
     */
    @Test
    void cancel_shouldTransitionPendingPayToCancelled() {
        TradeOrder order = new TradeOrder();
        order.setId(1L);
        order.setUserId(user.userId());
        order.setStatus(TradeOrderStatus.PENDING_PAY.name());

        when(tradeOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(tradeOrderRepository.markCancelled(1L, user.userId())).thenReturn(true);
        when(tradeOrderRepository.findItemsByOrderId(1L)).thenReturn(java.util.List.of());

        TradeOrderResponse resp = tradeService.cancel(user, 1L);

        assertEquals(TradeOrderStatus.CANCELLED.name(), resp.getStatus());
        verify(tradeOrderRepository).markCancelled(1L, user.userId());
    }

    /**
     * 超时取消：只有 PENDING_PAY 的订单才会被标记取消。
     */
    @Test
    void cancelOnTimeout_shouldSkipNonPendingPayOrder() {
        TradeOrder order = new TradeOrder();
        order.setId(1L);
        order.setStatus(TradeOrderStatus.PAID.name());

        TradeOrderTimeoutMessage msg = new TradeOrderTimeoutMessage(1L, "TO123");
        when(tradeOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        tradeService.cancelOnTimeout(msg);

        verify(tradeOrderRepository, never()).markCancelledByTimeout(anyLong());
    }
}
