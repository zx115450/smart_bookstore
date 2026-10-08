package com.zx.bookstore.borrow.service;

import com.zx.auth.repository.AuthUserRepository;
import com.zx.auth.security.AuthPrincipal;
import com.zx.bookstore.borrow.dto.BorrowOrderResponse;
import com.zx.bookstore.borrow.dto.CreateBorrowOrderRequest;
import com.zx.bookstore.borrow.entity.BorrowOrder;
import com.zx.bookstore.borrow.enums.BorrowOrderStatus;
import com.zx.bookstore.borrow.exception.BorrowException;
import com.zx.bookstore.borrow.repository.BorrowOrderRepository;
import com.zx.bookstore.catalog.entity.Book;
import com.zx.bookstore.catalog.repository.BookRepository;
import com.zx.bookstore.catalog.repository.BookshelfRepository;
import com.zx.bookstore.catalog.service.BookStockLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BorrowService 的单元测试：验证借阅状态流转与库存校验。
 * <p>
 * 使用 {@link MockitoExtension} 让 Mockito 自动初始化 {@code @Mock} 字段；
 * 用 {@link @InjectMocks} 把 Mock 注入被测 Service。
 * 这里只验证业务决策与仓库协作，不启动 Spring 容器，也不连真实数据库。
 */
@ExtendWith(MockitoExtension.class)
class BorrowServiceTest {

    // ===== 依赖替身：用 @Mock 替代真实 Repository / Service =====
    @Mock
    private BorrowOrderRepository borrowOrderRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookshelfRepository bookshelfRepository;
    @Mock
    private AuthUserRepository authUserRepository;
    @Mock
    private BorrowDueRedisService borrowDueRedisService;
    @Mock
    private BookStockLogService bookStockLogService;

    // 被测对象：Mockito 会按类型把上面的替身注入进来
    @InjectMocks
    private BorrowService borrowService;

    private final AuthPrincipal user = new AuthPrincipal(1L, "user", 100L, java.util.List.of("USER"));

    /**
     * 正常申请：用户无在借、书架上架、可借库存充足 → 生成 APPLIED 订单。
     * 验证：
     * 1. 返回状态为 APPLIED
     * 2. 仓库保存了一次订单
     * 3. 对可借占用数进行了校验（无需精确断言，但路径需走到）
     */
    @Test
    void apply_shouldCreateAppliedOrderWhenStockAvailable() {
        // given：准备请求与可用书籍
        CreateBorrowOrderRequest req = new CreateBorrowOrderRequest();
        req.setBookId(10L);

        Book book = new Book();
        book.setId(10L);
        book.setTitle("Spring");
        book.setStatus(1);
        book.setBorrowStock(5);

        when(borrowOrderRepository.hasActiveBorrowByUser(user.userId())).thenReturn(false);
        when(bookRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(book));
        when(borrowOrderRepository.countAppliedByBookId(10L)).thenReturn(2L); // 5 - 2 > 0
        when(borrowOrderRepository.save(any(BorrowOrder.class))).thenAnswer(invocation -> {
            BorrowOrder o = invocation.getArgument(0);
            o.setId(1L);
            return o;
        });

        // when
        BorrowOrderResponse resp = borrowService.apply(user, req);

        // then
        assertEquals(BorrowOrderStatus.APPLIED.name(), resp.getStatus());
        verify(borrowOrderRepository, times(1)).save(any(BorrowOrder.class));
        verify(borrowOrderRepository).countAppliedByBookId(10L);
    }

    /**
     * 用户在已经有 BORROWED / OVERDUE 订单时再次申请，应抛 hasUnreturned 异常。
     * 验证：查询后不再查书。
     */
    @Test
    void apply_shouldRejectWhenUserHasActiveBorrow() {
        CreateBorrowOrderRequest req = new CreateBorrowOrderRequest();
        req.setBookId(10L);

        when(borrowOrderRepository.hasActiveBorrowByUser(user.userId())).thenReturn(true);

        assertThrows(BorrowException.class, () -> borrowService.apply(user, req));
        verify(bookRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void apply_shouldRejectWhenUserHasPendingApply() {
        CreateBorrowOrderRequest req = new CreateBorrowOrderRequest();
        req.setBookId(10L);
        when(borrowOrderRepository.hasPendingApplyByUser(user.userId())).thenReturn(true);

        assertThrows(BorrowException.class, () -> borrowService.apply(user, req));
        verify(borrowOrderRepository, never()).hasActiveBorrowByUser(any());
        verify(bookRepository, never()).findByIdForUpdate(anyLong());
    }

    /**
     * 上架书已无剩余可借库存时申请应失败。
     * 验证：已走到库存校验但未保存订单。
     */
    @Test
    void apply_shouldRejectWhenNoAvailableStock() {
        CreateBorrowOrderRequest req = new CreateBorrowOrderRequest();
        req.setBookId(10L);

        Book book = new Book();
        book.setId(10L);
        book.setStatus(1);
        book.setBorrowStock(2);

        when(borrowOrderRepository.hasActiveBorrowByUser(user.userId())).thenReturn(false);
        when(bookRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(book));
        when(borrowOrderRepository.countAppliedByBookId(10L)).thenReturn(2L); // 2 <= 2

        assertThrows(BorrowException.class, () -> borrowService.apply(user, req));
        verify(borrowOrderRepository, never()).save(any(BorrowOrder.class));
    }

    /**
     * 管理员确认取书：APPLIED → BORROWED，扣减库存，设置借阅与到期时间。
     * 验证：
     * 1. 返回 BORROWED
     * 2. 书籍借阅库存 -1
     * 3. 更新订单、定时逾期、库存流水各被调用一次
     */
    @Test
    void confirm_shouldTransitionAppliedToBorrowedAndDeductStock() {
        BorrowOrder order = new BorrowOrder();
        order.setId(1L);
        order.setUserId(user.userId());
        order.setBookId(10L);
        order.setStatus(BorrowOrderStatus.APPLIED.name());

        Book book = new Book();
        book.setId(10L);
        book.setBorrowStock(5);
        book.setBorrowDays(30);

        when(borrowOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(bookRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(book));
        when(borrowOrderRepository.updateToBorrowed(eq(1L), any(), any())).thenReturn(1);
        doAnswer(invocation -> {
            Book b = invocation.getArgument(0);
            return b;
        }).when(bookRepository).saveBorrowStock(book);

        BorrowOrderResponse resp = borrowService.confirm(1L, 99L);

        assertEquals(BorrowOrderStatus.BORROWED.name(), resp.getStatus());
        assertEquals(4, book.getBorrowStock()); // 5 - 1
        verify(borrowOrderRepository).updateToBorrowed(eq(1L), any(), any());
        verify(borrowDueRedisService).scheduleDue(eq(1L), any());
        verify(bookStockLogService).recordBorrowOut(10L, 1, 1L, 99L);
    }

    /**
     * 重复确认：订单已经是 BORROWED 时直接返回，不扣库存。
     */
    @Test
    void confirm_shouldReturnDirectlyWhenAlreadyBorrowed() {
        BorrowOrder order = new BorrowOrder();
        order.setId(1L);
        order.setStatus(BorrowOrderStatus.BORROWED.name());

        when(borrowOrderRepository.findById(1L)).thenReturn(Optional.of(order));

        BorrowOrderResponse resp = borrowService.confirm(1L, 99L);

        assertEquals(BorrowOrderStatus.BORROWED.name(), resp.getStatus());
        verify(bookRepository, never()).findByIdForUpdate(anyLong());
        verify(borrowOrderRepository, never()).updateToBorrowed(anyLong(), any(), any());
    }

    /**
     * 用户取消：APPLIED → CANCELLED，调用 updateToCancelled。
     */
    @Test
    void cancel_shouldTransitionAppliedToCancelled() {
        BorrowOrder order = new BorrowOrder();
        order.setId(1L);
        order.setUserId(user.userId());
        order.setStatus(BorrowOrderStatus.APPLIED.name());

        when(borrowOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(borrowOrderRepository.updateToCancelled(1L)).thenReturn(1);

        BorrowOrderResponse resp = borrowService.cancel(user, 1L);

        assertEquals(BorrowOrderStatus.CANCELLED.name(), resp.getStatus());
        verify(borrowOrderRepository).updateToCancelled(1L);
    }

    @Test
    void apply_shouldUseRemainingStockAfterConfirmedBorrow() {
        CreateBorrowOrderRequest req = new CreateBorrowOrderRequest();
        req.setBookId(10L);
        Book book = new Book();
        book.setId(10L);
        book.setStatus(1);
        book.setBorrowStock(1);
        when(borrowOrderRepository.hasActiveBorrowByUser(user.userId())).thenReturn(false);
        when(bookRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(book));
        when(borrowOrderRepository.countAppliedByBookId(10L)).thenReturn(0L);
        when(borrowOrderRepository.save(any(BorrowOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BorrowOrderResponse resp = borrowService.apply(user, req);

        assertEquals(BorrowOrderStatus.APPLIED.name(), resp.getStatus());
        verify(borrowOrderRepository, never()).countOccupiedByBookId(any());
    }

    @Test
    void renew_overdue_shouldRestoreBorrowedAndExtendDue() {
        BorrowOrder order = new BorrowOrder();
        order.setId(3L);
        order.setUserId(user.userId());
        order.setBookId(10L);
        order.setStatus(BorrowOrderStatus.OVERDUE.name());
        order.setRenewCount(0);
        order.setDueAt(java.time.LocalDateTime.now().minusDays(1));
        Book book = new Book();
        book.setId(10L);
        book.setBorrowDays(14);
        when(borrowOrderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(bookRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(book));
        when(borrowOrderRepository.updateRenew(eq(3L), any(), eq(1))).thenReturn(1);

        BorrowOrderResponse resp = borrowService.renew(user, 3L);

        assertEquals(BorrowOrderStatus.BORROWED.name(), resp.getStatus());
        verify(borrowDueRedisService).scheduleDue(eq(3L), any());
        verify(bookRepository, never()).saveBorrowStock(any());
    }

    @Test
    void renew_shouldRejectWhenAlreadyRenewed() {
        BorrowOrder order = new BorrowOrder();
        order.setId(3L);
        order.setUserId(user.userId());
        order.setBookId(10L);
        order.setStatus(BorrowOrderStatus.BORROWED.name());
        order.setRenewCount(1);
        when(borrowOrderRepository.findById(3L)).thenReturn(Optional.of(order));

        assertThrows(BorrowException.class, () -> borrowService.renew(user, 3L));
        verify(borrowOrderRepository, never()).updateRenew(any(), any(), anyInt());
    }
}
