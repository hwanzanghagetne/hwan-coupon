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
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchRecoverySchedulerTest {

    @InjectMocks
    private BatchRecoveryScheduler batchRecoveryScheduler;

    @Mock
    private CouponIssueBatchRepository batchRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    private CouponIssueBatch batch(Long id, BatchStatus status) {
        CouponIssueBatch batch = CouponIssueBatch.create(1L, 10);
        ReflectionTestUtils.setField(batch, "id", id);
        ReflectionTestUtils.setField(batch, "status", status);
        return batch;
    }

    private void stubTransactionTemplateToRunCallback() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    @DisplayName("고착 배치가 없으면 아무 처리도 하지 않는다")
    void recoverStuckBatches_고착배치없음_조기반환() {
        when(batchRepository.findByStatusAndUpdatedAtBefore(any(), any()))
                .thenReturn(List.of());

        batchRecoveryScheduler.recoverStuckBatches();

        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    @DisplayName("PENDING 고착 배치를 조건부 UPDATE로 FAILED 처리한다")
    void recoverStuckBatches_PENDING배치_FAILED처리() {
        CouponIssueBatch stuck = batch(100L, BatchStatus.PENDING);
        when(batchRepository.findByStatusAndUpdatedAtBefore(eq(BatchStatus.PENDING), any()))
                .thenReturn(List.of(stuck));
        when(batchRepository.findByStatusAndUpdatedAtBefore(eq(BatchStatus.PROCESSING), any()))
                .thenReturn(List.of());
        stubTransactionTemplateToRunCallback();
        when(batchRepository.markFailedIfStale(eq(100L), eq(BatchStatus.PENDING), any(), any()))
                .thenReturn(1);

        batchRecoveryScheduler.recoverStuckBatches();

        verify(batchRepository).markFailedIfStale(eq(100L), eq(BatchStatus.PENDING), any(), any());
    }

    @Test
    @DisplayName("PROCESSING 고착 배치를 조건부 UPDATE로 FAILED 처리한다")
    void recoverStuckBatches_PROCESSING배치_FAILED처리() {
        CouponIssueBatch stuck = batch(200L, BatchStatus.PROCESSING);
        when(batchRepository.findByStatusAndUpdatedAtBefore(eq(BatchStatus.PENDING), any()))
                .thenReturn(List.of());
        when(batchRepository.findByStatusAndUpdatedAtBefore(eq(BatchStatus.PROCESSING), any()))
                .thenReturn(List.of(stuck));
        stubTransactionTemplateToRunCallback();
        when(batchRepository.markFailedIfStale(eq(200L), eq(BatchStatus.PROCESSING), any(), any()))
                .thenReturn(1);

        batchRecoveryScheduler.recoverStuckBatches();

        verify(batchRepository).markFailedIfStale(eq(200L), eq(BatchStatus.PROCESSING), any(), any());
    }

    @Test
    @DisplayName("조회 이후 정상 진행되어 조건이 더 이상 맞지 않으면 조건부 UPDATE가 0건이라 아무 것도 바뀌지 않는다")
    void recoverStuckBatches_조회이후진행됨_조건불일치로_스킵() {
        // BatchProcessor가 조회 직후 청크를 커밋해 updatedAt을 갱신한 상황을 가정.
        // markFailedIfStale의 WHERE 조건(status + updatedAt < threshold)이 더 이상 맞지 않아
        // 실제 DB에서는 0건이 갱신된다 — 이 스케줄러 코드는 그 반환값만으로 판단하므로
        // 엔티티를 다시 읽어 상태를 확인할 필요가 없다.
        CouponIssueBatch stuckAtQueryTime = batch(300L, BatchStatus.PROCESSING);
        when(batchRepository.findByStatusAndUpdatedAtBefore(eq(BatchStatus.PENDING), any()))
                .thenReturn(List.of());
        when(batchRepository.findByStatusAndUpdatedAtBefore(eq(BatchStatus.PROCESSING), any()))
                .thenReturn(List.of(stuckAtQueryTime));
        stubTransactionTemplateToRunCallback();
        when(batchRepository.markFailedIfStale(eq(300L), eq(BatchStatus.PROCESSING), any(), any()))
                .thenReturn(0);

        batchRecoveryScheduler.recoverStuckBatches();

        verify(batchRepository).markFailedIfStale(eq(300L), eq(BatchStatus.PROCESSING), any(), any());
        // 0건 갱신이므로 배치 자체를 findById로 다시 읽어 도메인 메서드를 호출하는 일이 없어야 한다.
        verify(batchRepository, never()).findById(anyLong());
    }
}
