package ie.bitstep.mango.instrument.spring.webflux;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.spring.AbstractTraceContextFilter;

/**
 * Reactive {@link WebFilter} that extracts incoming trace context headers (W3C {@code traceparent}, B3 single, or B3
 * multi) and populates SLF4J MDC keys ({@code traceId}, {@code spanId}, {@code parentSpanId}) for the duration of the
 * request.
 */
public final class TraceContextWebFilter extends AbstractTraceContextFilter implements WebFilter {

	/** @param support used to clean up thread-local flow state after each request completes */
	public TraceContextWebFilter(FlowProcessorSupport support) {
		super(support);
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
}
