package com.zx.reservation.service;

import com.zx.auth.repository.AuthUserRepository;
import com.zx.auth.security.AuthPrincipal;
import com.zx.auth.security.RoleConstants;
import com.zx.reservation.dto.*;
import com.zx.reservation.entity.ReservationOrder;
import com.zx.reservation.entity.ReservationResource;
import com.zx.reservation.entity.ReservationSeat;
import com.zx.reservation.entity.ReservationTimeSlot;
import com.zx.reservation.enums.ReservationOrderStatus;
import com.zx.reservation.exception.ReservationException;
import com.zx.reservation.repository.ReservationOrderRepository;
import com.zx.reservation.repository.ReservationResourceRepository;
import com.zx.reservation.repository.ReservationSeatRepository;
import com.zx.reservation.repository.ReservationTimeSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final int DAILY_ORDER_LIMIT = 2;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ReservationResourceRepository resourceRepository;
    private final ReservationTimeSlotRepository slotRepository;
    private final ReservationSeatRepository seatRepository;
    private final ReservationOrderRepository orderRepository;
    private final AuthUserRepository userRepository;

    public PageResult<ResourceResponse> listResources(long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 100);
        List<ResourceResponse> records = resourceRepository.pageEnabled(safePage, safeSize).stream()
                .map(this::toResourceResponse)
                .collect(Collectors.toList());
        return new PageResult<>(safePage, safeSize, resourceRepository.countEnabled(), records);
    }

    public List<SeatResponse> listSeats(Long resourceId) {
        if (!resourceRepository.findEnabledById(resourceId).isPresent()) {
            throw ReservationException.resourceNotFound();
        }
        return seatRepository.listEnabledByResource(resourceId).stream()
                .map(seat -> toSeatResponse(seat, null))
                .collect(Collectors.toList());
    }

    public List<TimeSlotResponse> listSlots(Long resourceId, LocalDate date) {
        if (!resourceRepository.findEnabledById(resourceId).isPresent()) {
            throw ReservationException.resourceNotFound();
        }
        long totalSeats = seatRepository.countEnabledByResource(resourceId);
        return slotRepository.listByResourceAndDate(resourceId, date).stream()
                .map(slot -> toSlotResponse(slot, totalSeats))
                .collect(Collectors.toList());
    }

    public List<SeatResponse> listSlotSeats(Long slotId) {
        ReservationTimeSlot slot = slotRepository.findById(slotId)
                .orElseThrow(ReservationException::slotNotFound);
        if (!resourceRepository.findEnabledById(slot.getResourceId()).isPresent()) {
            throw ReservationException.resourceNotFound();
        }
        Set<Long> bookedSeatIds = new HashSet<>(orderRepository.findBookedSeatIdsBySlot(slotId));
        return seatRepository.listEnabledByResource(slot.getResourceId()).stream()
                .map(seat -> toSeatResponse(seat, !bookedSeatIds.contains(seat.getId())))
                .collect(Collectors.toList());
    }

    @Transactional
    public OrderResponse createOrder(AuthPrincipal principal, CreateOrderRequest req) {

        if (req.getTimeSlotId() == null || req.getSeatId() == null) {
            throw ReservationException.slotNotFound();
        }
        //校验合法性 , 是否存在已有订单 , 是否存在 时段 , 座位 , 自习室
        if (StringUtils.hasText(req.getIdempotencyKey())) {
            var existing = orderRepository.findByIdempotencyKey(req.getIdempotencyKey());
            if (existing.isPresent()) {
                return buildOrderResponse(existing.get());
            }
        }
        ReservationTimeSlot slot = slotRepository.findById(req.getTimeSlotId())
                .orElseThrow(ReservationException::slotNotFound);
        ReservationResource resource = resourceRepository.findEnabledById(slot.getResourceId())
                .orElseThrow(ReservationException::resourceNotFound);

        //开启行级锁
        ReservationSeat seat = seatRepository.findByIdForUpdate(req.getSeatId())
                .orElseThrow(ReservationException::seatNotFound);
        if (!seat.getResourceId().equals(resource.getId()) || seat.getStatus() == null || seat.getStatus() != 1) {
            throw ReservationException.seatNotFound();
        }
        validateSlotBookable(slot);
        if (orderRepository.existsBookedByUserAndSlot(principal.userId(), slot.getId())) {
            throw ReservationException.duplicateBooking();
        }
        if (orderRepository.countActiveOrdersByUserAndDate(principal.userId(), slot.getSlotDate()) >= DAILY_ORDER_LIMIT) {
            throw ReservationException.dailyLimitExceeded();
        }
        if (orderRepository.existsBookedBySlotAndSeat(slot.getId(), seat.getId())) {
            throw ReservationException.seatAlreadyBooked();
        }


        ReservationOrder order = new ReservationOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(principal.userId());
        order.setResourceId(resource.getId());
        order.setTimeSlotId(slot.getId());
        order.setSeatId(seat.getId());
        order.setStatus(ReservationOrderStatus.BOOKED.name());
        order.setIdempotencyKey(StringUtils.hasText(req.getIdempotencyKey()) ? req.getIdempotencyKey() : null);
        order.setBookedAt(LocalDateTime.now());
        orderRepository.save(order);
        return buildOrderResponse(order);
    }

    public PageResult<OrderResponse> listMyOrders(AuthPrincipal principal, String status, long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 100);
        List<OrderResponse> records = orderRepository.pageByUser(principal.userId(), status, safePage, safeSize).stream()
                .map(this::buildOrderResponse)
                .collect(Collectors.toList());
        return new PageResult<>(safePage, safeSize, orderRepository.countByUser(principal.userId(), status), records);
    }

    public OrderResponse getOrder(AuthPrincipal principal, Long orderId) {
        ReservationOrder order = orderRepository.findById(orderId)
                .orElseThrow(ReservationException::orderNotFound);
        assertCanView(principal, order);
        return buildOrderResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(AuthPrincipal principal, Long orderId, CancelOrderRequest req) {
        ReservationOrder order = orderRepository.findById(orderId)
                .orElseThrow(ReservationException::orderNotFound);
        assertCanManage(principal, order);

        ReservationOrderStatus current = ReservationOrderStatus.valueOf(order.getStatus());
        if (!current.canTransitTo(ReservationOrderStatus.CANCELLED)) {
            throw ReservationException.invalidStatus();
        }

        order.setStatus(ReservationOrderStatus.CANCELLED.name());
        order.setCancelReason(req == null ? null : req.getReason());
        order.setCancelledAt(LocalDateTime.now());
        orderRepository.save(order);
        return buildOrderResponse(order);
    }

    @Transactional
    public ResourceResponse createResource(CreateResourceRequest req) {
        validateResourceRequest(req.getName(), req.getLocation(), req.getTotalCapacity());
        ReservationResource resource = new ReservationResource();
        resource.setName(req.getName().trim());
        resource.setLocation(req.getLocation().trim());
        resource.setTotalCapacity(req.getTotalCapacity());
        resource.setStatus(1);
        resource = resourceRepository.save(resource);
        generateSeats(resource.getId(), defaultSeatRequest(resource.getTotalCapacity()));
        return toResourceResponse(resource);
    }

    @Transactional
    public ResourceResponse updateResource(Long id, UpdateResourceRequest req) {
        ReservationResource resource = resourceRepository.findById(id)
                .orElseThrow(ReservationException::resourceNotFound);
        if (StringUtils.hasText(req.getName())) {
            resource.setName(req.getName().trim());
        }
        if (StringUtils.hasText(req.getLocation())) {
            resource.setLocation(req.getLocation().trim());
        }
        if (req.getTotalCapacity() != null) {
            if (req.getTotalCapacity() <= 0) {
                throw new IllegalArgumentException("totalCapacity must be positive");
            }
            resource.setTotalCapacity(req.getTotalCapacity());
        }
        if (req.getStatus() != null) {
            resource.setStatus(req.getStatus());
        }
        return toResourceResponse(resourceRepository.save(resource));
    }

    @Transactional
    public void disableResource(Long id) {
        ReservationResource resource = resourceRepository.findById(id)
                .orElseThrow(ReservationException::resourceNotFound);
        resource.setStatus(0);
        resourceRepository.save(resource);
    }

    @Transactional
    public int generateSlots(Long resourceId, GenerateSlotsRequest req) {
        ReservationResource resource = resourceRepository.findEnabledById(resourceId)
                .orElseThrow(ReservationException::resourceNotFound);

        if (seatRepository.countEnabledByResource(resourceId) == 0) {
            throw new IllegalArgumentException("请先生成座位");
        }

        LocalDate start = LocalDate.parse(req.getDate(), DATE_FMT);
        LocalDate end = StringUtils.hasText(req.getEndDate())
                ? LocalDate.parse(req.getEndDate(), DATE_FMT)
                : start;

        int startHour = req.getStartHour() == null ? 8 : req.getStartHour();
        int endHour = req.getEndHour() == null ? 22 : req.getEndHour();
        int duration = req.getDurationHours() == null ? 2 : req.getDurationHours();

        if (end.isBefore(start)) {
            throw new IllegalArgumentException("endDate must not be before date");
        }
        if (duration <= 0 || startHour >= endHour) {
            throw new IllegalArgumentException("invalid slot generation parameters");
        }

        int created = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            for (int hour = startHour; hour + duration <= endHour; hour += duration) {
                LocalTime slotStart = LocalTime.of(hour, 0);
                LocalTime slotEnd = LocalTime.of(hour + duration, 0);
                if (slotRepository.existsByResourceDateStart(resourceId, date, slotStart)) {
                    continue;
                }
                ReservationTimeSlot slot = new ReservationTimeSlot();
                slot.setResourceId(resourceId);
                slot.setSlotDate(date);
                slot.setStartTime(slotStart);
                slot.setEndTime(slotEnd);
                int totalSeats = (int) seatRepository.countEnabledByResource(resourceId);
                slot.setCapacity(totalSeats);
                slot.setBookedCount(0);
                slotRepository.save(slot);
                created++;
            }
        }
        return created;
    }

    @Transactional
    public int generateSeats(Long resourceId, GenerateSeatsRequest req) {
        ReservationResource resource = resourceRepository.findById(resourceId)
                .orElseThrow(ReservationException::resourceNotFound);

        String prefix = StringUtils.hasText(req.getPrefix()) ? req.getPrefix().trim() : "A";
        int created = 0;

        if (req.getRows() != null && req.getCols() != null) {
            if (req.getRows() <= 0 || req.getCols() <= 0) {
                throw new IllegalArgumentException("rows and cols must be positive");
            }
            for (int r = 1; r <= req.getRows(); r++) {
                for (int c = 1; c <= req.getCols(); c++) {
                    String seatNo = prefix + String.format("%02d", r) + "-" + String.format("%02d", c);
                    if (seatRepository.existsByResourceAndSeatNo(resourceId, seatNo)) {
                        continue;
                    }
                    ReservationSeat seat = new ReservationSeat();
                    seat.setResourceId(resourceId);
                    seat.setSeatNo(seatNo);
                    seat.setRowNum(r);
                    seat.setColNum(c);
                    seat.setStatus(1);
                    seatRepository.save(seat);
                    created++;
                }
            }
        } else {
            int count = req.getCount() != null ? req.getCount() : resource.getTotalCapacity();
            if (count <= 0) {
                throw new IllegalArgumentException("count must be positive");
            }
            int cols = Math.min(10, count);
            int rows = (count + cols - 1) / cols;
            int index = 1;
            for (int r = 1; r <= rows && index <= count; r++) {
                for (int c = 1; c <= cols && index <= count; c++, index++) {
                    String seatNo = prefix + String.format("%02d", index);
                    if (seatRepository.existsByResourceAndSeatNo(resourceId, seatNo)) {
                        continue;
                    }
                    ReservationSeat seat = new ReservationSeat();
                    seat.setResourceId(resourceId);
                    seat.setSeatNo(seatNo);
                    seat.setRowNum(r);
                    seat.setColNum(c);
                    seat.setStatus(1);
                    seatRepository.save(seat);
                    created++;
                }
            }
        }

        if (created > 0) {
            resource.setTotalCapacity((int) seatRepository.countEnabledByResource(resourceId));
            resourceRepository.save(resource);
        }
        return created;
    }

    public PageResult<OrderResponse> listAllOrders(String status, long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 100);
        List<OrderResponse> records = orderRepository.pageAll(status, safePage, safeSize).stream()
                .map(this::buildOrderResponse)
                .collect(Collectors.toList());
        return new PageResult<>(safePage, safeSize, orderRepository.countAll(status), records);
    }

    @Transactional
    public OrderResponse completeOrder(Long orderId) {
        ReservationOrder order = orderRepository.findById(orderId)
                .orElseThrow(ReservationException::orderNotFound);

        ReservationOrderStatus current = ReservationOrderStatus.valueOf(order.getStatus());
        if (!current.canTransitTo(ReservationOrderStatus.COMPLETED)) {
            throw ReservationException.invalidStatus();
        }

        order.setStatus(ReservationOrderStatus.COMPLETED.name());
        order.setCompletedAt(LocalDateTime.now());
        orderRepository.save(order);
        return buildOrderResponse(order);
    }

    private GenerateSeatsRequest defaultSeatRequest(int count) {
        GenerateSeatsRequest req = new GenerateSeatsRequest();
        req.setCount(count);
        req.setPrefix("A");
        return req;
    }

    private void validateSlotBookable(ReservationTimeSlot slot) {
        LocalDateTime slotStart = LocalDateTime.of(slot.getSlotDate(), slot.getStartTime());
        if (!slotStart.isAfter(LocalDateTime.now())) {
            throw ReservationException.slotExpired();
        }
    }

    private void assertCanView(AuthPrincipal principal, ReservationOrder order) {
        if (isAdmin(principal) || principal.userId().equals(order.getUserId())) {
            return;
        }
        throw ReservationException.forbidden();
    }

    private void assertCanManage(AuthPrincipal principal, ReservationOrder order) {
        if (isAdmin(principal) || principal.userId().equals(order.getUserId())) {
            return;
        }
        throw ReservationException.forbidden();
    }

    private boolean isAdmin(AuthPrincipal principal) {
        return principal.roles().contains(RoleConstants.ADMIN);
    }

    private String generateOrderNo() {
        return "RS" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    private void validateResourceRequest(String name, String location, Integer capacity) {
        if (!StringUtils.hasText(name) || !StringUtils.hasText(location) || capacity == null || capacity <= 0) {
            throw new IllegalArgumentException("name, location and positive totalCapacity required");
        }
    }

    private ResourceResponse toResourceResponse(ReservationResource resource) {
        ResourceResponse resp = new ResourceResponse();
        resp.setId(resource.getId());
        resp.setName(resource.getName());
        resp.setLocation(resource.getLocation());
        resp.setTotalCapacity(resource.getTotalCapacity());
        resp.setStatus(resource.getStatus());
        return resp;
    }

    private SeatResponse toSeatResponse(ReservationSeat seat, Boolean available) {
        SeatResponse resp = new SeatResponse();
        resp.setId(seat.getId());
        resp.setResourceId(seat.getResourceId());
        resp.setSeatNo(seat.getSeatNo());
        resp.setRowNum(seat.getRowNum());
        resp.setColNum(seat.getColNum());
        resp.setStatus(seat.getStatus());
        resp.setAvailable(available);
        return resp;
    }

    private TimeSlotResponse toSlotResponse(ReservationTimeSlot slot, long totalSeats) {
        long booked = orderRepository.countBookedSeatsBySlot(slot.getId());
        long remaining = Math.max(0, totalSeats - booked);
        TimeSlotResponse resp = new TimeSlotResponse();
        resp.setId(slot.getId());
        resp.setResourceId(slot.getResourceId());
        resp.setSlotDate(slot.getSlotDate().format(DATE_FMT));
        resp.setStartTime(slot.getStartTime().format(TIME_FMT));
        resp.setEndTime(slot.getEndTime().format(TIME_FMT));
        resp.setTotalSeats((int) totalSeats);
        resp.setBookedSeats((int) booked);
        resp.setRemainingSeats((int) remaining);
        resp.setCapacity((int) totalSeats);
        resp.setBookedCount((int) booked);
        resp.setRemainingCapacity((int) remaining);
        return resp;
    }

    private OrderResponse buildOrderResponse(ReservationOrder order) {
        OrderResponse resp = new OrderResponse();
        resp.setId(order.getId());
        resp.setOrderNo(order.getOrderNo());
        resp.setUserId(order.getUserId());
        userRepository.findById(order.getUserId()).ifPresent(u -> resp.setUsername(u.getUsername()));
        resp.setResourceId(order.getResourceId());
        resourceRepository.findById(order.getResourceId()).ifPresent(r -> resp.setResourceName(r.getName()));
        resp.setTimeSlotId(order.getTimeSlotId());
        resp.setSeatId(order.getSeatId());
        seatRepository.findById(order.getSeatId()).ifPresent(seat -> resp.setSeatNo(seat.getSeatNo()));
        slotRepository.findById(order.getTimeSlotId()).ifPresent(slot -> {
            resp.setSlotDate(slot.getSlotDate().format(DATE_FMT));
            resp.setStartTime(slot.getStartTime().format(TIME_FMT));
            resp.setEndTime(slot.getEndTime().format(TIME_FMT));
        });
        resp.setStatus(order.getStatus());
        resp.setCancelReason(order.getCancelReason());
        resp.setBookedAt(formatDateTime(order.getBookedAt()));
        resp.setCancelledAt(formatDateTime(order.getCancelledAt()));
        resp.setCompletedAt(formatDateTime(order.getCompletedAt()));
        resp.setCheckinAt(formatDateTime(order.getCheckinAt()));
        return resp;
    }

    private String formatDateTime(LocalDateTime dt) {
        return dt == null ? null : dt.format(DATETIME_FMT);
    }
}
