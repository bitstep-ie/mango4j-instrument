package ie.bitstep.mango.instrument.core;

import static org.assertj.core.api.Assertions.assertThat;

import ie.bitstep.mango.instrument.model.FlowEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FlowProcessorSupportTest {

    @Test
    void pop_clears_thread_state_on_mismatch() {
        FlowProcessorSupport support = new FlowProcessorSupport();
        FlowEvent first = FlowEvent.builder().name("first").build();
        FlowEvent second = FlowEvent.builder().name("second").build();

        support.push(first);
        support.push(second);
        support.pop(first);

        assertThat(support.currentContext()).isNull();
        assertThat(support.hasActiveFlow()).isFalse();
    }

    @Test
    void unix_nanos_uses_epoch_and_nano_components() {
        FlowProcessorSupport support = new FlowProcessorSupport();
        Instant instant = Instant.ofEpochSecond(12L, 345L);

        assertThat(support.unixNanos(instant)).isEqualTo(12_000_000_345L);
    }

    @Test
    void disabled_support_hides_current_context_but_keeps_stack_for_reenable() {
        FlowProcessorSupport support = new FlowProcessorSupport();
        FlowEvent event = FlowEvent.builder().name("flow").build();
        support.push(event);

        support.setEnabled(false);
        assertThat(support.currentContext()).isNull();
        assertThat(support.hasActiveFlow()).isTrue();

        support.setEnabled(true);
        assertThat(support.currentContext()).isSameAs(event);
    }
}
