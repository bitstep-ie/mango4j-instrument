package ie.bitstep.mango.instrument.spring.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import ie.bitstep.mango.instrument.annotations.FlowException;
import ie.bitstep.mango.instrument.annotations.OnFlowCompleted;
import ie.bitstep.mango.instrument.annotations.OnFlowFailure;
import ie.bitstep.mango.instrument.annotations.RequiredAttributes;
import ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry;
import ie.bitstep.mango.instrument.model.FlowEvent;
import ie.bitstep.mango.instrument.spring.annotations.FlowSink;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Adapted from obsinity:
 * /home/jallen/git/obsinity/obsinity-collection-spring/src/test/java/com/obsinity/collection/spring/scanner/FlowSinkScannerTest.java
 */
class FlowSinkScannerTest {

    @FlowSink
    static class MySinks {
        static final AtomicInteger failureAny = new AtomicInteger();
        static final AtomicInteger failureWithAttrs = new AtomicInteger();
        static final AtomicInteger failureWithThrowableOnly = new AtomicInteger();
        static volatile Throwable lastThrowable;
        static final AtomicInteger completedWithThrowable = new AtomicInteger();

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
    }

    private FlowHandlerRegistry registry;
    private FlowSinkScanner scanner;

    @BeforeEach
    void setUp() {
        registry = new FlowHandlerRegistry();
        scanner = new FlowSinkScanner(registry);
        scanner.postProcessAfterInitialization(new MySinks(), "mySinks");
        MySinks.failureAny.set(0);
        MySinks.failureWithAttrs.set(0);
        MySinks.failureWithThrowableOnly.set(0);
        MySinks.lastThrowable = null;
        MySinks.completedWithThrowable.set(0);
    }

    @Test
    void invokes_failure_handlers_when_required_attributes_are_present() throws Exception {
        FlowEvent event = FlowEvent.builder().name("orders.checkout").build();
        event.eventContext().put("lifecycle", "FAILED");
        event.attributes().put("order.id", "123");
        event.attributes().put("error.code", "PAYMENT");
        event.setThrowable(new IllegalArgumentException("bad payment"));

        registry.handlers().get(0).handle(event);

        assertThat(MySinks.failureAny.get()).isEqualTo(1);
        assertThat(MySinks.failureWithAttrs.get()).isEqualTo(1);
        assertThat(MySinks.failureWithThrowableOnly.get()).isEqualTo(1);
        assertThat(MySinks.lastThrowable).isInstanceOf(IllegalArgumentException.class);
        assertThat(MySinks.completedWithThrowable.get()).isZero();
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
}
