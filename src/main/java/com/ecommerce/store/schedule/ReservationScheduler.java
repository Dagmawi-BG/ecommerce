package com.ecommerce.store.schedule;

import com.ecommerce.store.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically releases stock held by pending orders that were never paid. */
@Component
public class ReservationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationScheduler.class);

    private final OrderService orderService;

    public ReservationScheduler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(
            fixedDelayString = "${app.checkout.release-scan-ms:60000}",
            initialDelayString = "${app.checkout.release-scan-ms:60000}")
    public void releaseExpired() {
        int released = orderService.releaseExpiredReservations();
        if (released > 0) {
            log.info("Released {} expired stock reservation(s)", released);
        }
    }
}
