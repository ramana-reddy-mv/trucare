package com.trucare.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(name = "notification.delivery.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationDeliveryWorker {
    private final NotificationStore store;
    private final NotificationDispatcher dispatcher;
    private final int batchSize;
    private final long staleTimeoutMs;

    public NotificationDeliveryWorker(NotificationStore store, NotificationDispatcher dispatcher,
                                      @Value("${notification.delivery.batch-size:20}") int batchSize,
                                      @Value("${notification.delivery.stale-processing-timeout-ms:60000}")
                                      long staleTimeoutMs) {
        this.store = store;
        this.dispatcher = dispatcher;
        if (batchSize < 1 || staleTimeoutMs < 1) {
            throw new IllegalStateException("Notification delivery batch size and stale timeout must be positive");
        }
        this.batchSize = batchSize;
        this.staleTimeoutMs = staleTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${notification.delivery.fixed-delay-ms:2000}")
    public void dispatchDue() {
        Instant cutoff = Instant.now().minusMillis(staleTimeoutMs);
        for (Long id : store.staleIds(cutoff, batchSize)) {
            store.recoverStale(id, cutoff);
        }
        for (Long id : store.dueIds(batchSize)) {
            dispatcher.dispatch(id);
        }
    }
}
