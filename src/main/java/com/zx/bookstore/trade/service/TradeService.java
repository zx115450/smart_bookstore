package com.zx.bookstore.trade.service;

import com.zx.auth.repository.AuthUserRepository;
import com.zx.auth.security.AuthPrincipal;
import com.zx.bookstore.cart.service.CartService;
import com.zx.bookstore.catalog.dto.PageResult;
import com.zx.bookstore.catalog.entity.Book;
import com.zx.bookstore.catalog.repository.BookRepository;
import com.zx.bookstore.catalog.service.BookStockLogService;
import com.zx.bookstore.cart.entity.CartItem;
import com.zx.bookstore.coupon.service.CouponService;
import com.zx.bookstore.trade.dto.CreateTradeOrderRequest;
import com.zx.bookstore.trade.dto.TradeOrderResponse;
import com.zx.bookstore.trade.dto.TradeOrderTimeoutMessage;
import com.zx.bookstore.trade.entity.TradeOrder;
import com.zx.bookstore.trade.entity.TradeOrderItem;
import com.zx.bookstore.trade.enums.TradeOrderStatus;
import com.zx.bookstore.trade.exception.TradeException;
import com.zx.bookstore.trade.repository.TradeOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeService {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TradeOrderRepository tradeOrderRepository;
    private final BookRepository bookRepository;
    private final AuthUserRepository authUserRepository;
    private final CouponService couponService;
    private final CartService cartService;
    private final TradeMqProducer tradeMqProducer;
    private final BookStockLogService bookStockLogService;

    @Transactional
    public TradeOrderResponse createOrder(AuthPrincipal principal, CreateTradeOrderRequest req) {
        if (StringUtils.hasText(req.getIdempotencyKey())) {
            var existing = tradeOrderRepository.findByIdempotencyKey(req.getIdempotencyKey());
            if (existing.isPresent()) {
                return buildResponse(existing.get());
            }
        }

        List<OrderLine> lines = resolveOrderLines(principal, req);
        BigDecimal totalAmount = lines.stream()
                .map(line -> line.price().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discountAmount = couponService.calculateDiscount(
                principal.userId(), req.getUserCouponId(), totalAmount);
        BigDecimal payAmount = totalAmount.subtract(discountAmount);
        if (payAmount.compareTo(BigDecimal.ZERO) < 0) {
            payAmount = BigDecimal.ZERO;
        }

        TradeOrder order = new TradeOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(principal.userId());
        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(discountAmount);
        order.setPayAmount(payAmount);
        order.setCouponId(req.getUserCouponId());
        order.setStatus(TradeOrderStatus.PENDING_PAY.name());
        order.setIdempotencyKey(StringUtils.hasText(req.getIdempotencyKey()) ? req.getIdempotencyKey() : null);
        tradeOrderRepository.save(order);

        for (OrderLine line : lines) {
            TradeOrderItem item = new TradeOrderItem();
            item.setOrderId(order.getId());
            item.setBookId(line.bookId());
            item.setBookTitle(line.bookTitle());
            item.setPrice(line.price());
            item.setQuantity(line.quantity());
            tradeOrderRepository.saveItem(item);
        }

        if (req.getCartItemIds() != null && !req.getCartItemIds().isEmpty()) {
            cartService.removeItems(principal.userId(), req.getCartItemIds());
        }

        scheduleOrderTimeoutCancel(order);
        return buildResponse(order);
    }

    /**
     * 延迟消息触发的超时关单：仅当仍为 PENDING_PAY 时取消。
     * 当前流程在支付时才扣库存、核销券，故关单无需回滚库存/券。
     */
    @Transactional
    public void cancelOnTimeout(TradeOrderTimeoutMessage message) {
        if (message == null || message.getOrderId() == null) {
            throw new IllegalArgumentException("超时关单消息缺少 orderId");
        }
        Long orderId = message.getOrderId();
        TradeOrder order = tradeOrderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("timeout cancel skipped: order not found, orderId={}", orderId);
            return;
        }
        if (!TradeOrderStatus.PENDING_PAY.name().equals(order.getStatus())) {
            log.info("timeout cancel skipped: orderId={}, status={}", orderId, order.getStatus());
            return;
        }
        if (!tradeOrderRepository.markCancelledByTimeout(orderId)) {
            log.info("timeout cancel race lost: orderId={}", orderId);
            return;
        }
        log.info("timeout cancel success: orderId={}, orderNo={}", orderId, order.getOrderNo());
    }

    private void scheduleOrderTimeoutCancel(TradeOrder order) {
        TradeOrderTimeoutMessage message = new TradeOrderTimeoutMessage(order.getId(), order.getOrderNo());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tradeMqProducer.publishOrderTimeout(message);
                }
            });
        } else {
            tradeMqProducer.publishOrderTimeout(message);
        }
    }

    @Transactional
    public TradeOrderResponse pay(AuthPrincipal principal, Long orderId) {
        TradeOrder order = tradeOrderRepository.findById(orderId)
                .orElseThrow(TradeException::orderNotFound);
        if (!principal.userId().equals(order.getUserId())) {
            throw TradeException.forbidden();
        }
        if (!TradeOrderStatus.PENDING_PAY.name().equals(order.getStatus())) {
            throw TradeException.invalidStatus();
        }

        List<TradeOrderItem> items = tradeOrderRepository.findItemsByOrderId(order.getId());
        for (TradeOrderItem item : items) {
            bookRepository.findByIdForUpdate(item.getBookId())
                    .orElseThrow(TradeException::bookNotFound);
            if (!bookRepository.deductSaleStock(item.getBookId(), item.getQuantity())) {
                throw TradeException.outOfStock();
            }
            bookStockLogService.recordSaleOut(
                    item.getBookId(), item.getQuantity(), order.getId(), principal.userId());
        }

        authUserRepository.findByIdForUpdate(principal.userId())
                .orElseThrow(TradeException::orderNotFound);
        if (!authUserRepository.deductBalance(principal.userId(), order.getPayAmount())) {
            throw TradeException.insufficientBalance();
        }

        if (!tradeOrderRepository.markPaid(order.getId(), principal.userId())) {
            throw TradeException.invalidStatus();
        }

        couponService.markUsed(principal.userId(), order.getCouponId(), order.getId());

        order.setStatus(TradeOrderStatus.PAID.name());
        order.setPaidAt(LocalDateTime.now());
        return buildResponse(order);
    }

    @Transactional
    public TradeOrderResponse cancel(AuthPrincipal principal, Long orderId) {
        TradeOrder order = tradeOrderRepository.findById(orderId)
                .orElseThrow(TradeException::orderNotFound);
        if (!principal.userId().equals(order.getUserId())) {
            throw TradeException.forbidden();
        }
        if (!TradeOrderStatus.PENDING_PAY.name().equals(order.getStatus())) {
            throw TradeException.invalidStatus();
        }
        if (!tradeOrderRepository.markCancelled(order.getId(), principal.userId())) {
            throw TradeException.invalidStatus();
        }
        order.setStatus(TradeOrderStatus.CANCELLED.name());
        order.setCancelledAt(LocalDateTime.now());
        return buildResponse(order);
    }

    public TradeOrderResponse getOrder(AuthPrincipal principal, Long orderId) {
        TradeOrder order = tradeOrderRepository.findById(orderId)
                .orElseThrow(TradeException::orderNotFound);
        if (!principal.userId().equals(order.getUserId())) {
            throw TradeException.forbidden();
        }
        return buildResponse(order);
    }

    public PageResult<TradeOrderResponse> listMyOrders(AuthPrincipal principal, String status, long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 100);
        List<TradeOrderResponse> records = tradeOrderRepository.pageByUser(principal.userId(), status, safePage, safeSize)
                .stream()
                .map(this::buildResponse)
                .collect(Collectors.toList());
        long total = tradeOrderRepository.countByUser(principal.userId(), status);
        return new PageResult<>(safePage, safeSize, total, records);
    }

    public PageResult<TradeOrderResponse> listAllOrders(String status, long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 100);
        List<TradeOrderResponse> records = tradeOrderRepository.pageAll(status, safePage, safeSize)
                .stream()
                .map(this::buildAdminResponse)
                .collect(Collectors.toList());
        long total = tradeOrderRepository.countAll(status);
        return new PageResult<>(safePage, safeSize, total, records);
    }

    private List<OrderLine> resolveOrderLines(AuthPrincipal principal, CreateTradeOrderRequest req) {
        if (req.getCartItemIds() != null && !req.getCartItemIds().isEmpty()) {
            List<CartItem> cartItems = cartService.loadItemsForCheckout(principal.userId(), req.getCartItemIds());
            List<OrderLine> lines = new ArrayList<>();
            for (CartItem cartItem : cartItems) {
                lines.add(toOrderLine(cartItem.getBookId(), cartItem.getQuantity()));
            }
            return lines;
        }
        if (req.getBookId() != null) {
            int quantity = req.getQuantity() == null || req.getQuantity() <= 0 ? 1 : req.getQuantity();
            return List.of(toOrderLine(req.getBookId(), quantity));
        }
        throw new IllegalArgumentException("cartItemIds 或 bookId 至少提供一个");
    }

    private OrderLine toOrderLine(Long bookId, int quantity) {
        Book book = bookRepository.findEnabledById(bookId)
                .orElseThrow(TradeException::bookNotFound);
        if (book.getSaleStock() == null || book.getSaleStock() < quantity) {
            throw TradeException.outOfStock();
        }
        return new OrderLine(book.getId(), book.getTitle(), book.getPrice(), quantity);
    }

    private TradeOrderResponse buildResponse(TradeOrder order) {
        TradeOrderResponse resp = new TradeOrderResponse();
        resp.setId(order.getId());
        resp.setOrderNo(order.getOrderNo());
        resp.setUserId(order.getUserId());
        resp.setTotalAmount(order.getTotalAmount());
        resp.setDiscountAmount(order.getDiscountAmount());
        resp.setPayAmount(order.getPayAmount());
        resp.setCouponId(order.getCouponId());
        resp.setStatus(order.getStatus());
        resp.setPaidAt(format(order.getPaidAt()));
        resp.setItems(buildItemResponses(order.getId()));
        return resp;
    }

    private TradeOrderResponse buildAdminResponse(TradeOrder order) {
        TradeOrderResponse resp = buildResponse(order);
        authUserRepository.findById(order.getUserId())
                .ifPresent(user -> resp.setUsername(user.getUsername()));
        return resp;
    }

    private List<TradeOrderResponse.TradeOrderItemResponse> buildItemResponses(Long orderId) {
        return tradeOrderRepository.findItemsByOrderId(orderId)
                .stream()
                .map(item -> {
                    TradeOrderResponse.TradeOrderItemResponse ir = new TradeOrderResponse.TradeOrderItemResponse();
                    ir.setBookId(item.getBookId());
                    ir.setBookTitle(item.getBookTitle());
                    ir.setPrice(item.getPrice());
                    ir.setQuantity(item.getQuantity());
                    return ir;
                })
                .toList();
    }

    private String format(LocalDateTime dt) {
        return dt == null ? null : dt.format(DATETIME_FMT);
    }

    private String generateOrderNo() {
        return "TO" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    private record OrderLine(Long bookId, String bookTitle, BigDecimal price, int quantity) {
    }
}
