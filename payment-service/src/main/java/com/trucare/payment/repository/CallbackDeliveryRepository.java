package com.trucare.payment.repository;

import com.trucare.payment.model.CallbackDelivery;
import com.trucare.payment.model.CallbackStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CallbackDeliveryRepository extends JpaRepository<CallbackDelivery, Long> {
    List<CallbackDelivery> findByDeliveryStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            CallbackStatus status, Instant now, Pageable pageable);

    @Query("select c.id from CallbackDelivery c where c.deliveryStatus = :status "
            + "and c.nextAttemptAt <= :now order by c.createdAt")
    List<Long> findDueIds(@Param("status") CallbackStatus status, @Param("now") Instant now,
                          Pageable pageable);

    List<CallbackDelivery> findByPaymentIdAndDeliveryStatus(Long paymentId, CallbackStatus status);

    @Query("select c.id from CallbackDelivery c where c.deliveryStatus = :status "
            + "and c.startGatewayAfterDelivery = true order by c.createdAt")
    List<Long> findGatewayContinuationIds(@Param("status") CallbackStatus status, Pageable pageable);

    @Query("select c.id from CallbackDelivery c where c.deliveryStatus = :status "
            + "and c.deliveryStartedAt <= :cutoff order by c.deliveryStartedAt")
    List<Long> findStaleDeliveryIds(@Param("status") CallbackStatus status,
                                    @Param("cutoff") Instant cutoff, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CallbackDelivery c join fetch c.payment where c.id = :id")
    CallbackDelivery findByIdForUpdate(@Param("id") Long id);
}
