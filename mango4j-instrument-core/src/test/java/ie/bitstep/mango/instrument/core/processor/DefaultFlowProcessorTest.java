package ie.bitstep.mango.instrument.core.processor;

import static org.assertj.core.api.Assertions.assertThat;

import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.core.dispatch.AsyncDispatchBus;
import ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry;
import ie.bitstep.mango.instrument.model.FlowEvent;
import ie.bitstep.mango.instrument.model.OStatus;
import ie.bitstep.mango.instrument.validation.FlowAttributeValidator;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DefaultFlowProcessorTest {

    @Test
    void emits_started_and_completed_events_with_meta_and_cleans_up_context() {
        FlowHandlerRegistry registry = new FlowHandlerRegistry();
        List<FlowEvent> seen = new CopyOnWriteArrayList<>();
        registry.register(seen::add);
        FlowProcessorSupport support = new FlowProcessorSupport();
        DefaultFlowProcessor processor = new DefaultFlowProcessor(new AsyncDispatchBus(registry), support);
        FlowMeta startMeta = FlowMeta.builder()
                .kind("SERVER")
                .trace("trace-1", "span-1", "parent-1", "vendor=data")
                .build();
        FlowMeta endMeta = FlowMeta.builder().status("OK", "done").build();

        processor.onFlowStarted("demo.flow", Map.of("user.id", "alice"), Map.of("tenant.id", "bitstep"), startMeta);
        processor.onFlowCompleted("demo.flow", Map.of("result", "ok"), Map.of(), endMeta);

        awaitSize(seen, 2);
        FlowEvent started = seen.get(0);
        FlowEvent completed = seen.get(1);

        assertThat(started.eventContext()).containsEntry("lifecycle", "STARTED");
        assertThat(completed.eventContext()).containsEntry("lifecycle", "COMPLETED");
        assertThat(started.kind()).isEqualTo(SpanKind.SERVER);
        assertThat(started.traceId()).isEqualTo("trace-1");
        assertThat(started.spanId()).isEqualTo("span-1");
        assertThat(started.parentSpanId()).isEqualTo("parent-1");
        assertThat(started.attributes().map()).containsEntry("trace.tracestate", "vendor=data");
        assertThat(completed.attributes().map()).containsEntry("result", "ok");
        assertThat(completed.status()).isEqualTo(new OStatus(StatusCode.OK, "done"));
        assertThat(support.currentContext()).isNull();
        assertThat(support.hasActiveFlow()).isFalse();
    }

    @Test
    void failed_flow_validates_input_and_records_error_details() {
        FlowHandlerRegistry registry = new FlowHandlerRegistry();
        List<FlowEvent> seen = new CopyOnWriteArrayList<>();
        registry.register(seen::add);
        FlowProcessorSupport support = new FlowProcessorSupport();
        AtomicInteger validations = new AtomicInteger();
        FlowAttributeValidator validator = (key, value) -> validations.incrementAndGet();
        DefaultFlowProcessor processor = new DefaultFlowProcessor(new AsyncDispatchBus(registry), support, validator);
        IllegalStateException failure = new IllegalStateException("boom");

        processor.onFlowStarted("demo.flow", Map.of(), Map.of(), null);
        processor.onFlowFailed("demo.flow", failure, Map.of("order.id", "123"), Map.of("tenant.id", "bitstep"), null);

        awaitSize(seen, 2);
        FlowEvent failedEvent = seen.get(1);
        assertThat(failedEvent.eventContext()).containsEntry("lifecycle", "FAILED");
        assertThat(failedEvent.throwable()).isSameAs(failure);
        assertThat(failedEvent.attributes().map())
                .containsEntry("order.id", "123")
                .containsEntry("error", "IllegalStateException");
        assertThat(validations.get()).isEqualTo(3);
        assertThat(support.currentContext()).isNull();
    }

    @Test
    void falls_back_for_invalid_kind_and_status_code_and_noops_when_disabled() {
        FlowHandlerRegistry registry = new FlowHandlerRegistry();
        List<FlowEvent> seen = new CopyOnWriteArrayList<>();
        registry.register(seen::add);
        FlowProcessorSupport support = new FlowProcessorSupport();
        DefaultFlowProcessor processor = new DefaultFlowProcessor(new AsyncDispatchBus(registry), support);

        processor.onFlowStarted(
                "demo.flow",
                Map.of(),
                Map.of(),
                FlowMeta.builder().kind("NOT_A_KIND").trace("trace", "span", null, null).build());
        processor.onFlowCompleted(
                "demo.flow",
                Map.of(),
                Map.of(),
                FlowMeta.builder().status("NOT_A_STATUS", "weird").build());

        awaitSize(seen, 2);
        assertThat(seen.get(0).kind()).isEqualTo(SpanKind.INTERNAL);
        assertThat(seen.get(1).status()).isEqualTo(new OStatus(StatusCode.UNSET, "weird"));

        support.setEnabled(false);
        processor.onFlowStarted("disabled.flow", Map.of(), Map.of(), null);
        assertThat(seen).hasSize(2);
    }

    @Test
    void completion_and_failure_without_active_context_are_ignored_and_null_error_is_allowed() {
        FlowHandlerRegistry registry = new FlowHandlerRegistry();
        List<FlowEvent> seen = new CopyOnWriteArrayList<>();
        registry.register(seen::add);
        FlowProcessorSupport support = new FlowProcessorSupport();
        DefaultFlowProcessor processor = new DefaultFlowProcessor(new AsyncDispatchBus(registry), support);

        processor.onFlowCompleted("demo.flow", Map.of("result", "ok"), Map.of(), FlowMeta.builder().status("OK", null).build());
        processor.onFlowFailed("demo.flow", null, Map.of(), Map.of(), FlowMeta.builder().status("ERROR", null).build());

        assertThat(seen).isEmpty();

        processor.onFlowStarted("demo.flow", null, null, FlowMeta.builder().trace("trace-1", null, null, " ").build());
        processor.onFlowFailed("demo.flow", null, Map.of(), Map.of(), FlowMeta.builder().status("ERROR", null).build());

        awaitSize(seen, 2);
        FlowEvent failedEvent = seen.get(1);
        assertThat(failedEvent.eventContext()).containsEntry("lifecycle", "FAILED");
        assertThat(failedEvent.throwable()).isNull();
        assertThat(failedEvent.attributes().map()).doesNotContainKey("error");
        assertThat(failedEvent.status()).isEqualTo(new OStatus(StatusCode.ERROR, null));
        assertThat(seen.get(0).attributes().map()).doesNotContainKey("trace.tracestate");
    }

    @Test
    void supports_null_bus_and_null_support_dependencies() {
        DefaultFlowProcessor nullSupportProcessor = new DefaultFlowProcessor(null, null);

        nullSupportProcessor.onFlowStarted("demo.flow", Map.of(), Map.of(), null);
        nullSupportProcessor.onFlowCompleted("demo.flow", Map.of(), Map.of(), null);
        nullSupportProcessor.onFlowFailed("demo.flow", new IllegalStateException("boom"), Map.of(), Map.of(), null);

        FlowProcessorSupport support = new FlowProcessorSupport();
        DefaultFlowProcessor noBusProcessor = new DefaultFlowProcessor(null, support);

        noBusProcessor.onFlowStarted("demo.flow", Map.of(), Map.of(), FlowMeta.builder().kind(null).build());
        FlowEvent current = support.currentContext();
        assertThat(current).isNotNull();
        assertThat(current.eventContext()).containsEntry("lifecycle", "STARTED");

        noBusProcessor.onFlowCompleted("demo.flow", Map.of(), Map.of(), null);
        assertThat(support.currentContext()).isNull();
    }

    @Test
    void private_meta_helpers_cover_null_guards_and_trace_variants() throws Exception {
        DefaultFlowProcessor processor = new DefaultFlowProcessor(null, new FlowProcessorSupport());
        Method applyMeta = DefaultFlowProcessor.class.getDeclaredMethod("applyMeta", FlowEvent.class, FlowMeta.class);
        Method applyCompletionMeta =
                DefaultFlowProcessor.class.getDeclaredMethod("applyCompletionMeta", FlowEvent.class, FlowMeta.class);
        applyMeta.setAccessible(true);
        applyCompletionMeta.setAccessible(true);

        FlowEvent event = FlowEvent.builder().name("demo.flow").build();

        applyMeta.invoke(processor, null, FlowMeta.builder().kind("SERVER").build());
        applyMeta.invoke(processor, event, null);
        applyMeta.invoke(processor, event, FlowMeta.builder().trace(null, "span-1", null, "state").build());
        assertThat(event.traceId()).isNull();
        assertThat(event.spanId()).isEqualTo("span-1");
        assertThat(event.attributes().map()).containsEntry("trace.tracestate", "state");

        applyCompletionMeta.invoke(processor, null, FlowMeta.builder().status("OK", "done").build());
        applyCompletionMeta.invoke(processor, event, null);
        applyCompletionMeta.invoke(processor, event, FlowMeta.builder().status(null, "done").build());
        assertThat(event.status()).isNull();
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
}
