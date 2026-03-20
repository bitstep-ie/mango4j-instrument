package ie.bitstep.mango.instrument.spring.webflux;

import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public final class TraceContextWebFilter implements WebFilter {
    private final FlowProcessorSupport support;

    public TraceContextWebFilter(FlowProcessorSupport support) {
        this.support = support;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String previousTraceId = MDC.get("traceId");
        String previousSpanId = MDC.get("spanId");
        String previousParentSpanId = MDC.get("parentSpanId");
        String previousTracestate = MDC.get("tracestate");
        String previousTraceparent = MDC.get("traceparent");

        HttpHeaders headers = exchange.getRequest().getHeaders();
        String traceparent = headers.getFirst("traceparent");
        String tracestate = headers.getFirst("tracestate");
        String b3 = headers.getFirst("b3");
        String traceId = null;
        String spanId = null;
        String parentSpanId = null;

        if (traceparent != null) {
            String[] parsed = parseTraceparent(traceparent);
            if (parsed != null) {
                traceId = parsed[0];
                spanId = parsed[1];
            }
        } else if (b3 != null) {
            String[] parsed = parseB3Single(b3);
            if (parsed != null) {
                traceId = parsed[0];
                spanId = parsed[1];
                parentSpanId = parsed[2];
            }
        } else {
            traceId = headers.getFirst("X-B3-TraceId");
            spanId = headers.getFirst("X-B3-SpanId");
            parentSpanId = headers.getFirst("X-B3-ParentSpanId");
        }

        putIfPresent("traceparent", traceparent);
        putIfPresent("tracestate", tracestate);
        putIfPresent("traceId", traceId);
        putIfPresent("spanId", spanId);
        putIfPresent("parentSpanId", parentSpanId);

        return chain.filter(exchange).doFinally(signalType -> {
            if (support != null) {
                support.cleanupThreadLocals();
            }
            restore("traceId", previousTraceId);
            restore("spanId", previousSpanId);
            restore("parentSpanId", previousParentSpanId);
            restore("tracestate", previousTracestate);
            restore("traceparent", previousTraceparent);
        });
    }

    private static void putIfPresent(String key, String value) {
        if (value != null) {
            MDC.put(key, value);
        }
    }

    private static void restore(String key, String previous) {
        if (previous == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previous);
        }
    }

    private static String[] parseTraceparent(String traceparent) {
        try {
            String[] parts = traceparent.trim().toLowerCase(Locale.ROOT).split("-", -1);
            if (parts.length >= 4 && parts[1].length() == 32 && parts[2].length() == 16) {
                return new String[] {parts[1], parts[2], null};
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String[] parseB3Single(String b3) {
        try {
            String[] parts = b3.trim().split("-", -1);
            if (parts.length >= 2) {
                return new String[] {parts[0], parts[1], parts.length >= 4 ? parts[3] : null};
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
