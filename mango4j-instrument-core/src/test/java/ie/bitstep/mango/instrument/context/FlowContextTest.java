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

        String result = context.putAttr("user.id", "alice");
        context.putAllAttrs(Map.of("tenant.id", "bitstep"));
        context.putContext("cart.size", 3);
        context.putAllContext(Map.of("currency", "EUR"));

        assertThat(result).isEqualTo("alice");
        assertThat(event.attributes().map())
                .containsEntry("user.id", "alice")
                .containsEntry("tenant.id", "bitstep");
        assertThat(event.eventContext())
                .containsEntry("cart.size", 3)
                .containsEntry("currency", "EUR");
    }

    @Test
    void ignores_writes_when_support_is_disabled() {
        FlowProcessorSupport support = new FlowProcessorSupport();
        support.setEnabled(false);
        FlowContext context = new FlowContext(support);
        FlowEvent event = FlowEvent.builder().name("demo.flow").build();
        support.push(event);

        context.putAttr("user.id", "alice");
        context.putContext("cart.size", 3);

        assertThat(event.attributes().map()).isEmpty();
        assertThat(event.eventContext()).isEmpty();
        assertThat(support.currentContext()).isNull();
    }

    @Test
    void ignores_blank_keys_and_no_active_flow() {
        FlowProcessorSupport support = new FlowProcessorSupport();
        FlowContext context = new FlowContext(support);

        assertThat(context.put(" ", "alice")).isEqualTo("alice");
        assertThat(context.putAttr("user.id", "alice")).isEqualTo("alice");
        context.putAll(Map.of("", "ignored"));
        context.putAllAttrs(Map.of("tenant.id", "bitstep"));
        context.putContext(null, 3);
        assertThat(context.putContext("cart.size", 3)).isEqualTo(3);
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
