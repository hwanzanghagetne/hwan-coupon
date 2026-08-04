package com.hwan.coupon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.core.Ordered;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
// order를 명시하지 않으면 @Transactional 어드바이저와 우선순위가 같아져서, 재시도가
// 트랜잭션 "안쪽"에 걸릴 위험이 있다 — 그러면 데드락으로 rollback-only 마킹된 트랜잭션 안에서
// 재시도해봐야 UnexpectedRollbackException만 나고 끝난다. 재시도가 트랜잭션을 감싸서
// 실패할 때마다 완전히 새 트랜잭션으로 다시 시작하도록 순서를 명시적으로 고정한다.
@EnableRetry(order = Ordered.LOWEST_PRECEDENCE - 1)
public class CouponApplication {

	public static void main(String[] args) {
		SpringApplication.run(CouponApplication.class, args);
	}

}
