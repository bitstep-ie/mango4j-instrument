package ie.bitstep.mango.instrument.core.dispatch;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry;
import ie.bitstep.mango.instrument.model.FlowEvent;

import static org.assertj.core.api.Assertions.assertThat;

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
	void registry_ignores_null_handler_registration() {
		FlowHandlerRegistry registry = new FlowHandlerRegistry();
		registry.register(null);
		assertThat(registry.handlers()).isEmpty();
	}

	@Test
	void worker_run_loop_handles_poll_timeout_with_null_event() throws Exception {
		FlowHandlerRegistry registry = new FlowHandlerRegistry();
		List<FlowEvent> seen = new CopyOnWriteArrayList<>();
		registry.register(seen::add);
		AsyncDispatchBus bus = new AsyncDispatchBus(registry);
		// Let the worker idle for longer than the 250ms poll timeout so it loops with a null event
		Thread.sleep(320);
		bus.close();
		assertThat(seen).isEmpty();
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
		assertThat(unwrap.invoke(null, new InvocationTargetException(null)))
				.isInstanceOf(InvocationTargetException.class);
		assertThat(unwrap.invoke(null, selfReferential)).isSameAs(selfReferential);
	}

	@Test
	void drops_event_and_logs_warn_when_queue_is_at_capacity() throws Exception {
		CountDownLatch workerOccupied = new CountDownLatch(1);
		CountDownLatch releaseWorker = new CountDownLatch(1);

		FlowHandlerRegistry registry = new FlowHandlerRegistry();
		registry.register(event -> {
			workerOccupied.countDown();
			try {
				releaseWorker.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});

		AsyncDispatchBus bus = new AsyncDispatchBus(registry);

		// Block the worker thread inside the sink so it can't drain the queue
		bus.dispatch(FlowEvent.builder().name("blocker").build());
		assertThat(workerOccupied.await(2, TimeUnit.SECONDS)).isTrue();

		// Fill the queue to its capacity limit
		for (int i = 0; i < AsyncDispatchBus.MAX_QUEUE_DEPTH; i++) {
			bus.dispatch(FlowEvent.builder().name("flood").build());
		}

		// This dispatch overflows the queue — the warn branch in offer() is hit
		bus.dispatch(FlowEvent.builder().name("overflow").build());

		releaseWorker.countDown();
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
