package ie.bitstep.mango.instrument.spring;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ie.bitstep.mango.instrument.core.FlowProcessorSupport;

public abstract class AbstractTraceContextFilter {
	private static final Logger log = LoggerFactory.getLogger(AbstractTraceContextFilter.class);

	protected static final String TRACE_ID = "traceId";
	protected static final String SPAN_ID = "spanId";
	protected static final String PARENT_SPAN_ID = "parentSpanId";
	protected static final String TRACESTATE = "tracestate";
	protected static final String TRACEPARENT = "traceparent";

	protected final FlowProcessorSupport support;

	protected AbstractTraceContextFilter(FlowProcessorSupport support) {
		this.support = support;
	}

	protected static void putIfPresent(String key, String value) {
		if (value != null) {
			MDC.put(key, value);
		}
	}

	protected static void restore(String key, String previous) {
		if (previous == null) {
			MDC.remove(key);
		} else {
			MDC.put(key, previous);
		}
	}

	protected static String[] parseTraceparent(String traceparent) {
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

	protected static String[] parseB3Single(String b3) {
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
