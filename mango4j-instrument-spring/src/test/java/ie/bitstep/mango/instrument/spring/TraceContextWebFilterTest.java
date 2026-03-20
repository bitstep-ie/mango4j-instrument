package ie.bitstep.mango.instrument.spring;

import static org.assertj.core.api.Assertions.assertThat;

import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.spring.webflux.TraceContextWebFilter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Adapted from obsinity:
 * /home/jallen/git/obsinity/obsinity-collection-spring/src/test/java/com/obsinity/collection/spring/TraceContextWebFilterTest.java
 */
class TraceContextWebFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void extracts_b3_headers_and_restores_mdc() {
        TraceContextWebFilter filter = new TraceContextWebFilter(new FlowProcessorSupport());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test")
                        .header("b3", "4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-1-a2fb4a1d1a96d312")
                        .build());
        AtomicReference<Map<String, String>> seen = new AtomicReference<>();

        WebFilterChain chain = webExchange -> {
            seen.set(Map.copyOf(MDC.getCopyOfContextMap()));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(seen.get()).containsEntry("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(seen.get()).containsEntry("spanId", "00f067aa0ba902b7");
        assertThat(seen.get()).containsEntry("parentSpanId", "a2fb4a1d1a96d312");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
