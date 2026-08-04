package com.hwan.coupon.demo;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * self-invocation이 @Async를 실제로 무력화하는지 직접 확인하는 독립 실험용 테스트.
 * 프로덕션 코드/설정은 전혀 건드리지 않고, 별도의 미니 Spring 컨텍스트로 검증한다.
 *
 * 검증 방식: 필드에 스레드 이름을 기록해서 비교하는 방식은 실패했다 — CGLIB 프록시는
 * @Async가 안 걸린(어드바이스 대상이 아닌) 메서드를 호출하면 프록시가 아닌 target(원본) 객체에서
 * 그대로 실행시키기 때문에, ctx.getBean()으로 받은 프록시 참조로 필드를 읽으면 값이 항상 null이었다.
 * (이게 바로 self-invocation 문제 그 자체였음 — 검증하려던 현상이 검증 코드 안에서도 그대로 발생)
 * 그래서 "외부에서 관찰 가능한" 소요 시간(블로킹 여부)으로 검증 방식을 바꿨다.
 */
class SelfInvocationAsyncDemoTest {

    @Configuration
    @EnableAsync
    static class TestConfig {
        @Bean
        SelfInvoker selfInvoker() {
            return new SelfInvoker();
        }

        @Bean
        AsyncWorker asyncWorker() {
            return new AsyncWorker();
        }

        @Bean
        ProperCaller properCaller(AsyncWorker asyncWorker) {
            return new ProperCaller(asyncWorker);
        }
    }

    private static final int SLEEP_MS = 300;

    /** 같은 클래스 안에서 자기 자신의 @Async 메서드를 this로 호출하는 self-invocation 케이스 */
    static class SelfInvoker {
        public void callSelf() throws InterruptedException {
            this.asyncMethod(); // ← this로 자기 자신 호출 (self-invocation) → 프록시를 안 거침
        }

        @Async
        public void asyncMethod() throws InterruptedException {
            Thread.sleep(SLEEP_MS); // 진짜 비동기라면 호출자가 이 sleep을 기다릴 이유가 없음
        }
    }

    /** 별도 빈으로 분리해서 정상적으로 프록시를 거치는 케이스 */
    static class AsyncWorker {
        @Async
        public void asyncMethod() throws InterruptedException {
            Thread.sleep(SLEEP_MS);
        }
    }

    static class ProperCaller {
        private final AsyncWorker asyncWorker;

        ProperCaller(AsyncWorker asyncWorker) {
            this.asyncWorker = asyncWorker;
        }

        public void callWorker() throws InterruptedException {
            asyncWorker.asyncMethod(); // ← 외부 빈을 통해 호출 → 프록시를 정상적으로 거침
        }
    }

    @Test
    void self_invocation은_프록시를_안_거쳐서_동기로_블로킹된다() throws Exception {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            SelfInvoker selfInvoker = ctx.getBean(SelfInvoker.class);

            long start = System.currentTimeMillis();
            selfInvoker.callSelf();
            long elapsed = System.currentTimeMillis() - start;

            System.out.println("[self-invocation] callSelf() 리턴까지 걸린 시간 = " + elapsed + "ms (내부에 " + SLEEP_MS + "ms sleep이 있음)");

            // 진짜 비동기였다면 즉시 리턴됐어야 하는데, self-invocation은 프록시를 안 거쳐서
            // 그냥 같은 스레드에서 동기 실행되므로 sleep(300ms)을 고스란히 기다리게 된다.
            assertThat(elapsed).isGreaterThanOrEqualTo(SLEEP_MS);
        }
    }

    @Test
    void 별도_빈으로_분리하면_프록시를_거쳐서_실제로_비동기로_즉시_리턴된다() throws Exception {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            ProperCaller properCaller = ctx.getBean(ProperCaller.class);

            long start = System.currentTimeMillis();
            properCaller.callWorker();
            long elapsed = System.currentTimeMillis() - start;

            System.out.println("[proper async] callWorker() 리턴까지 걸린 시간 = " + elapsed + "ms (내부에 " + SLEEP_MS + "ms sleep이 있음)");

            // 프록시를 정상적으로 거치면 실행이 별도 스레드로 위임되어 호출자는 sleep을 기다리지 않고 바로 리턴받는다.
            assertThat(elapsed).isLessThan(100);
        }
    }
}
