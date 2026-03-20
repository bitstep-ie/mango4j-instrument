package ie.bitstep.mango.instrument.model;

import io.opentelemetry.api.trace.SpanKind;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FlowEvent {
    private final String name;
    private final Instant timestamp;
    private Instant endTimestamp;
    private final OAttributes attributes;
    private final Map<String, Object> eventContext;
    private final List<OEvent> events;
    private final Deque<OpenStep> openSteps;
    private SpanKind kind = SpanKind.INTERNAL;
    private OStatus status;
    private Throwable throwable;
    private Object returnValue;
    private String traceId;
    private String spanId;
    private String parentSpanId;

    private FlowEvent(Builder builder) {
        this.name = builder.name;
        this.timestamp = builder.timestamp == null ? Instant.now() : builder.timestamp;
        this.attributes = new OAttributes(builder.attributes);
        this.eventContext = new LinkedHashMap<>(builder.eventContext);
        this.events = new ArrayList<>();
        this.openSteps = new ArrayDeque<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String name() {
        return name;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public Instant endTimestamp() {
        return endTimestamp;
    }

    public OAttributes attributes() {
        return attributes;
    }

    public Map<String, Object> eventContext() {
        return eventContext;
    }

    public Map<String, Object> getEventContext() {
        return eventContext();
    }

    public List<OEvent> events() {
        return List.copyOf(events);
    }

    public Throwable throwable() {
        return throwable;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    public Object returnValue() {
        return returnValue;
    }

    public void setReturnValue(Object returnValue) {
        this.returnValue = returnValue;
    }

    public void clearReturnValue() {
        this.returnValue = null;
    }

    public SpanKind kind() {
        return kind;
    }

    public void kind(SpanKind kind) {
        this.kind = kind == null ? SpanKind.INTERNAL : kind;
    }

    public OStatus status() {
        return status;
    }

    public void setStatus(OStatus status) {
        this.status = status;
    }

    public String traceId() {
        return traceId;
    }

    public String spanId() {
        return spanId;
    }

    public String parentSpanId() {
        return parentSpanId;
    }

    public void trace(String traceId, String spanId, String parentSpanId) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
    }

    public void beginStepEvent(
            String name,
            long startEpochNanos,
            long startNanoTime,
            OAttributes initialAttributes,
            String kindValue) {
        OEvent event = new OEvent(name, startEpochNanos, initialAttributes, kindValue);
        events.add(event);
        openSteps.addLast(new OpenStep(event, startNanoTime));
    }

    public void endStepEvent(long endEpochNanos, long endNanoTime, Map<String, Object> updates) {
        OpenStep openStep = openSteps.pollLast();
        if (openStep == null) {
            return;
        }
        if (updates != null && !updates.isEmpty()) {
            openStep.event.attributes().map().putAll(updates);
        }
        openStep.event.setEndTimeUnixNano(endEpochNanos);
        openStep.event.setElapsedNanos(Math.max(0L, endNanoTime - openStep.startNanoTime()));
        this.endTimestamp = Instant.now();
    }

    public static final class Builder {
        private String name;
        private Instant timestamp;
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private final Map<String, Object> eventContext = new LinkedHashMap<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder putAttribute(String key, Object value) {
            this.attributes.put(key, value);
            return this;
        }

        public Builder putEventContext(String key, Object value) {
            this.eventContext.put(key, value);
            return this;
        }

        public FlowEvent build() {
            return new FlowEvent(this);
        }
    }

    private record OpenStep(OEvent event, long startNanoTime) {
    }
}
