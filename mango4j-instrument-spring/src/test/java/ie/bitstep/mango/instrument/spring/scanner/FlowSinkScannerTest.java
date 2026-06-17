package ie.bitstep.mango.instrument.spring.scanner;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.TargetClassAware;
import ie.bitstep.mango.instrument.annotations.FlowException;
import ie.bitstep.mango.instrument.annotations.OnFlowCompleted;
import ie.bitstep.mango.instrument.annotations.OnFlowFailure;
import ie.bitstep.mango.instrument.annotations.RequiredAttributes;
import ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry;
import ie.bitstep.mango.instrument.model.FlowEvent;
import ie.bitstep.mango.instrument.spring.annotations.FlowSink;

import static org.assertj.core.api.Assertions.assertThat;

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
		static final AtomicInteger completed = new AtomicInteger();

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
		public void onFailureThrowableRoot(@FlowException(FlowException.Source.ROOT) Throwable throwable) {}

		@OnFlowCompleted
		public void onCompleted(FlowEvent event) {
			completed.incrementAndGet();
		}
	}

	@FlowSink
	static class NullTargetClassAwareSink implements TargetClassAware {
		@Override
		public Class<?> getTargetClass() {
			return null;
		}

		@OnFlowFailure
		public void onFailure(FlowEvent event) {}
	}

	static class PlainBean {}

	@FlowSink
	static class NoLifecycleSink {
		public void helperOnly() {}
	}

	@FlowSink
	static class FallbackOnlySink {
		static final java.util.concurrent.atomic.AtomicInteger fallbackCalls =
				new java.util.concurrent.atomic.AtomicInteger();

		@ie.bitstep.mango.instrument.annotations.OnFlowNotMatched
		public void onFallback() {
			fallbackCalls.incrementAndGet();
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
		MySinks.completed.set(0);
	}

	@Test
	void failed_lifecycle_dispatch_matches_failure_handlers_only() throws Exception {
		FlowEvent event = FlowEvent.builder().name("orders.checkout").build();
		event.eventContext().put("lifecycle", "FAILED");
		event.attributes().put("order.id", "123");
		event.attributes().put("error.code", "PAYMENT");
		event.setThrowable(new IllegalArgumentException("bad payment"));

		for (var handler : registry.handlers()) {
			handler.handle(event);
		}

		assertThat(MySinks.failureAny.get()).isEqualTo(1);
		assertThat(MySinks.failureWithAttrs.get()).isEqualTo(1);
		assertThat(MySinks.failureWithThrowableOnly.get()).isEqualTo(1);
		assertThat(MySinks.lastThrowable).isInstanceOf(IllegalArgumentException.class);
		assertThat(MySinks.completed.get()).isZero();
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

	@Test
	void success_completion_matches_completed_handlers_only() throws Exception {
		FlowEvent event = FlowEvent.builder().name("orders.checkout").build();
		event.eventContext().put("lifecycle", "COMPLETED");

		for (var handler : registry.handlers()) {
			handler.handle(event);
		}

		assertThat(MySinks.completed.get()).isEqualTo(1);
		assertThat(MySinks.failureAny.get()).isZero();
		assertThat(MySinks.failureWithThrowableOnly.get()).isZero();
	}

	@Test
	void target_class_aware_sink_with_null_target_class_is_still_registered() {
		FlowHandlerRegistry localRegistry = new FlowHandlerRegistry();
		FlowSinkScanner localScanner = new FlowSinkScanner(localRegistry);

		Object bean = new NullTargetClassAwareSink();
		Object processed = localScanner.postProcessAfterInitialization(bean, "nullTargetClassAwareSink");

		assertThat(processed).isSameAs(bean);
		assertThat(localRegistry.handlers()).hasSize(1);
	}

	@Test
	void fallback_only_sink_is_registered_when_handlers_empty_but_fallbacks_present() {
		FlowHandlerRegistry localRegistry = new FlowHandlerRegistry();
		FlowSinkScanner localScanner = new FlowSinkScanner(localRegistry);
		FallbackOnlySink.fallbackCalls.set(0);

		Object bean = new FallbackOnlySink();
		Object processed = localScanner.postProcessAfterInitialization(bean, "fallbackOnlySink");

		assertThat(processed).isSameAs(bean);
		assertThat(localRegistry.handlers()).hasSize(1);
	}

	@Test
	void returns_original_bean_for_non_sink_and_no_lifecycle_sink() {
		FlowHandlerRegistry localRegistry = new FlowHandlerRegistry();
		FlowSinkScanner localScanner = new FlowSinkScanner(localRegistry);
		PlainBean plainBean = new PlainBean();
		NoLifecycleSink noLifecycleSink = new NoLifecycleSink();

		Object plainProcessed = localScanner.postProcessAfterInitialization(plainBean, "plainBean");
		Object noLifecycleProcessed = localScanner.postProcessAfterInitialization(noLifecycleSink, "noLifecycleSink");

		assertThat(plainProcessed).isSameAs(plainBean);
		assertThat(noLifecycleProcessed).isSameAs(noLifecycleSink);
		assertThat(localRegistry.handlers()).isEmpty();
	}
}
