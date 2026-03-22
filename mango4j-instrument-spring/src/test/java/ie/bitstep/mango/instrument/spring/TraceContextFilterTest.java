package ie.bitstep.mango.instrument.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.spring.web.TraceContextFilter;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Adapted from obsinity:
 * /home/jallen/git/obsinity/obsinity-collection-spring/src/test/java/com/obsinity/collection/spring/TraceContextFilterTest.java
 */
class TraceContextFilterTest {

    static class TrackingSupport extends FlowProcessorSupport {
        int cleanupCalls;

        @Override
        public void cleanupThreadLocals() {
            cleanupCalls++;
        }
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void extracts_w3c_trace_headers_and_restores_mdc() throws ServletException, IOException {
        TraceContextFilter filter = new TraceContextFilter(new FlowProcessorSupport());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        request.addHeader("tracestate", "vendor=value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Map<String, String>> seen = new AtomicReference<>();

        FilterChain chain = (req, res) -> seen.set(Map.copyOf(MDC.getCopyOfContextMap()));

        filter.doFilter(request, response, chain);

        assertThat(seen.get()).containsEntry("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(seen.get()).containsEntry("spanId", "00f067aa0ba902b7");
        assertThat(seen.get()).containsEntry("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        assertThat(seen.get()).containsEntry("tracestate", "vendor=value");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void extracts_b3_multi_headers_and_restores_previous_mdc() throws ServletException, IOException {
        MDC.put("traceId", "previous");
        TraceContextFilter filter = new TraceContextFilter(new FlowProcessorSupport());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-B3-TraceId", "trace-123");
        request.addHeader("X-B3-SpanId", "span-456");
        request.addHeader("X-B3-ParentSpanId", "parent-789");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Map<String, String>> seen = new AtomicReference<>();

        FilterChain chain = (req, res) -> seen.set(Map.copyOf(MDC.getCopyOfContextMap()));

        filter.doFilter(request, response, chain);

        assertThat(seen.get())
                .containsEntry("traceId", "trace-123")
                .containsEntry("spanId", "span-456")
                .containsEntry("parentSpanId", "parent-789");
        assertThat(MDC.get("traceId")).isEqualTo("previous");
        assertThat(MDC.get("spanId")).isNull();
    }

    @Test
    void ignores_blank_or_invalid_headers_and_restores_all_previous_values() throws ServletException, IOException {
        MDC.put("traceId", "previous-trace");
        MDC.put("spanId", "previous-span");
        MDC.put("parentSpanId", "previous-parent");
        MDC.put("tracestate", "previous-state");
        MDC.put("traceparent", "previous-parent-header");

        TraceContextFilter filter = new TraceContextFilter(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("traceparent", " ");
        request.addHeader("X-B3-TraceId", " ");
        request.addHeader("X-B3-SpanId", "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Map<String, String>> seen = new AtomicReference<>();

        FilterChain chain = (req, res) -> seen.set(Map.copyOf(MDC.getCopyOfContextMap()));

        filter.doFilter(request, response, chain);

        assertThat(seen.get())
                .containsEntry("traceId", "previous-trace")
                .containsEntry("spanId", "previous-span")
                .containsEntry("parentSpanId", "previous-parent")
                .containsEntry("tracestate", "previous-state")
                .containsEntry("traceparent", "previous-parent-header");
        assertThat(MDC.getCopyOfContextMap())
                .containsEntry("traceId", "previous-trace")
                .containsEntry("spanId", "previous-span")
                .containsEntry("parentSpanId", "previous-parent")
                .containsEntry("tracestate", "previous-state")
                .containsEntry("traceparent", "previous-parent-header");
    }

    @Test
    void handles_non_http_requests_without_touching_mdc() throws ServletException, IOException {
        TraceContextFilter filter = new TraceContextFilter(new FlowProcessorSupport());
        ServletRequest request = (ServletRequest) Proxy.newProxyInstance(
                ServletRequest.class.getClassLoader(),
                new Class<?>[] {ServletRequest.class},
                (proxy, method, args) -> null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> assertThat(MDC.getCopyOfContextMap()).isNull());

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void extracts_b3_single_without_parent_span() throws ServletException, IOException {
        TraceContextFilter filter = new TraceContextFilter(new FlowProcessorSupport());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("b3", "4BF92F3577B34DA6A3CE929D0E0E4736-00F067AA0BA902B7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Map<String, String>> seen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seen.set(Map.copyOf(MDC.getCopyOfContextMap())));

        assertThat(seen.get())
                .containsEntry("traceId", "4BF92F3577B34DA6A3CE929D0E0E4736")
                .containsEntry("spanId", "00F067AA0BA902B7")
                .doesNotContainKey("parentSpanId");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void extracts_b3_single_with_parent_span_from_four_part_header() throws ServletException, IOException {
        TraceContextFilter filter = new TraceContextFilter(new FlowProcessorSupport());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("b3", "trace123-span456-1-parent789");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Map<String, String>> seen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seen.set(Map.copyOf(MDC.getCopyOfContextMap())));

        assertThat(seen.get())
                .containsEntry("traceId", "trace123")
                .containsEntry("spanId", "span456")
                .containsEntry("parentSpanId", "parent789");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void ignores_malformed_traceparent_and_b3_single_headers() throws ServletException, IOException {
        TraceContextFilter filter = new TraceContextFilter(new FlowProcessorSupport());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("traceparent", "00-short-span-01");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Map<String, String>> seen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seen.set(MDC.getCopyOfContextMap()));

        assertThat(seen.get()).containsEntry("traceparent", "00-short-span-01");
        assertThat(seen.get()).doesNotContainKeys("traceId", "spanId", "parentSpanId");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void ignores_malformed_b3_single_header() throws ServletException, IOException {
        TraceContextFilter filter = new TraceContextFilter(new FlowProcessorSupport());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("b3", "traceonly");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Map<String, String>> seen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seen.set(MDC.getCopyOfContextMap()));

        assertThat(seen.get()).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void restores_previous_context_even_when_chain_throws() {
        MDC.put("traceId", "previous");
        TrackingSupport support = new TrackingSupport();
        TraceContextFilter filter = new TraceContextFilter(support);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-B3-TraceId", "trace-123");
        request.addHeader("X-B3-SpanId", "span-456");

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                    throw new ServletException("boom");
                }))
                .isInstanceOf(ServletException.class)
                .hasMessage("boom");

        assertThat(MDC.get("traceId")).isEqualTo("previous");
        assertThat(MDC.get("spanId")).isNull();
        assertThat(support.cleanupCalls).isEqualTo(1);
    }

    @Test
    void parse_helpers_return_null_for_null_inputs() throws Exception {
        Method parseTraceparent = TraceContextFilter.class.getDeclaredMethod("parseTraceparent", String.class);
        Method parseB3Single = TraceContextFilter.class.getDeclaredMethod("parseB3Single", String.class);
        parseTraceparent.setAccessible(true);
        parseB3Single.setAccessible(true);

        assertThat(parseTraceparent.invoke(null, new Object[] {null})).isNull();
        assertThat(parseB3Single.invoke(null, new Object[] {null})).isNull();
    }
}
