package com.zx.bookstore.trade.service;

import com.zx.auth.repository.AuthUserRepository;
import com.zx.auth.security.AuthPrincipal;
import com.zx.bookstore.catalog.dto.PageResult;
import com.zx.bookstore.catalog.entity.Book;
import com.zx.bookstore.catalog.repository.BookRepository;
import com.zx.bookstore.coupon.service.CouponService;
import com.zx.bookstore.trade.dto.CreateTradeOrderRequest;
import com.zx.bookstore.trade.dto.TradeOrderResponse;
import com.zx.bookstore.trade.entity.TradeOrder;
import com.zx.bookstore.trade.entity.TradeOrderItem;
import com.zx.bookstore.trade.enums.TradeOrderStatus;
import com.zx.bookstore.trade.exception.TradeException;
import com.zx.bookstore.trade.repository.TradeOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TradeService {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TradeOrderRepository tradeOrderRepository;
    private final BookRepository bookRepository;
    private final AuthUserRepository authUserRepository;
    private final CouponService couponService;

    @Transactional
    public TradeOrderResponse createOrder(AuthPrincipal principal, CreateTradeOrderRequest req) {
        if (req.getBookId() == null) {
            throw TradeException.bookNotFound();
        }
        int quantity = req.getQuantity() == null || req.getQuantity() <= 0 ? 1 : req.getQuantity();

        if (StringUtils.hasText(req.getIdempotencyKey())) {
            var existing = tradeOrderRepository.findByIdempotencyKey(req.getIdempotencyKey());
            if (existing.isPresent()) {
                return buildResponse(existing.get());
            }
        }

        Book book = bookRepository.findEnabledById(req.getBookId())
                .orElseThrow(TradeException::bookNotFound);
        if (book.getSaleStock() == null || book.getSaleStock() < quantity) {
            throw TradeException.outOfStock();
        }

        BigDecimal totalAmount = book.getPrice().multiply(BigDecimal.valueOf(quantity));
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

        TradeOrderItem item = new TradeOrderItem();
        item.setOrderId(order.getId());
        item.setBookId(book.getId());
        item.setBookTitle(book.getTitle());
        item.setPrice(book.getPrice());
        item.setQuantity(quantity);
        tradeOrderRepository.saveItem(item);

        return buildResponse(order);
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
        List<TradeOrderResponse> records = tradeOrderRepository.pageByUser(principal.userId(), status, page, size)
                .stream()
                .map(this::buildResponse)
                .collect(Collectors.toList());
        long total = tradeOrderRepository.countByUser(principal.userId(), status);
        return new PageResult<>(Math.max(1, page), Math.min(Math.max(1, size), 100), total, records);
    }

    private TradeOrderResponse buildResponse(TradeOrder order) {
        TradeOrderResponse resp = new TradeOrderResponse();
        resp.setId(order.getId());
        resp.setOrderNo(order.getOrderNo());
        resp.setTotalAmount(order.getTotalAmount());
        resp.setDiscountAmount(order.getDiscountAmount());
        resp.setPayAmount(order.getPayAmount());
        resp.setCouponId(order.getCouponId());
        resp.setStatus(order.getStatus());
        resp.setPaidAt(format(order.getPaidAt()));
        List<TradeOrderResponse.TradeOrderItemResponse> items = tradeOrderRepository.findItemsByOrderId(order.getId())
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
        resp.setItems(items);
        return resp;
    }

    private String format(LocalDateTime dt) {
        return dt == null ? null : dt.format(DATETIME_FMT);
    }

    private String generateOrderNo() {
        return "TO" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }
}
