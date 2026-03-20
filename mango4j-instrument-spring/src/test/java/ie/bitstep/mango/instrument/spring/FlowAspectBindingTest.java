package ie.bitstep.mango.instrument.spring;

import static org.assertj.core.api.Assertions.assertThat;

import ie.bitstep.mango.instrument.annotations.Flow;
import ie.bitstep.mango.instrument.annotations.Kind;
import ie.bitstep.mango.instrument.annotations.PushAttribute;
import ie.bitstep.mango.instrument.annotations.PushContextValue;
import ie.bitstep.mango.instrument.core.sinks.FlowSinkHandler;
import ie.bitstep.mango.instrument.model.FlowEvent;
import io.opentelemetry.api.trace.SpanKind;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adapted from obsinity:
 * /home/jallen/git/obsinity/obsinity-collection-spring/src/test/java/com/obsinity/collection/spring/FlowAspectBindingTest.java
 */
class FlowAspectBindingTest {

    @Test
    void emits_started_and_completed_with_attributes_and_context() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            SampleFlows flows = context.getBean(SampleFlows.class);
            CapturingSink sink = context.getBean(CapturingSink.class);

            flows.checkout("alice", 3);

            awaitSize(sink.events, 2);
            FlowEvent started = sink.events.get(0);
            FlowEvent completed = sink.events.get(1);

            assertThat(started.name()).isEqualTo("demo.checkout");
            assertThat(completed.name()).isEqualTo("demo.checkout");
            assertThat(started.eventContext()).containsEntry("lifecycle", "STARTED");
            assertThat(completed.eventContext()).containsEntry("lifecycle", "COMPLETED");
            assertThat(started.attributes().map()).containsEntry("user.id", "alice");
            assertThat(completed.attributes().map()).containsEntry("user.id", "alice");
            assertThat(started.eventContext()).containsEntry("cart.size", 3);
            assertThat(completed.eventContext()).containsEntry("cart.size", 3);
        }
    }

    @Test
    void captures_return_value_for_non_void_flow() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            SampleFlows flows = context.getBean(SampleFlows.class);
            CapturingSink sink = context.getBean(CapturingSink.class);

            assertThat(flows.checkoutResult("bob")).isEqualTo("ok:bob");

            awaitSize(sink.events, 2);
            FlowEvent completed = sink.events.get(1);
            assertThat(completed.name()).isEqualTo("demo.checkout.result");
            assertThat(completed.eventContext()).containsEntry("lifecycle", "COMPLETED");
            assertThat(completed.returnValue()).isEqualTo("ok:bob");
        }
    }

    private static void awaitSize(List<FlowEvent> events, int expectedSize) {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (events.size() >= expectedSize) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(events).hasSize(expectedSize);
    }

    @Configuration
    @EnableMangoInstrumentation
    static class TestConfig {
        @Bean
        SampleFlows sampleFlows() {
            return new SampleFlows();
        }

        @Bean
        CapturingSink capturingSink() {
            return new CapturingSink();
        }
    }

    static class SampleFlows {
        @Flow(name = "demo.checkout")
        @Kind(SpanKind.SERVER)
        public void checkout(@PushAttribute("user.id") String userId, @PushContextValue("cart.size") int items) {
        }

        @Flow(name = "demo.checkout.result")
        public String checkoutResult(@PushAttribute("user.id") String userId) {
            return "ok:" + userId;
        }
    }

    static class CapturingSink implements FlowSinkHandler {
        final List<FlowEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void handle(FlowEvent event) {
            events.add(event);
        }
    }
}
