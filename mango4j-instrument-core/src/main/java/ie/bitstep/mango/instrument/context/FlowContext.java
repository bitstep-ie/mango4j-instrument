package ie.bitstep.mango.instrument.context;

import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.model.FlowEvent;
import java.util.Map;
import java.util.Objects;

public class FlowContext {
    private final FlowProcessorSupport support;

    public FlowContext(FlowProcessorSupport support) {
        this.support = Objects.requireNonNull(support, "support");
    }

    public <T> T put(String key, T value) {
        return putAttr(key, value);
    }

    public void putAll(Map<String, ?> map) {
        putAllAttrs(map);
    }

    public <T> T putAttr(String key, T value) {
        if (!support.isEnabled() || key == null || key.isBlank()) {
            return value;
        }
        FlowEvent context = support.currentContext();
        if (context != null) {
            context.attributes().put(key, value);
        }
        return value;
    }

    public void putAllAttrs(Map<String, ?> map) {
        if (!support.isEnabled() || map == null || map.isEmpty()) {
            return;
        }
        FlowEvent context = support.currentContext();
        if (context == null) {
            return;
        }
        map.forEach((key, value) -> {
            if (key != null && !key.isBlank()) {
                context.attributes().put(key, value);
            }
        });
    }

    public <T> T putContext(String key, T value) {
        if (!support.isEnabled() || key == null || key.isBlank()) {
            return value;
        }
        FlowEvent context = support.currentContext();
        if (context != null) {
            context.eventContext().put(key, value);
        }
        return value;
    }

    public void putAllContext(Map<String, ?> map) {
        if (!support.isEnabled() || map == null || map.isEmpty()) {
            return;
        }
        FlowEvent context = support.currentContext();
        if (context == null) {
            return;
        }
        map.forEach((key, value) -> {
            if (key != null && !key.isBlank()) {
                context.eventContext().put(key, value);
            }
        });
    }
}
