package ie.bitstep.mango.instrument.spring.web;

import java.io.IOException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.MDC;
import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.spring.AbstractTraceContextFilter;

/**
 * Servlet {@link Filter} that extracts incoming trace context headers (W3C {@code traceparent}, B3 single, or B3 multi)
 * and populates SLF4J MDC keys ({@code traceId}, {@code spanId}, {@code parentSpanId}) for the duration of the request.
 */
public final class TraceContextFilter extends AbstractTraceContextFilter implements Filter {

	/** @param support used to clean up thread-local flow state after each request */
	public TraceContextFilter(FlowProcessorSupport support) {
		super(support);
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		String previousTraceId = MDC.get(TRACE_ID);
		String previousSpanId = MDC.get(SPAN_ID);
		String previousParentSpanId = MDC.get(PARENT_SPAN_ID);
		String previousTracestate = MDC.get(TRACESTATE);
		String previousTraceparent = MDC.get(TRACEPARENT);
		try {
			if (request instanceof HttpServletRequest httpRequest) {
				String traceparent = header(httpRequest, TRACEPARENT);
				String tracestate = header(httpRequest, TRACESTATE);
				String b3 = header(httpRequest, "b3");
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
					traceId = header(httpRequest, "X-B3-TraceId");
					spanId = header(httpRequest, "X-B3-SpanId");
					parentSpanId = header(httpRequest, "X-B3-ParentSpanId");
				}

				putIfPresent(TRACEPARENT, traceparent);
				putIfPresent(TRACESTATE, tracestate);
				putIfPresent(TRACE_ID, traceId);
				putIfPresent(SPAN_ID, spanId);
				putIfPresent(PARENT_SPAN_ID, parentSpanId);
			}
			chain.doFilter(request, response);
		} finally {
			if (support != null) {
				support.cleanupThreadLocals();
			}
			restore(TRACE_ID, previousTraceId);
			restore(SPAN_ID, previousSpanId);
			restore(PARENT_SPAN_ID, previousParentSpanId);
			restore(TRACESTATE, previousTracestate);
			restore(TRACEPARENT, previousTraceparent);
		}
	}

	private static String header(HttpServletRequest request, String name) {
		String value = request.getHeader(name);
		return value == null || value.isBlank() ? null : value;
	}
}
