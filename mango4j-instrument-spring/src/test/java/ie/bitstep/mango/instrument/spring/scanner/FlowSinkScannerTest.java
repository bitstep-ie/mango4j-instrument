package ie.bitstep.mango.instrument.spring.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import ie.bitstep.mango.instrument.annotations.FlowException;
import ie.bitstep.mango.instrument.annotations.OnFlowCompleted;
import ie.bitstep.mango.instrument.annotations.OnFlowFailure;
import ie.bitstep.mango.instrument.annotations.OnOutcome;
import ie.bitstep.mango.instrument.annotations.Outcome;
import ie.bitstep.mango.instrument.annotations.RequiredAttributes;
import ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry;
import ie.bitstep.mango.instrument.model.FlowEvent;
import ie.bitstep.mango.instrument.spring.annotations.FlowSink;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.TargetClassAware;

/**
 * Adapted from obsinity:
 * /home/jallen/git/obsinity/obsinity-collection-spring/src/test/java/com/obsinity/collection/spring/scanner/FlowSinkScannerTest.java
 *
 * These tests intentionally make the current lifecycle/outcome semantics visible:
 * - `@OnFlowFailure` matches emitted failed lifecycle events.
 * - `@OnFlowCompleted @OnOutcome(FAILURE)` currently overlaps with failed lifecycle dispatch in the runtime matcher.
 * - that overlap is tested here as current behavior, not as the ideal long-term semantic model.
 */
class FlowSinkScannerTest {

    @FlowSink
    static class MySinks {
        static final AtomicInteger failureAny = new AtomicInteger();
        static final AtomicInteger failureWithAttrs = new AtomicInteger();
        static final AtomicInteger failureWithThrowableOnly = new AtomicInteger();
        static volatile Throwable lastThrowable;
        static final AtomicInteger completedWithThrowable = new AtomicInteger();
        static final AtomicInteger completedFailureOutcome = new AtomicInteger();

        @OnFlowFailure
        public void onFailureAny(FlowEvent event) {
            failureAny.incrementAndGet();
        }

        @OnFlowFailure
        @RequiredAttributes({"order.id", "error.code"})
        public void onFailureWithAttrs(FlowEvent event) {
            failureWithAttrs.incrementAndGet();
        }

        @OnFlowFailure
        public void onFailureThrowable(Throwable throwable) {
            lastThrowable = throwable;
            failureWithThrowableOnly.incrementAndGet();
        }

        @OnFlowFailure
        public void onFailureThrowableRoot(@FlowException(FlowException.Source.ROOT) Throwable throwable) {
        }

        @OnFlowCompleted
        public void onCompletedThrowable(Throwable throwable) {
            completedWithThrowable.incrementAndGet();
        }

        @OnFlowCompleted
        @OnOutcome(Outcome.FAILURE)
        public void onCompletedFailureOutcome(Throwable throwable) {
            lastThrowable = throwable;
            completedFailureOutcome.incrementAndGet();
        }
    }

    @FlowSink
    static class FailureFinishOnlySink {
        static final AtomicInteger completedFailureOutcome = new AtomicInteger();
        static volatile Throwable lastThrowable;

        @OnFlowCompleted
        @OnOutcome(Outcome.FAILURE)
        public void onCompletedFailureOutcome(Throwable throwable) {
            lastThrowable = throwable;
            completedFailureOutcome.incrementAndGet();
        }
    }

    @FlowSink
    static class NullTargetClassAwareSink implements TargetClassAware {
        @Override
        public Class<?> getTargetClass() {
            return null;
        }

        @OnFlowFailure
        public void onFailure(FlowEvent event) {
        }
    }

    static class PlainBean {
    }

    @FlowSink
    static class NoLifecycleSink {
        public void helperOnly() {
        }
    }

    private FlowHandlerRegistry registry;
    private FlowSinkScanner scanner;

    @BeforeEach
    void setUp() {
        registry = new FlowHandlerRegistry();
        scanner = new FlowSinkScanner(registry);
        scanner.postProcessAfterInitialization(new MySinks(), "mySinks");
        scanner.postProcessAfterInitialization(new FailureFinishOnlySink(), "failureFinishOnlySink");
        MySinks.failureAny.set(0);
        MySinks.failureWithAttrs.set(0);
        MySinks.failureWithThrowableOnly.set(0);
        MySinks.lastThrowable = null;
        MySinks.completedWithThrowable.set(0);
        MySinks.completedFailureOutcome.set(0);
        FailureFinishOnlySink.completedFailureOutcome.set(0);
        FailureFinishOnlySink.lastThrowable = null;
    }

    @Test
    void failed_lifecycle_dispatch_matches_failure_handlers_and_current_failure_outcome_overlap() throws Exception {
        FlowEvent event = FlowEvent.builder().name("orders.checkout").build();
        event.eventContext().put("lifecycle", "FAILED");
        event.attributes().put("order.id", "123");
        event.attributes().put("error.code", "PAYMENT");
        event.setThrowable(new IllegalArgumentException("bad payment"));

        for (var handler : registry.handlers()) {
            handler.handle(event);
        }

        assertThat(MySinks.failureAny.get()).isEqualTo(1);
        assertThat(MySinks.failureWithAttrs.get()).isEqualTo(1);
        assertThat(MySinks.failureWithThrowableOnly.get()).isEqualTo(1);
        assertThat(MySinks.lastThrowable).isInstanceOf(IllegalArgumentException.class);
        assertThat(MySinks.completedWithThrowable.get()).isZero();
        assertThat(MySinks.completedFailureOutcome.get()).isZero();
        assertThat(FailureFinishOnlySink.completedFailureOutcome.get()).isEqualTo(1);
        assertThat(FailureFinishOnlySink.lastThrowable).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void skips_required_attribute_handler_when_attributes_are_missing() throws Exception {
        FlowEvent event = FlowEvent.builder().name("orders.checkout").build();
        event.eventContext().put("lifecycle", "FAILED");
        event.setThrowable(new IllegalStateException("failed"));

        registry.handlers().get(0).handle(event);

        assertThat(MySinks.failureAny.get()).isEqualTo(1);
        assertThat(MySinks.failureWithAttrs.get()).isZero();
    }

    @Test
    void success_completion_does_not_match_failure_outcome_handlers() throws Exception {
        FlowEvent event = FlowEvent.builder().name("orders.checkout").build();
        event.eventContext().put("lifecycle", "COMPLETED");

        for (var handler : registry.handlers()) {
            handler.handle(event);
        }

        assertThat(MySinks.completedWithThrowable.get()).isZero();
        assertThat(MySinks.completedFailureOutcome.get()).isZero();
        assertThat(FailureFinishOnlySink.completedFailureOutcome.get()).isZero();
    }

    @Test
    void target_class_aware_sink_with_null_target_class_is_still_registered() {
        FlowHandlerRegistry localRegistry = new FlowHandlerRegistry();
        FlowSinkScanner localScanner = new FlowSinkScanner(localRegistry);

        Object bean = new NullTargetClassAwareSink();
        Object processed = localScanner.postProcessAfterInitialization(bean, "nullTargetClassAwareSink");

        assertThat(processed).isSameAs(bean);
        assertThat(localRegistry.handlers()).hasSize(1);
    }

    @Test
    void returns_original_bean_for_non_sink_and_no_lifecycle_sink() {
        FlowHandlerRegistry localRegistry = new FlowHandlerRegistry();
        FlowSinkScanner localScanner = new FlowSinkScanner(localRegistry);
        PlainBean plainBean = new PlainBean();
        NoLifecycleSink noLifecycleSink = new NoLifecycleSink();

        Object plainProcessed = localScanner.postProcessAfterInitialization(plainBean, "plainBean");
        Object noLifecycleProcessed = localScanner.postProcessAfterInitialization(noLifecycleSink, "noLifecycleSink");

        assertThat(plainProcessed).isSameAs(plainBean);
        assertThat(noLifecycleProcessed).isSameAs(noLifecycleSink);
        assertThat(localRegistry.handlers()).isEmpty();
    }
}
