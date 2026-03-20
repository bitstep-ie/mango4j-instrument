package ie.bitstep.mango.instrument.core.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry;
import ie.bitstep.mango.instrument.model.FlowEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class AsyncDispatchBusTest {

    @Test
    void dispatches_a_snapshot_not_the_mutated_original_event() {
        FlowHandlerRegistry registry = new FlowHandlerRegistry();
        List<FlowEvent> seen = new CopyOnWriteArrayList<>();
        registry.register(seen::add);
        AsyncDispatchBus bus = new AsyncDispatchBus(registry);

        FlowEvent original = FlowEvent.builder().name("demo.flow").build();
        original.eventContext().put("lifecycle", "STARTED");
        original.attributes().put("user.id", "alice");

        bus.dispatch(original);

        original.eventContext().put("lifecycle", "COMPLETED");
        original.attributes().put("extra", "later");

        awaitSize(seen, 1);
        FlowEvent delivered = seen.get(0);
        assertThat(delivered).isNotSameAs(original);
        assertThat(delivered.eventContext()).containsEntry("lifecycle", "STARTED");
        assertThat(delivered.attributes().map())
                .containsEntry("user.id", "alice")
                .doesNotContainKey("extra");

        bus.close();
    }

    @Test
    void continues_dispatching_when_one_sink_throws() {
        FlowHandlerRegistry registry = new FlowHandlerRegistry();
        List<FlowEvent> seen = new CopyOnWriteArrayList<>();
        registry.register(event -> {
            throw new IllegalStateException("boom");
        });
        registry.register(seen::add);
        AsyncDispatchBus bus = new AsyncDispatchBus(registry);

        FlowEvent event = FlowEvent.builder().name("demo.flow").build();
        bus.dispatch(event);

        awaitSize(seen, 1);
        assertThat(seen.get(0).name()).isEqualTo("demo.flow");

        bus.close();
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
