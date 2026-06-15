package ie.bitstep.mango.instrument.spring.webflux;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import ie.bitstep.mango.instrument.core.FlowProcessorSupport;

/**
 * Reactive {@link WebFilter} that extracts incoming trace context headers (W3C {@code traceparent}, B3 single, or B3
 * multi) and populates SLF4J MDC keys ({@code traceId}, {@code spanId}, {@code parentSpanId}) for the duration of the
 * request.
 */
public final class TraceContextWebFilter implements WebFilter {
	private static final Logger log = LoggerFactory.getLogger(TraceContextWebFilter.class);

	private static final String TRACE_ID = "traceId";
	private static final String SPAN_ID = "spanId";
	private static final String PARENT_SPAN_ID = "parentSpanId";
	private static final String TRACESTATE = "tracestate";
	private static final String TRACEPARENT = "traceparent";

	private final FlowProcessorSupport support;

	/** @param support used to clean up thread-local flow state after each request completes */
	public TraceContextWebFilter(FlowProcessorSupport support) {
		this.support = support;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String previousTraceId = MDC.get(TRACE_ID);
		String previousSpanId = MDC.get(SPAN_ID);
		String previousParentSpanId = MDC.get(PARENT_SPAN_ID);
		String previousTracestate = MDC.get(TRACESTATE);
		String previousTraceparent = MDC.get(TRACEPARENT);

		HttpHeaders headers = exchange.getRequest().getHeaders();
		String traceparent = headers.getFirst(TRACEPARENT);
		String tracestate = headers.getFirst(TRACESTATE);
		String b3 = headers.getFirst("b3");
		String traceId = null;
		String spanId = null;
		String parentSpanId = null;

		if (traceparent != null) {
			String[] parsed = parseTraceparent(traceparent);
			if (parsed.length > 0) {
				traceId = parsed[0];
				spanId = parsed[1];
			}
		} else if (b3 != null) {
			String[] parsed = parseB3Single(b3);
			if (parsed.length > 0) {
				traceId = parsed[0];
				spanId = parsed[1];
				parentSpanId = parsed[2];
			}
		} else {
			traceId = headers.getFirst("X-B3-TraceId");
			spanId = headers.getFirst("X-B3-SpanId");
			parentSpanId = headers.getFirst("X-B3-ParentSpanId");
		}

		putIfPresent(TRACEPARENT, traceparent);
		putIfPresent(TRACESTATE, tracestate);
		putIfPresent(TRACE_ID, traceId);
		putIfPresent(SPAN_ID, spanId);
		putIfPresent(PARENT_SPAN_ID, parentSpanId);

		return chain.filter(exchange).doFinally(signalType -> {
			if (support != null) {
				support.cleanupThreadLocals();
			}
			restore(TRACE_ID, previousTraceId);
			restore(SPAN_ID, previousSpanId);
			restore(PARENT_SPAN_ID, previousParentSpanId);
			restore(TRACESTATE, previousTracestate);
			restore(TRACEPARENT, previousTraceparent);
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
		} catch (Exception e) {
			log.debug("Failed to parse traceparent header: {}", e.getMessage());
		}
		return new String[0];
	}

	private static String[] parseB3Single(String b3) {
		try {
			String[] parts = b3.trim().split("-", -1);
			if (parts.length >= 2) {
				return new String[] {parts[0], parts[1], parts.length >= 4 ? parts[3] : null};
			}
		} catch (Exception e) {
			log.debug("Failed to parse B3 single header: {}", e.getMessage());
		}
		return new String[0];
	}
}
