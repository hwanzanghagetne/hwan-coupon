package com.hwan.coupon.coupon.infra;

import com.hwan.coupon.coupon.domain.BatchStatus;
import com.hwan.coupon.coupon.domain.CouponIssueBatch;
import com.hwan.coupon.coupon.repository.CouponIssueBatchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchRecoverySchedulerTest {

    @InjectMocks
    private BatchRecoveryScheduler batchRecoveryScheduler;

    @Mock
    private CouponIssueBatchRepository batchRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    private CouponIssueBatch pendingBatch(Long id) {
        CouponIssueBatch batch = CouponIssueBatch.create(1L, 10);
        ReflectionTestUtils.setField(batch, "id", id);
        // requestedAt을 6분 전으로 설정하여 5분 임계값 초과
        ReflectionTestUtils.setField(batch, "requestedAt", LocalDateTime.now().minusMinutes(6));
        return batch;
    }

    @Test
    @DisplayName("고착 배치가 없으면 아무 처리도 하지 않는다")
    void recoverStuckBatches_고착배치없음_조기반환() {
        when(batchRepository.findByStatusAndRequestedAtBefore(any(), any()))
                .thenReturn(List.of());

        batchRecoveryScheduler.recoverStuckBatches();

        verify(transactionTemplate, never()).executeWithoutResult(any());
    }

    @Test
    @DisplayName("PENDING 고착 배치를 FAILED로 마킹한다")
    void recoverStuckBatches_PENDING배치_FAILED처리() {
        CouponIssueBatch stuck = pendingBatch(100L);
        when(batchRepository.findByStatusAndRequestedAtBefore(any(), any()))
                .thenReturn(List.of(stuck));

        doAnswer(inv -> {
            var consumer = inv.<java.util.function.Consumer<org.springframework.transaction.TransactionStatus>>getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        CouponIssueBatch fresh = pendingBatch(100L);
        when(batchRepository.findById(100L)).thenReturn(Optional.of(fresh));

        batchRecoveryScheduler.recoverStuckBatches();

        assertThat(fresh.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(fresh.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("조회 시점엔 고착이었지만 처리 직전 이미 완료된 배치는 상태를 변경하지 않는다")
    void recoverStuckBatches_이미처리된배치_스킵() {
        CouponIssueBatch stuckInList = pendingBatch(200L);
        when(batchRepository.findByStatusAndRequestedAtBefore(any(), any()))
                .thenReturn(List.of(stuckInList));

        doAnswer(inv -> {
            var consumer = inv.<java.util.function.Consumer<org.springframework.transaction.TransactionStatus>>getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        // fresh 조회 시엔 이미 DONE 상태
        CouponIssueBatch alreadyDone = CouponIssueBatch.create(1L, 10);
        ReflectionTestUtils.setField(alreadyDone, "id", 200L);
        alreadyDone.markProcessing();
        alreadyDone.markDone();
        when(batchRepository.findById(200L)).thenReturn(Optional.of(alreadyDone));

        batchRecoveryScheduler.recoverStuckBatches();

        assertThat(alreadyDone.getStatus()).isEqualTo(BatchStatus.DONE);
    }
}