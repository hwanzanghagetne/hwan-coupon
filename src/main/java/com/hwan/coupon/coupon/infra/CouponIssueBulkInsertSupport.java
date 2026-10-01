package com.hwan.coupon.coupon.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 대량발급(BatchProcessor)과 선착순 배치 반영(CouponIssueWriter)이 공유하는
 * coupon_issue 멀티 VALUES INSERT 로직.
 *
 * batchUpdate()는 rewriteBatchedStatements=true 환경에서 SUCCESS_NO_INFO(-2)를 반환할 수 있어
 * .sum()으로 집계하면 issuedQuantity가 잘못 계산된다. 대신 멀티 VALUES INSERT 문을 직접 조립하고
 * update()를 사용하면 MySQL이 실제 영향받은 행 수만 반환하므로 INSERT IGNORE 중복 스킵도
 * 정확히 집계된다.
 *
 * INSERT IGNORE는 중복 회원뿐 아니라 존재하지 않는 회원 ID(FK 위반) 등도 같은 방식으로 조용히
 * 스킵한다 — 스킵 사유는 구분하지 않으며, targetCount와 issuedCount의 차이로만 드러난다.
 */
@Component
@RequiredArgsConstructor
public class CouponIssueBulkInsertSupport {

    private final JdbcTemplate jdbcTemplate;

    public int insertIgnore(Long couponId, List<Long> userIds) {
        if (userIds.isEmpty()) return 0;

        StringBuilder sql = new StringBuilder(
                "INSERT IGNORE INTO coupon_issue (coupon_id, user_id, status, issued_at) VALUES "
        );
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        List<Object> params = new ArrayList<>();

        for (int i = 0; i < userIds.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("(?,?,'ISSUED',?)");
            params.add(couponId);
            params.add(userIds.get(i));
            params.add(now);
        }

        return jdbcTemplate.update(sql.toString(), params.toArray());
    }
}
