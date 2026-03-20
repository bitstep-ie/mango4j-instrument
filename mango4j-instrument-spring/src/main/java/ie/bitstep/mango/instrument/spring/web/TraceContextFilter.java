package ie.bitstep.mango.instrument.spring.web;

import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Locale;
import org.slf4j.MDC;

public final class TraceContextFilter implements Filter {
    private final FlowProcessorSupport support;

    public TraceContextFilter(FlowProcessorSupport support) {
        this.support = support;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String previousTraceId = MDC.get("traceId");
        String previousSpanId = MDC.get("spanId");
        String previousParentSpanId = MDC.get("parentSpanId");
        String previousTracestate = MDC.get("tracestate");
        String previousTraceparent = MDC.get("traceparent");
        try {
            if (request instanceof HttpServletRequest httpRequest) {
                String traceparent = header(httpRequest, "traceparent");
                String tracestate = header(httpRequest, "tracestate");
                String b3 = header(httpRequest, "b3");
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
                    traceId = header(httpRequest, "X-B3-TraceId");
                    spanId = header(httpRequest, "X-B3-SpanId");
                    parentSpanId = header(httpRequest, "X-B3-ParentSpanId");
                }

                putIfPresent("traceparent", traceparent);
                putIfPresent("tracestate", tracestate);
                putIfPresent("traceId", traceId);
                putIfPresent("spanId", spanId);
                putIfPresent("parentSpanId", parentSpanId);
            }
            chain.doFilter(request, response);
        } finally {
            if (support != null) {
                support.cleanupThreadLocals();
            }
            restore("traceId", previousTraceId);
            restore("spanId", previousSpanId);
            restore("parentSpanId", previousParentSpanId);
            restore("tracestate", previousTracestate);
            restore("traceparent", previousTraceparent);
        }
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? null : value;
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
