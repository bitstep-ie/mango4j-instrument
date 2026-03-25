package ie.bitstep.mango.instrument.spring.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ie.bitstep.mango.instrument.annotations.Flow;
import ie.bitstep.mango.instrument.annotations.Kind;
import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.core.processor.FlowMeta;
import ie.bitstep.mango.instrument.core.processor.FlowProcessor;
import ie.bitstep.mango.instrument.model.FlowEvent;
import io.opentelemetry.api.trace.SpanKind;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Unit tests for {@link FlowWebInterceptor}.
 *
 * <p>Uses {@link RecordingProcessor} (a hand-rolled stub that mirrors the style used in
 * {@code FlowAspectUnitTest}) and Spring's {@code MockHttpServletRequest/Response} to
 * exercise the interceptor in isolation — no Spring application context required.
 */
class FlowWebInterceptorTest {

    private final TrackingSupport support = new TrackingSupport();
    private final RecordingProcessor processor = new RecordingProcessor(support);
    private final FlowWebInterceptor interceptor = new FlowWebInterceptor(processor, support);

    @AfterEach
    void cleanup() {
        support.cleanupThreadLocals();
    }

    // -------------------------------------------------------------------------
    // preHandle — @Flow handler
    // -------------------------------------------------------------------------

    @Test
    void preHandle_starts_flow_for_annotated_handler_method() throws Exception {
        MockHttpServletRequest request = requestFor("GET", "/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = handlerMethod("getOrders");

        boolean proceed = interceptor.preHandle(request, response, handler);

        assertThat(proceed).isTrue();
        assertThat(processor.started).hasSize(1);
        RecordedCall started = processor.started.get(0);
        assertThat(started.name()).isEqualTo("orders.get");
        assertThat(support.currentContext()).isNotNull();
        assertThat(support.currentContext().name()).isEqualTo("orders.get");
    }

    @Test
    void preHandle_stores_flow_name_as_request_attribute() throws Exception {
        MockHttpServletRequest request = requestFor("POST", "/checkout");
        HandlerMethod handler = handlerMethod("checkout");

        interceptor.preHandle(request, new MockHttpServletResponse(), handler);

        String stored = (String) request.getAttribute(FlowWebInterceptor.class.getName() + ".flowName");
        assertThat(stored).isEqualTo("checkout.flow");
    }

    @Test
    void preHandle_passes_http_context_to_started_flow() throws Exception {
        MockHttpServletRequest request = requestFor("GET", "/items/42");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/items/{id}");
        HandlerMethod handler = handlerMethod("getOrders");

        interceptor.preHandle(request, new MockHttpServletResponse(), handler);

        assertThat(processor.started).hasSize(1);
        Map<String, Object> ctx = processor.started.get(0).context();
        @SuppressWarnings("unchecked")
        Map<String, Object> http = (Map<String, Object>) ctx.get(FlowWebInterceptor.CTX_HTTP_KEY);
        assertThat(http).isNotNull();
        assertThat(http).containsEntry(FlowWebInterceptor.CTX_HTTP_METHOD, "GET");
        assertThat(http).containsEntry(FlowWebInterceptor.CTX_HTTP_URI, "/items/42");
        assertThat(http).containsEntry(FlowWebInterceptor.CTX_HTTP_MAPPING, "/items/{id}");
    }

    @Test
    void preHandle_falls_back_to_uri_when_mapping_attribute_absent() throws Exception {
        MockHttpServletRequest request = requestFor("GET", "/fallback");
        // no BEST_MATCHING_PATTERN_ATTRIBUTE set
        HandlerMethod handler = handlerMethod("getOrders");

        interceptor.preHandle(request, new MockHttpServletResponse(), handler);

        @SuppressWarnings("unchecked")
        Map<String, Object> http = (Map<String, Object>)
                processor.started.get(0).context().get(FlowWebInterceptor.CTX_HTTP_KEY);
        assertThat(http).containsEntry(FlowWebInterceptor.CTX_HTTP_MAPPING, "/fallback");
    }

    @Test
    void preHandle_applies_server_kind_by_default() throws Exception {
        HandlerMethod handler = handlerMethod("getOrders");

        interceptor.preHandle(requestFor("GET", "/x"), new MockHttpServletResponse(), handler);

        assertThat(processor.started.get(0).meta().kind()).isEqualTo(SpanKind.SERVER.name());
    }

    @Test
    void preHandle_applies_explicit_kind_from_annotation() throws Exception {
        HandlerMethod handler = handlerMethod("clientKindEndpoint");

        interceptor.preHandle(requestFor("GET", "/x"), new MockHttpServletResponse(), handler);

        assertThat(processor.started.get(0).meta().kind()).isEqualTo(SpanKind.CLIENT.name());
    }

    @Test
    void preHandle_cleans_up_stale_thread_state_before_starting() throws Exception {
        // Simulate a leftover flow from a previous (leaked) request on the same thread
        FlowEvent stale = FlowEvent.builder().name("stale").build();
        support.push(stale);
        assertThat(support.hasActiveFlow()).isTrue();

        interceptor.preHandle(requestFor("GET", "/orders"), new MockHttpServletResponse(), handlerMethod("getOrders"));

        // cleanup is called via the interceptor's preHandle
        assertThat(support.cleanupCalls).isGreaterThanOrEqualTo(1);
    }

    @Test
    void preHandle_passes_through_for_non_annotated_handler() throws Exception {
        MockHttpServletRequest request = requestFor("GET", "/health");
        HandlerMethod handler = handlerMethod("healthCheck");

        boolean proceed = interceptor.preHandle(request, new MockHttpServletResponse(), handler);

        assertThat(proceed).isTrue();
        assertThat(processor.started).isEmpty();
        assertThat(request.getAttribute(FlowWebInterceptor.class.getName() + ".flowName")).isNull();
    }

    @Test
    void preHandle_passes_through_for_non_handler_method_objects() throws Exception {
        MockHttpServletRequest request = requestFor("GET", "/static.html");

        // passing a random Object (e.g., a ResourceHttpRequestHandler string) — not a HandlerMethod
        boolean proceed = interceptor.preHandle(request, new MockHttpServletResponse(), "someResourceHandler");

        assertThat(proceed).isTrue();
        assertThat(processor.started).isEmpty();
    }

    // -------------------------------------------------------------------------
    // afterCompletion — success
    // -------------------------------------------------------------------------

    @Test
    void afterCompletion_completes_flow_on_2xx_response() throws Exception {
        MockHttpServletRequest request = requestFor("GET", "/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        HandlerMethod handler = handlerMethod("getOrders");

        interceptor.preHandle(request, response, handler);
        interceptor.afterCompletion(request, response, handler, null);

        assertThat(processor.completed).hasSize(1);
        assertThat(processor.failed).isEmpty();
        RecordedCall completed = processor.completed.get(0);
        assertThat(completed.name()).isEqualTo("orders.get");
        assertThat(completed.meta().statusCode()).isEqualTo("OK");
        Map<String, Object> ctx = completed.context();
        assertThat(ctx).containsKey(FlowWebInterceptor.CTX_HTTP_STATUS);
        assertThat(ctx.get(FlowWebInterceptor.CTX_HTTP_STATUS)).isEqualTo("200");
    }

    @Test
    void afterCompletion_cleans_up_thread_locals_on_success() throws Exception {
        MockHttpServletRequest request = requestFor("GET", "/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        HandlerMethod handler = handlerMethod("getOrders");

        interceptor.preHandle(request, response, handler);
        int cleanupsBefore = support.cleanupCalls;
        interceptor.afterCompletion(request, response, handler, null);

        assertThat(support.cleanupCalls).isGreaterThan(cleanupsBefore);
        assertThat(support.hasActiveFlow()).isFalse();
    }

    @Test
    void afterCompletion_removes_request_attribute_after_completing() throws Exception {
        MockHttpServletRequest request = requestFor("GET", "/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        HandlerMethod handler = handlerMethod("getOrders");

        interceptor.preHandle(request, response, handler);
        interceptor.afterCompletion(request, response, handler, null);

        assertThat(request.getAttribute(FlowWebInterceptor.class.getName() + ".flowName")).isNull();
    }

    // -------------------------------------------------------------------------
    // afterCompletion — 4xx / 5xx
    // -------------------------------------------------------------------------

    @Test
    void afterCompletion_fails_flow_on_4xx_response() throws Exception {
        MockHttpServletRequest request = requestFor("GET", "/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(404);
        HandlerMethod handler = handlerMethod("getOrders");

        interceptor.preHandle(request, response, handler);
        interceptor.afterCompletion(request, response, handler, null);

        assertThat(processor.failed).hasSize(1);
        assertThat(processor.completed).isEmpty();
        RecordedCall failed = processor.failed.get(0);
        assertThat(failed.meta().statusCode()).isEqualTo("ERROR");
        assertThat(failed.meta().statusMessage()).isEqualTo("404");
    }

    @Test
    void afterCompletion_fails_flow_on_5xx_response() throws Exception {
        MockHttpServletRequest request = requestFor("GET", "/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);
        HandlerMethod handler = handlerMethod("getOrders");

        interceptor.preHandle(request, response, handler);
        interceptor.afterCompletion(request, response, handler, null);

        assertThat(processor.failed).hasSize(1);
        RecordedCall failed = processor.failed.get(0);
        assertThat(failed.meta().statusCode()).isEqualTo("ERROR");
    }

    // -------------------------------------------------------------------------
    // afterCompletion — exception propagated from handler
    // -------------------------------------------------------------------------

    @Test
    void afterCompletion_fails_flow_when_exception_is_present() throws Exception {
        MockHttpServletRequest request = requestFor("GET", "/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        HandlerMethod handler = handlerMethod("getOrders");
        Exception thrown = new RuntimeException("handler exploded");

        interceptor.preHandle(request, response, handler);
        interceptor.afterCompletion(request, response, handler, thrown);

        assertThat(processor.failed).hasSize(1);
        assertThat(processor.completed).isEmpty();
        RecordedCall failed = processor.failed.get(0);
        assertThat(failed.error()).isSameAs(thrown);
        assertThat(failed.meta().statusCode()).isEqualTo("ERROR");
        assertThat(failed.meta().statusMessage()).isEqualTo("handler exploded");
    }

    @Test
    void afterCompletion_cleans_up_thread_locals_on_failure() throws Exception {
        MockHttpServletRequest request = requestFor("GET", "/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);
        HandlerMethod handler = handlerMethod("getOrders");

        interceptor.preHandle(request, response, handler);
        interceptor.afterCompletion(request, response, handler, null);

        assertThat(support.hasActiveFlow()).isFalse();
    }

    // -------------------------------------------------------------------------
    // afterCompletion — no-flow-name guard
    // -------------------------------------------------------------------------

    @Test
    void afterCompletion_is_noop_when_no_flow_name_attribute() throws Exception {
        MockHttpServletRequest request = requestFor("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        HandlerMethod handler = handlerMethod("healthCheck");

        // preHandle sets no attribute because the handler is not @Flow
        interceptor.preHandle(request, response, handler);
        interceptor.afterCompletion(request, response, handler, null);

        assertThat(processor.completed).isEmpty();
        assertThat(processor.failed).isEmpty();
    }

    @Test
    void afterCompletion_fails_flow_on_exactly_400_response() throws Exception {
        MockHttpServletRequest request = requestFor("GET", "/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(400);
        HandlerMethod handler = handlerMethod("getOrders");

        interceptor.preHandle(request, response, handler);
        interceptor.afterCompletion(request, response, handler, null);

        assertThat(processor.failed).hasSize(1);
        assertThat(processor.completed).isEmpty();
        assertThat(processor.failed.get(0).meta().statusCode()).isEqualTo("ERROR");
    }

    @Test
    void afterCompletion_cleans_up_thread_locals_even_when_processor_throws() throws Exception {
        // Attach a Logback ListAppender to the interceptor's logger so we can assert
        // that log.error() is called — this kills the VoidMethodCallMutator on that line.
        ch.qos.logback.classic.Logger interceptorLogger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(FlowWebInterceptor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        interceptorLogger.addAppender(appender);

        try {
            ThrowingProcessor throwingProcessor = new ThrowingProcessor(support);
            FlowWebInterceptor throwingInterceptor = new FlowWebInterceptor(throwingProcessor, support);

            MockHttpServletRequest request = requestFor("GET", "/orders");
            MockHttpServletResponse response = new MockHttpServletResponse();
            response.setStatus(200);
            HandlerMethod handler = handlerMethod("getOrders");

            throwingInterceptor.preHandle(request, response, handler);
            int cleanupsBefore = support.cleanupCalls;

            // must not propagate the exception — the interceptor swallows it
            throwingInterceptor.afterCompletion(request, response, handler, null);

            assertThat(support.cleanupCalls).isGreaterThan(cleanupsBefore);
            assertThat(support.hasActiveFlow()).isFalse();
            assertThat(request.getAttribute(FlowWebInterceptor.class.getName() + ".flowName")).isNull();
            assertThat(appender.list)
                    .anyMatch(e -> e.getLevel() == Level.ERROR
                            && e.getFormattedMessage().contains("orders.get"));
        } finally {
            interceptorLogger.detachAppender(appender);
        }
    }

    // -------------------------------------------------------------------------
    // Flow name resolution
    // -------------------------------------------------------------------------

    @Test
    void preHandle_resolves_flow_name_from_annotation_name_attribute() throws Exception {
        HandlerMethod handler = handlerMethod("getOrders");
        interceptor.preHandle(requestFor("GET", "/x"), new MockHttpServletResponse(), handler);
        assertThat(processor.started.get(0).name()).isEqualTo("orders.get");
    }

    @Test
    void preHandle_resolves_flow_name_from_annotation_value_attribute() throws Exception {
        HandlerMethod handler = handlerMethod("checkout");
        interceptor.preHandle(requestFor("POST", "/x"), new MockHttpServletResponse(), handler);
        assertThat(processor.started.get(0).name()).isEqualTo("checkout.flow");
    }

    @Test
    void preHandle_falls_back_to_classname_method_when_name_and_value_blank() throws Exception {
        HandlerMethod handler = handlerMethod("implicitName");
        interceptor.preHandle(requestFor("GET", "/x"), new MockHttpServletResponse(), handler);
        assertThat(processor.started.get(0).name()).isEqualTo("SampleController.implicitName");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static MockHttpServletRequest requestFor(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(uri);
        return request;
    }

    private static HandlerMethod handlerMethod(String methodName) throws Exception {
        SampleController controller = new SampleController();
        for (Method method : SampleController.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return new HandlerMethod(controller, method);
            }
        }
        throw new NoSuchMethodException("No method '" + methodName + "' in SampleController");
    }

    // -------------------------------------------------------------------------
    // Supporting types
    // -------------------------------------------------------------------------

    /** Sample controller whose methods exercise different annotation combinations. */
    static class SampleController {

        @Flow(name = "orders.get")
        void getOrders() {}

        @Flow("checkout.flow")
        void checkout() {}

        @Flow
        void implicitName() {}

        @Flow(name = "client.endpoint")
        @Kind(io.opentelemetry.api.trace.SpanKind.CLIENT)
        void clientKindEndpoint() {}

        /** No @Flow annotation — must pass through untouched. */
        void healthCheck() {}
    }

    static class RecordingProcessor implements FlowProcessor {
        private final FlowProcessorSupport support;
        final List<RecordedCall> started = new ArrayList<>();
        final List<RecordedCall> completed = new ArrayList<>();
        final List<RecordedCall> failed = new ArrayList<>();

        RecordingProcessor(FlowProcessorSupport support) {
            this.support = support;
        }

        @Override
        public void onFlowStarted(
                String name, Map<String, Object> attrs, Map<String, Object> ctx, FlowMeta meta) {
            started.add(new RecordedCall(name, attrs, ctx, meta, null));
            FlowEvent event = FlowEvent.builder().name(name).build();
            event.attributes().map().putAll(attrs);
            event.eventContext().putAll(ctx);
            support.push(event);
        }

        @Override
        public void onFlowCompleted(
                String name, Map<String, Object> attrs, Map<String, Object> ctx, FlowMeta meta) {
            completed.add(new RecordedCall(name, attrs, ctx, meta, null));
            FlowEvent current = support.currentContext();
            if (current != null) support.pop(current);
        }

        @Override
        public void onFlowFailed(
                String name, Throwable error, Map<String, Object> attrs, Map<String, Object> ctx, FlowMeta meta) {
            failed.add(new RecordedCall(name, attrs, ctx, meta, error));
            FlowEvent current = support.currentContext();
            if (current != null) support.pop(current);
        }
    }

    static class ThrowingProcessor implements FlowProcessor {
        private final FlowProcessorSupport support;

        ThrowingProcessor(FlowProcessorSupport support) {
            this.support = support;
        }

        @Override
        public void onFlowStarted(
                String name, Map<String, Object> attrs, Map<String, Object> ctx, FlowMeta meta) {
            FlowEvent event = FlowEvent.builder().name(name).build();
            support.push(event);
        }

        @Override
        public void onFlowCompleted(
                String name, Map<String, Object> attrs, Map<String, Object> ctx, FlowMeta meta) {
            throw new RuntimeException("processor exploded");
        }

        @Override
        public void onFlowFailed(
                String name, Throwable error, Map<String, Object> attrs, Map<String, Object> ctx, FlowMeta meta) {
            throw new RuntimeException("processor exploded");
        }
    }

    static class TrackingSupport extends FlowProcessorSupport {
        int cleanupCalls;

        @Override
        public void cleanupThreadLocals() {
            cleanupCalls++;
            super.cleanupThreadLocals();
        }
    }

    record RecordedCall(
            String name,
            Map<String, Object> attrs,
            Map<String, Object> context,
            FlowMeta meta,
            Throwable error) {}
}
