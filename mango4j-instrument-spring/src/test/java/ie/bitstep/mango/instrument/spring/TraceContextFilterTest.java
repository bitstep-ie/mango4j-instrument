package ie.bitstep.mango.instrument.spring;

import static org.assertj.core.api.Assertions.assertThat;

import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.spring.web.TraceContextFilter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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
}
