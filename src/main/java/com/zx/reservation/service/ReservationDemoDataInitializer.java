package com.zx.reservation.service;

import com.zx.reservation.dto.GenerateSeatsRequest;
import com.zx.reservation.dto.GenerateSlotsRequest;
import com.zx.reservation.repository.ReservationResourceRepository;
import com.zx.reservation.repository.ReservationSeatRepository;
import com.zx.reservation.repository.ReservationTimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 首次启动时为示例自习室生成座位与未来 7 天时段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationDemoDataInitializer implements ApplicationRunner {

    private final ReservationResourceRepository resourceRepository;
    private final ReservationSeatRepository seatRepository;
    private final ReservationTimeSlotRepository slotRepository;
    private final ReservationService reservationService;

    @Override
    public void run(ApplicationArguments args) {
        resourceRepository.findEnabledById(1L).ifPresent(resource -> {
            if (seatRepository.countEnabledByResource(resource.getId()) == 0) {
                GenerateSeatsRequest seatReq = new GenerateSeatsRequest();
                seatReq.setCount(resource.getTotalCapacity());
                seatReq.setPrefix("A");
                int seats = reservationService.generateSeats(resource.getId(), seatReq);
                log.info("reservation demo seats initialized: {}", seats);
            }

            long existingSlots = slotRepository.listByResourceAndDate(resource.getId(), LocalDate.now()).size();
            if (existingSlots > 0) {
                return;
            }
            var slotReq = new GenerateSlotsRequest();
            slotReq.setDate(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
            slotReq.setEndDate(LocalDate.now().plusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE));
            int slots = reservationService.generateSlots(resource.getId(), slotReq);
            log.info("reservation demo slots initialized: {}", slots);
        });
    }
}
