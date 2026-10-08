package com.zx.bookstore.borrow.service;

import com.zx.auth.repository.AuthUserRepository;
import com.zx.auth.security.AuthPrincipal;
import com.zx.bookstore.borrow.dto.BorrowOrderResponse;
import com.zx.bookstore.borrow.dto.CreateBorrowOrderRequest;
import com.zx.bookstore.borrow.entity.BorrowOrder;
import com.zx.bookstore.borrow.enums.BorrowOrderStatus;
import com.zx.bookstore.borrow.exception.BorrowException;
import com.zx.bookstore.borrow.repository.BorrowOrderRepository;
import com.zx.bookstore.catalog.dto.PageResult;
import com.zx.bookstore.catalog.entity.Book;
import com.zx.bookstore.catalog.entity.Bookshelf;
import com.zx.bookstore.catalog.repository.BookRepository;
import com.zx.bookstore.catalog.repository.BookshelfRepository;
import com.zx.bookstore.catalog.service.BookStockLogService;
import com.zx.bookstore.catalog.support.ShelfLocationSupport;
import com.zx.bookstore.exception.BookstoreException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BorrowService {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 每张借阅单最多续借一次，逾期后续借可恢复线上阅读。 */
    private static final int MAX_RENEW = 1;

    private final BorrowOrderRepository borrowOrderRepository;
    private final BookRepository bookRepository;
    private final BookshelfRepository bookshelfRepository;
    private final AuthUserRepository authUserRepository;
    private final BorrowDueRedisService borrowDueRedisService;
    private final BookStockLogService bookStockLogService;

    @Transactional
    public BorrowOrderResponse apply(AuthPrincipal principal, CreateBorrowOrderRequest req) {
        if (req == null || req.getBookId() == null) {
            throw new IllegalArgumentException("bookId 不能为空");
        }
        if (borrowOrderRepository.hasPendingApplyByUser(principal.userId())) {
            throw BorrowException.pendingApply();
        }
        if (borrowOrderRepository.hasActiveBorrowByUser(principal.userId())) {
            throw BorrowException.hasUnreturned();
        }

        Book book = bookRepository.findByIdForUpdate(req.getBookId())
                .orElseThrow(BookstoreException::bookNotFound);
        if (book.getStatus() == null || book.getStatus() != 1) {
            throw BookstoreException.bookNotFound();
        }

        int stock = book.getBorrowStock() == null ? 0 : book.getBorrowStock();
        long pendingApplies = borrowOrderRepository.countAppliedByBookId(req.getBookId());
        if (stock <= pendingApplies) {
            throw BorrowException.outOfStock();
        }

        BorrowOrder order = new BorrowOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(principal.userId());
        order.setBookId(req.getBookId());
        order.setStatus(BorrowOrderStatus.APPLIED.name());
        borrowOrderRepository.save(order);
        return buildResponse(order);
    }

    @Transactional
    public BorrowOrderResponse cancel(AuthPrincipal principal, Long orderId) {
        BorrowOrder order = loadOwnedOrder(principal, orderId);
        if (BorrowOrderStatus.CANCELLED.name().equals(order.getStatus())) {
            return buildResponse(order);
        }
        BorrowOrderStatus current = BorrowOrderStatus.valueOf(order.getStatus());
        if (!current.canTransitTo(BorrowOrderStatus.CANCELLED)) {
            throw BorrowException.invalidStatus();
        }
        int rows = borrowOrderRepository.updateToCancelled(orderId);
        if (rows == 0) {
            return reloadOrThrow(orderId, BorrowOrderStatus.CANCELLED);
        }
        order.setStatus(BorrowOrderStatus.CANCELLED.name());
        return buildResponse(order);
    }

    /**
     * 借阅中或逾期可续借一次。应还日未到时从原应还日顺延，已逾期则从现在起算，并恢复为借阅中。
     */
    @Transactional
    public BorrowOrderResponse renew(AuthPrincipal principal, Long orderId) {
        BorrowOrder order = loadOwnedOrder(principal, orderId);
        String status = order.getStatus();
        if (!BorrowOrderStatus.BORROWED.name().equals(status)
                && !BorrowOrderStatus.OVERDUE.name().equals(status)) {
            throw BorrowException.invalidStatus();
        }
        int used = order.getRenewCount() == null ? 0 : order.getRenewCount();
        if (used >= MAX_RENEW) {
            throw BorrowException.renewLimit();
        }

        Book book = bookRepository.findByIdForUpdate(order.getBookId())
                .orElseThrow(BookstoreException::bookNotFound);
        int borrowDays = book.getBorrowDays() == null ? 30 : book.getBorrowDays();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = order.getDueAt() != null && order.getDueAt().isAfter(now) ? order.getDueAt() : now;
        LocalDateTime dueAt = base.plusDays(borrowDays);

        int rows = borrowOrderRepository.updateRenew(orderId, dueAt, MAX_RENEW);
        if (rows == 0) {
            BorrowOrder latest = borrowOrderRepository.findById(orderId)
                    .orElseThrow(BorrowException::orderNotFound);
            int latestUsed = latest.getRenewCount() == null ? 0 : latest.getRenewCount();
            if (latestUsed >= MAX_RENEW) {
                throw BorrowException.renewLimit();
            }
            throw BorrowException.invalidStatus();
        }

        order.setStatus(BorrowOrderStatus.BORROWED.name());
        order.setDueAt(dueAt);
        order.setRenewCount(used + 1);
        borrowDueRedisService.scheduleDue(orderId, dueAt);
        return buildResponse(order);
    }

    @Transactional
    public BorrowOrderResponse returnBook(AuthPrincipal principal, Long orderId) {
        BorrowOrder order = loadOwnedOrder(principal, orderId);
        return doReturn(order, principal.userId());
    }

    @Transactional
    public BorrowOrderResponse confirm(Long orderId, Long operatorId) {
        BorrowOrder order = borrowOrderRepository.findById(orderId)
                .orElseThrow(BorrowException::orderNotFound);
        if (BorrowOrderStatus.BORROWED.name().equals(order.getStatus())) {
            return buildResponse(order);
        }
        BorrowOrderStatus current = BorrowOrderStatus.valueOf(order.getStatus());
        if (!current.canTransitTo(BorrowOrderStatus.BORROWED)) {
            throw BorrowException.invalidStatus();
        }

        Book book = bookRepository.findByIdForUpdate(order.getBookId())
                .orElseThrow(BookstoreException::bookNotFound);
        int stock = book.getBorrowStock() == null ? 0 : book.getBorrowStock();
        if (stock <= 0) {
            throw BorrowException.outOfStock();
        }

        LocalDateTime now = LocalDateTime.now();
        int borrowDays = book.getBorrowDays() == null ? 30 : book.getBorrowDays();
        LocalDateTime dueAt = now.plusDays(borrowDays);

        int rows = borrowOrderRepository.updateToBorrowed(orderId, now, dueAt);
        if (rows == 0) {
            return reloadOrThrow(orderId, BorrowOrderStatus.BORROWED);
        }

        book.setBorrowStock(stock - 1);
        bookRepository.saveBorrowStock(book);

        order.setStatus(BorrowOrderStatus.BORROWED.name());
        order.setBorrowAt(now);
        order.setDueAt(dueAt);
        borrowDueRedisService.scheduleDue(orderId, dueAt);
        bookStockLogService.recordBorrowOut(order.getBookId(), 1, orderId, operatorId);
        return buildResponse(order);
    }

    @Transactional
    public BorrowOrderResponse reject(Long orderId) {
        BorrowOrder order = borrowOrderRepository.findById(orderId)
                .orElseThrow(BorrowException::orderNotFound);
        if (BorrowOrderStatus.CANCELLED.name().equals(order.getStatus())) {
            return buildResponse(order);
        }
        BorrowOrderStatus current = BorrowOrderStatus.valueOf(order.getStatus());
        if (!current.canTransitTo(BorrowOrderStatus.CANCELLED)) {
            throw BorrowException.invalidStatus();
        }
        int rows = borrowOrderRepository.updateToCancelled(orderId);
        if (rows == 0) {
            return reloadOrThrow(orderId, BorrowOrderStatus.CANCELLED);
        }
        order.setStatus(BorrowOrderStatus.CANCELLED.name());
        return buildResponse(order);
    }

    @Transactional
    public BorrowOrderResponse adminReturn(Long orderId, Long operatorId) {
        BorrowOrder order = borrowOrderRepository.findById(orderId)
                .orElseThrow(BorrowException::orderNotFound);
        return doReturn(order, operatorId);
    }

    public BorrowOrderResponse getOrder(AuthPrincipal principal, Long orderId) {
        return buildResponse(loadOwnedOrder(principal, orderId));
    }

    public PageResult<BorrowOrderResponse> listMyOrders(AuthPrincipal principal, String status, long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 100);
        List<BorrowOrderResponse> records = toResponses(borrowOrderRepository
                .pageByUser(principal.userId(), status, safePage, safeSize));
        return new PageResult<>(safePage, safeSize,
                borrowOrderRepository.countByUser(principal.userId(), status), records);
    }

    public PageResult<BorrowOrderResponse> listAllOrders(String status, long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 100);
        List<BorrowOrderResponse> records = toResponses(borrowOrderRepository
                .pageAll(status, safePage, safeSize));
        return new PageResult<>(safePage, safeSize, borrowOrderRepository.countAll(status), records);
    }

    private BorrowOrderResponse doReturn(BorrowOrder order, Long operatorId) {
        if (BorrowOrderStatus.RETURNED.name().equals(order.getStatus())) {
            return buildResponse(order);
        }
        BorrowOrderStatus current = BorrowOrderStatus.valueOf(order.getStatus());
        if (!current.canTransitTo(BorrowOrderStatus.RETURNED)) {
            throw BorrowException.invalidStatus();
        }

        LocalDateTime now = LocalDateTime.now();
        int rows = borrowOrderRepository.updateToReturned(order.getId(), now);
        if (rows == 0) {
            return reloadOrThrow(order.getId(), BorrowOrderStatus.RETURNED);
        }

        Book book = bookRepository.findByIdForUpdate(order.getBookId())
                .orElseThrow(BookstoreException::bookNotFound);
        int stock = book.getBorrowStock() == null ? 0 : book.getBorrowStock();
        book.setBorrowStock(stock + 1);
        bookRepository.saveBorrowStock(book);

        order.setStatus(BorrowOrderStatus.RETURNED.name());
        order.setReturnAt(now);
        borrowDueRedisService.removeDue(order.getId());
        bookStockLogService.recordBorrowIn(order.getBookId(), 1, order.getId(), operatorId);
        return buildResponse(order);
    }

    private BorrowOrder loadOwnedOrder(AuthPrincipal principal, Long orderId) {
        BorrowOrder order = borrowOrderRepository.findById(orderId)
                .orElseThrow(BorrowException::orderNotFound);
        if (!order.getUserId().equals(principal.userId())) {
            throw BorrowException.forbidden();
        }
        return order;
    }

    private BorrowOrderResponse reloadOrThrow(Long orderId, BorrowOrderStatus expected) {
        BorrowOrder order = borrowOrderRepository.findById(orderId)
                .orElseThrow(BorrowException::orderNotFound);
        if (expected.name().equals(order.getStatus())) {
            return buildResponse(order);
        }
        throw BorrowException.invalidStatus();
    }

    private List<BorrowOrderResponse> toResponses(List<BorrowOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = orders.stream().map(BorrowOrder::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> bookIds = orders.stream().map(BorrowOrder::getBookId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, com.zx.auth.entity.AuthUser> users = authUserRepository.findByIds(userIds);
        Map<Long, Book> books = bookRepository.findByIds(bookIds);
        Set<Long> shelfIds = books.values().stream()
                .map(Book::getBookshelfId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, com.zx.bookstore.catalog.entity.Bookshelf> shelves = bookshelfRepository.findByIds(shelfIds);
        return orders.stream().map(order -> buildResponse(order, users, books, shelves)).toList();
    }

    private BorrowOrderResponse buildResponse(BorrowOrder order) {
        Map<Long, com.zx.auth.entity.AuthUser> users = order.getUserId() == null
                ? Map.of()
                : authUserRepository.findByIds(Set.of(order.getUserId()));
        Map<Long, Book> books = order.getBookId() == null
                ? Map.of()
                : bookRepository.findByIds(Set.of(order.getBookId()));
        Set<Long> shelfIds = books.values().stream()
                .map(Book::getBookshelfId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return buildResponse(order, users, books, bookshelfRepository.findByIds(shelfIds));
    }

    private BorrowOrderResponse buildResponse(BorrowOrder order,
                                             Map<Long, com.zx.auth.entity.AuthUser> users,
                                             Map<Long, Book> books,
                                             Map<Long, com.zx.bookstore.catalog.entity.Bookshelf> shelves) {
        BorrowOrderResponse resp = new BorrowOrderResponse();
        resp.setId(order.getId());
        resp.setOrderNo(order.getOrderNo());
        resp.setUserId(order.getUserId());
        if (order.getUserId() != null) {
            com.zx.auth.entity.AuthUser user = users.get(order.getUserId());
            if (user != null) {
                resp.setUsername(user.getUsername());
            }
        }
        resp.setBookId(order.getBookId());
        Book book = order.getBookId() == null ? null : books.get(order.getBookId());
        if (book != null) {
            resp.setBookTitle(book.getTitle());
            com.zx.bookstore.catalog.entity.Bookshelf bookshelf = book.getBookshelfId() == null
                    ? null
                    : shelves.get(book.getBookshelfId());
            if (bookshelf != null) {
                resp.setBookshelfFloor(bookshelf.getFloor());
                resp.setBookshelfCode(bookshelf.getCode());
                resp.setShelfLayer(book.getShelfLayer());
                resp.setShelfLocation(ShelfLocationSupport.format(bookshelf, book.getShelfLayer()));
            }
        }
        resp.setStatus(order.getStatus());
        resp.setBorrowAt(formatDateTime(order.getBorrowAt()));
        resp.setDueAt(formatDateTime(order.getDueAt()));
        resp.setReturnAt(formatDateTime(order.getReturnAt()));
        resp.setCreatedAt(formatDateTime(order.getCreatedAt()));
        return resp;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATETIME_FMT);
    }

    private String generateOrderNo() {
        return "BR" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }
}
