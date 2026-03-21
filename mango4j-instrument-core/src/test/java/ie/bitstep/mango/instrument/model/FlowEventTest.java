package ie.bitstep.mango.instrument.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlowEventTest {

    @Test
    void begin_and_end_step_event_records_timing_and_updates() {
        FlowEvent event = FlowEvent.builder().name("demo.flow").timestamp(Instant.now()).build();

        event.beginStepEvent("demo.step", 10L, 100L, new OAttributes(Map.of("sku", "123")), "CLIENT");
        event.endStepEvent(20L, 175L, Map.of("status", "ok"));

        assertThat(event.events()).hasSize(1);
        OEvent step = event.events().get(0);
        assertThat(step.name()).isEqualTo("demo.step");
        assertThat(step.timeUnixNano()).isEqualTo(10L);
        assertThat(step.endTimeUnixNano()).isEqualTo(20L);
        assertThat(step.elapsedNanos()).isEqualTo(75L);
        assertThat(step.attributes().map()).containsEntry("sku", "123").containsEntry("status", "ok");
        assertThat(event.endTimestamp()).isNotNull();
    }

    @Test
    void snapshot_is_deep_enough_for_attributes_context_and_events() {
        FlowEvent event = FlowEvent.builder().name("demo.flow").build();
        event.attributes().put("user.id", "alice");
        event.eventContext().put("lifecycle", "STARTED");
        event.beginStepEvent("demo.step", 10L, 100L, new OAttributes(Map.of("sku", "123")), "CLIENT");
        event.endStepEvent(20L, 175L, null);

        FlowEvent snapshot = event.snapshot();
        event.attributes().put("later", "value");
        event.eventContext().put("lifecycle", "COMPLETED");

        assertThat(snapshot).isNotSameAs(event);
        assertThat(snapshot.attributes().map()).containsEntry("user.id", "alice").doesNotContainKey("later");
        assertThat(snapshot.eventContext()).containsEntry("lifecycle", "STARTED");
        assertThat(snapshot.events()).hasSize(1);
        assertThat(snapshot.events().get(0)).isNotSameAs(event.events().get(0));
    }

    @Test
    void builder_trace_and_defaults_are_preserved() {
        Instant timestamp = Instant.now();
        FlowEvent event = FlowEvent.builder()
                .name("demo.flow")
                .timestamp(timestamp)
                .putAttribute("user.id", "alice")
                .putEventContext("lifecycle", "STARTED")
                .build();

        event.trace("trace-1", "span-2", "parent-3");
        event.setReturnValue("ok");
        event.clearReturnValue();
        event.kind(null);
        event.endStepEvent(30L, 40L, Map.of("ignored", true));

        OEvent step = new OEvent("demo.step", 10L, null, "INTERNAL");
        step.setEndTimeUnixNano(20L);
        step.setElapsedNanos(10L);
        OEvent snapshot = step.snapshot();
        OAttributes nullBacked = new OAttributes(null);

        assertThat(event.timestamp()).isEqualTo(timestamp);
        assertThat(event.attributes().map()).containsEntry("user.id", "alice");
        assertThat(event.getEventContext()).containsEntry("lifecycle", "STARTED");
        assertThat(event.traceId()).isEqualTo("trace-1");
        assertThat(event.spanId()).isEqualTo("span-2");
        assertThat(event.parentSpanId()).isEqualTo("parent-3");
        assertThat(event.returnValue()).isNull();
        assertThat(event.kind()).isEqualTo(io.opentelemetry.api.trace.SpanKind.INTERNAL);
        assertThat(snapshot.attributes().map()).isEmpty();
        assertThat(snapshot.endTimeUnixNano()).isEqualTo(20L);
        assertThat(snapshot.elapsedNanos()).isEqualTo(10L);
        assertThat(snapshot.kind()).isEqualTo("INTERNAL");
        assertThat(nullBacked.map()).isEmpty();
    }

    @Test
    void oattributes_copies_input_and_allows_put() {
        Map<String, Object> source = new java.util.LinkedHashMap<>();
        source.put("user.id", "alice");
        OAttributes attributes = new OAttributes(source);
        source.put("later", "ignored");

        attributes.put("tenant.id", "bitstep");

        assertThat(attributes.map())
                .containsEntry("user.id", "alice")
                .containsEntry("tenant.id", "bitstep")
                .doesNotContainKey("later");
    }
}
