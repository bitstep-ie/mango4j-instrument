package ie.bitstep.mango.instrument.core.dispatch;

import ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry;
import ie.bitstep.mango.instrument.core.sinks.FlowSinkHandler;
import ie.bitstep.mango.instrument.model.FlowEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AsyncDispatchBus implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(AsyncDispatchBus.class);

    private final FlowHandlerRegistry registry;
    private final Map<FlowSinkHandler, Worker> workers = new ConcurrentHashMap<>();

    public AsyncDispatchBus(FlowHandlerRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public void dispatch(FlowEvent event) {
        if (event == null) {
            return;
        }
        List<FlowSinkHandler> handlers = registry.handlers();
        for (FlowSinkHandler handler : handlers) {
            workers.computeIfAbsent(handler, Worker::new).offer(event);
        }
    }

    @Override
    public void close() {
        workers.values().forEach(Worker::shutdown);
        workers.clear();
    }

    private static final class Worker implements Runnable {
        private final FlowSinkHandler sink;
        private final LinkedBlockingDeque<FlowEvent> queue = new LinkedBlockingDeque<>();
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final Thread thread;

        private Worker(FlowSinkHandler sink) {
            this.sink = sink;
            this.thread = new Thread(this, "mango4j-instrument-" + sink.getClass().getSimpleName());
            this.thread.setDaemon(true);
            this.thread.start();
        }

        private void offer(FlowEvent event) {
            queue.offer(event);
        }

        private void shutdown() {
            running.set(false);
            thread.interrupt();
        }

        @Override
        public void run() {
            while (running.get()) {
                FlowEvent event = null;
                try {
                    event = queue.poll(250, TimeUnit.MILLISECONDS);
                    if (event != null) {
                        sink.handle(event);
                    }
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                } catch (Throwable throwable) {
                    Throwable root = unwrap(throwable);
                    log.warn(
                            "Flow sink {} failed to handle event {} due to {}",
                            sink.getClass().getName(),
                            event != null ? event.name() : "<none>",
                            root.getMessage(),
                            root);
                }
            }
        }

        private static Throwable unwrap(Throwable throwable) {
            Throwable current = throwable;
            while (current instanceof InvocationTargetException || current instanceof UndeclaredThrowableException) {
                Throwable next = current instanceof InvocationTargetException
                        ? ((InvocationTargetException) current).getTargetException()
                        : ((UndeclaredThrowableException) current).getUndeclaredThrowable();
                if (next == null || next == current) {
                    break;
                }
                current = next;
            }
            return current;
        }
    }
}
