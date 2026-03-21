package ie.bitstep.mango.instrument.core.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry;
import ie.bitstep.mango.instrument.model.FlowEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
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

    @Test
    void ignores_null_dispatch_and_unwraps_nested_reflection_exceptions() throws Exception {
        FlowHandlerRegistry registry = new FlowHandlerRegistry();
        AsyncDispatchBus bus = new AsyncDispatchBus(registry);
        bus.dispatch(null);
        bus.close();

        Class<?> workerClass = Class.forName("ie.bitstep.mango.instrument.core.dispatch.AsyncDispatchBus$Worker");
        Method unwrap = workerClass.getDeclaredMethod("unwrap", Throwable.class);
        unwrap.setAccessible(true);

        IllegalArgumentException root = new IllegalArgumentException("root");
        InvocationTargetException invocation = new InvocationTargetException(root);
        UndeclaredThrowableException undeclared = new UndeclaredThrowableException(invocation);
        UndeclaredThrowableException selfReferential = new UndeclaredThrowableException(null) {
            @Override
            public synchronized Throwable getUndeclaredThrowable() {
                return this;
            }
        };

        assertThat(unwrap.invoke(null, undeclared)).isSameAs(root);
        assertThat(unwrap.invoke(null, new InvocationTargetException(null))).isInstanceOf(InvocationTargetException.class);
        assertThat(unwrap.invoke(null, selfReferential)).isSameAs(selfReferential);
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
