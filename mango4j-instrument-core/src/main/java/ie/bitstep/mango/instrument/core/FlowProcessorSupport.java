package ie.bitstep.mango.instrument.core;

import ie.bitstep.mango.instrument.annotations.OrphanAlert;
import ie.bitstep.mango.instrument.model.FlowEvent;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowProcessorSupport {
    private static final Logger log = LoggerFactory.getLogger(FlowProcessorSupport.class);
    private static final String ORPHAN_MESSAGE =
            "Step '{}' executed with no active Flow; auto-promoted to Flow.";

    private final ThreadLocal<Deque<FlowEvent>> stack = ThreadLocal.withInitial(ArrayDeque::new);
    private volatile boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public FlowEvent currentContext() {
        if (!enabled) {
            return null;
        }
        Deque<FlowEvent> deque = stack.get();
        return deque.isEmpty() ? null : deque.peekLast();
    }

    public boolean hasActiveFlow() {
        return !stack.get().isEmpty();
    }

    public void push(FlowEvent event) {
        if (enabled && event != null) {
            stack.get().addLast(event);
        }
    }

    public void pop(FlowEvent expectedTop) {
        Deque<FlowEvent> deque = stack.get();
        if (deque.isEmpty()) {
            return;
        }
        FlowEvent current = deque.peekLast();
        if (current == expectedTop) {
            deque.removeLast();
        } else {
            log.warn("Inconsistent flow nesting detected. Clearing thread state.");
            deque.clear();
        }
        if (deque.isEmpty()) {
            stack.remove();
        }
    }

    public void startNewBatch() {
        // Reserved for future batching support.
    }

    public void clearBatchAfterDispatch() {
        // Reserved for future batching support.
    }

    public void cleanupThreadLocals() {
        stack.remove();
    }

    public long unixNanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }

    public void logOrphanStep(String stepName, OrphanAlert.Level level) {
        OrphanAlert.Level resolved = level == null ? OrphanAlert.Level.ERROR : level;
        switch (resolved) {
            case NONE -> {
            }
            case TRACE -> log.trace(ORPHAN_MESSAGE, stepName);
            case DEBUG -> log.debug(ORPHAN_MESSAGE, stepName);
            case INFO -> log.info(ORPHAN_MESSAGE, stepName);
            case WARN -> log.warn(ORPHAN_MESSAGE, stepName);
            case ERROR -> log.error(ORPHAN_MESSAGE, stepName);
        }
    }
}
