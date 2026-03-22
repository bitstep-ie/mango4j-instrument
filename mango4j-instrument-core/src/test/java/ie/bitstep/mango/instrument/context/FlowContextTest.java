package ie.bitstep.mango.instrument.context;

import static org.assertj.core.api.Assertions.assertThat;

import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.model.FlowEvent;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlowContextTest {

    @Test
    void writes_attributes_and_context_to_current_flow() {
        FlowProcessorSupport support = new FlowProcessorSupport();
        FlowContext context = new FlowContext(support);
        FlowEvent event = FlowEvent.builder().name("demo.flow").build();
        support.push(event);

        String aliasResult = context.put("flow.id", "demo-1");
        String result = context.putAttr("user.id", "alice");
        Integer contextValue = context.putContext("cart.size", 3);
        context.putAll(Map.of("tenant.id", "bitstep"));
        context.putAllAttrs(Map.of("tenant.id", "bitstep"));
        context.putAllContext(Map.of("currency", "EUR"));

        assertThat(aliasResult).isEqualTo("demo-1");
        assertThat(result).isEqualTo("alice");
        assertThat(contextValue).isEqualTo(3);
        assertThat(event.attributes().map())
                .containsEntry("flow.id", "demo-1")
                .containsEntry("user.id", "alice")
                .containsEntry("tenant.id", "bitstep");
        assertThat(event.eventContext())
                .containsEntry("cart.size", 3)
                .containsEntry("currency", "EUR");
    }

    @Test
    void put_all_delegates_to_attribute_writes_and_put_context_returns_same_reference() {
        FlowProcessorSupport support = new FlowProcessorSupport();
        FlowContext context = new FlowContext(support);
        FlowEvent event = FlowEvent.builder().name("demo.flow").build();
        support.push(event);
        Object marker = new Object();

        context.putAll(Map.of("tenant.id", "bitstep"));
        Object returned = context.putContext("marker", marker);

        assertThat(event.attributes().map()).containsEntry("tenant.id", "bitstep");
        assertThat(event.eventContext()).containsEntry("marker", marker);
        assertThat(returned).isSameAs(marker);
    }

    @Test
    void ignores_writes_when_support_is_disabled() {
        FlowProcessorSupport support = new FlowProcessorSupport();
        support.setEnabled(false);
        FlowContext context = new FlowContext(support);
        FlowEvent event = FlowEvent.builder().name("demo.flow").build();
        support.push(event);
        Object marker = new Object();

        context.putAttr("user.id", "alice");
        assertThat(context.putContext("cart.size", marker)).isSameAs(marker);

        assertThat(event.attributes().map()).isEmpty();
        assertThat(event.eventContext()).isEmpty();
        assertThat(support.currentContext()).isNull();
    }

    @Test
    void ignores_blank_keys_and_no_active_flow() {
        FlowProcessorSupport support = new FlowProcessorSupport();
        FlowContext context = new FlowContext(support);
        Object marker = new Object();

        assertThat(context.put(" ", "alice")).isEqualTo("alice");
        assertThat(context.putAttr("user.id", "alice")).isEqualTo("alice");
        context.putAll(Map.of("", "ignored"));
        context.putAllAttrs(Map.of("tenant.id", "bitstep"));
        assertThat(context.putContext(null, marker)).isSameAs(marker);
        assertThat(context.putContext("cart.size", marker)).isSameAs(marker);
        context.putAllContext(Map.of(" ", "ignored"));
        context.putAllContext(Map.of("currency", "EUR"));

        assertThat(support.currentContext()).isNull();
        assertThat(support.hasActiveFlow()).isFalse();
    }

    @Test
    void ignores_null_and_empty_maps_and_blank_entries_with_active_flow() {
        FlowProcessorSupport support = new FlowProcessorSupport();
        FlowContext context = new FlowContext(support);
        FlowEvent event = FlowEvent.builder().name("demo.flow").build();
        support.push(event);

        context.putAllAttrs(null);
        context.putAllAttrs(Map.of());
        context.putAllAttrs(Map.of(" ", "ignored"));
        context.putAllContext(null);
        context.putAllContext(Map.of());
        context.putAllContext(Map.of("", "ignored"));

        assertThat(event.attributes().map()).isEmpty();
        assertThat(event.eventContext()).isEmpty();
        assertThat(support.currentContext()).isSameAs(event);
    }
}
