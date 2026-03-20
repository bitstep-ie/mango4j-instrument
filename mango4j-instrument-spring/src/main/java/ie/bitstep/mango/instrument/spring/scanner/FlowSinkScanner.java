package ie.bitstep.mango.instrument.spring.scanner;

import ie.bitstep.mango.instrument.annotations.FlowException;
import ie.bitstep.mango.instrument.annotations.OnAllLifecycles;
import ie.bitstep.mango.instrument.annotations.OnFlowCompleted;
import ie.bitstep.mango.instrument.annotations.OnFlowFailure;
import ie.bitstep.mango.instrument.annotations.OnFlowLifecycle;
import ie.bitstep.mango.instrument.annotations.OnFlowLifecycles;
import ie.bitstep.mango.instrument.annotations.OnFlowNotMatched;
import ie.bitstep.mango.instrument.annotations.OnFlowScope;
import ie.bitstep.mango.instrument.annotations.OnFlowScopes;
import ie.bitstep.mango.instrument.annotations.OnFlowStarted;
import ie.bitstep.mango.instrument.annotations.OnFlowSuccess;
import ie.bitstep.mango.instrument.annotations.OnOutcome;
import ie.bitstep.mango.instrument.annotations.Outcome;
import ie.bitstep.mango.instrument.annotations.PullAllAttributes;
import ie.bitstep.mango.instrument.annotations.PullAllContextValues;
import ie.bitstep.mango.instrument.annotations.PullAttribute;
import ie.bitstep.mango.instrument.annotations.PullContextValue;
import ie.bitstep.mango.instrument.annotations.RequiredAttributes;
import ie.bitstep.mango.instrument.annotations.RequiredEventContext;
import ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry;
import ie.bitstep.mango.instrument.model.FlowEvent;
import ie.bitstep.mango.instrument.spring.annotations.FlowSink;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;

public class FlowSinkScanner implements BeanPostProcessor, ApplicationContextAware {
    private static final Logger log = LoggerFactory.getLogger(FlowSinkScanner.class);

    private final FlowHandlerRegistry registry;
    private ApplicationContext applicationContext;

    public FlowSinkScanner(FlowHandlerRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetType = AopUtils.getTargetClass(bean);
        if (targetType == null) {
            targetType = bean.getClass();
        }
        if (!AnnotatedElementUtils.hasAnnotation(targetType, FlowSink.class)) {
            return bean;
        }

        CompiledSink compiledSink = compileSink(bean, targetType);
        if (compiledSink.handlers.isEmpty() && compiledSink.fallbacks.isEmpty()) {
            return bean;
        }

        registry.register(compiledSink::dispatch);
        log.info(
                "Registered FlowSink: {} (handlers={}, fallbacks={})",
                targetType.getSimpleName(),
                compiledSink.handlers.size(),
                compiledSink.fallbacks.size());
        return bean;
    }

    private CompiledSink compileSink(Object bean, Class<?> type) {
        List<String> classScopes = extractScopes(type.getAnnotations());
        Set<OnFlowLifecycle.Lifecycle> classLifecycles = extractLifecycles(type.getAnnotations());
        List<CompiledHandler> handlers = new ArrayList<>();
        List<CompiledHandler> fallbacks = new ArrayList<>();

        ReflectionUtils.doWithMethods(type, method -> {
            CompiledHandler compiledHandler = compileHandler(bean, method, classScopes, classLifecycles);
            if (compiledHandler == null) {
                return;
            }
            if (compiledHandler.fallback) {
                fallbacks.add(compiledHandler);
            } else {
                handlers.add(compiledHandler);
            }
        });

        return new CompiledSink(handlers, fallbacks);
    }

    private static List<String> extractScopes(Annotation[] annotations) {
        List<String> scopes = new ArrayList<>();
        for (Annotation annotation : annotations) {
            if (annotation instanceof OnFlowScope scope) {
                scopes.add(nullToEmpty(scope.value()));
            } else if (annotation instanceof OnFlowScopes scopesAnnotation) {
                for (OnFlowScope scope : scopesAnnotation.value()) {
                    scopes.add(nullToEmpty(scope.value()));
                }
            }
        }
        return scopes;
    }

    private static Set<OnFlowLifecycle.Lifecycle> extractLifecycles(Annotation[] annotations) {
        EnumSet<OnFlowLifecycle.Lifecycle> lifecycles = EnumSet.noneOf(OnFlowLifecycle.Lifecycle.class);
        boolean all = false;
        for (Annotation annotation : annotations) {
            if (annotation instanceof OnAllLifecycles) {
                all = true;
            } else if (annotation instanceof OnFlowLifecycle lifecycle) {
                lifecycles.add(lifecycle.value());
            } else if (annotation instanceof OnFlowLifecycles lifecyclesAnnotation) {
                for (OnFlowLifecycle lifecycle : lifecyclesAnnotation.value()) {
                    lifecycles.add(lifecycle.value());
                }
            }
        }
        return all ? EnumSet.allOf(OnFlowLifecycle.Lifecycle.class) : lifecycles;
    }

    private CompiledHandler compileHandler(
            Object bean, Method method, List<String> classScopes, Set<OnFlowLifecycle.Lifecycle> classLifecycles) {
        Method bridged = BridgeMethodResolver.findBridgedMethod(method);
        Method invocable;
        try {
            invocable = AopUtils.selectInvocableMethod(bridged, bean.getClass());
        } catch (IllegalStateException ex) {
            if (log.isDebugEnabled()) {
                log.debug("Skipping FlowSink method {} due to proxy mismatch: {}", method, ex.getMessage());
            }
            return null;
        }

        boolean started = method.isAnnotationPresent(OnFlowStarted.class);
        boolean completed = method.isAnnotationPresent(OnFlowCompleted.class);
        boolean failure = method.isAnnotationPresent(OnFlowFailure.class);
        boolean success = method.isAnnotationPresent(OnFlowSuccess.class);
        boolean fallback = method.isAnnotationPresent(OnFlowNotMatched.class);
        OnFlowLifecycle lifecycle = method.getAnnotation(OnFlowLifecycle.class);
        boolean lifecycleDeclared = started || completed || failure || success || lifecycle != null || fallback;
        if (!lifecycleDeclared) {
            return null;
        }

        EnumSet<OnFlowLifecycle.Lifecycle> lifecycles = EnumSet.noneOf(OnFlowLifecycle.Lifecycle.class);
        if (started) {
            lifecycles.add(OnFlowLifecycle.Lifecycle.STARTED);
        }
        if (completed) {
            lifecycles.add(OnFlowLifecycle.Lifecycle.COMPLETED);
            lifecycles.add(OnFlowLifecycle.Lifecycle.FAILED);
        }
        if (success) {
            lifecycles.add(OnFlowLifecycle.Lifecycle.COMPLETED);
        }
        if (failure) {
            lifecycles.add(OnFlowLifecycle.Lifecycle.FAILED);
        }
        if (lifecycle != null) {
            lifecycles.add(lifecycle.value());
        }
        if (lifecycles.isEmpty()) {
            lifecycles = EnumSet.allOf(OnFlowLifecycle.Lifecycle.class);
        }
        if (!classLifecycles.isEmpty()) {
            lifecycles.retainAll(classLifecycles);
        }

        ReflectionUtils.makeAccessible(invocable);
        Parameter[] parameters = invocable.getParameters();
        Annotation[][] parameterAnnotations = invocable.getParameterAnnotations();
        List<Function<FlowEvent, Object>> bindings = new ArrayList<>(parameters.length);
        boolean allowThrowable = failure
                || (lifecycle != null && lifecycle.value() == OnFlowLifecycle.Lifecycle.FAILED)
                || (completed
                        && method.isAnnotationPresent(OnOutcome.class)
                        && method.getAnnotation(OnOutcome.class).value() == Outcome.FAILURE);
        for (int index = 0; index < parameters.length; index++) {
            Function<FlowEvent, Object> binding = buildParamBinding(parameters[index], parameterAnnotations[index], allowThrowable);
            if (binding == null) {
                return null;
            }
            bindings.add(binding);
        }

        return new CompiledHandler(
                bean,
                invocable,
                classScopes,
                extractScopes(method.getAnnotations()),
                lifecycles,
                method.getAnnotation(OnOutcome.class),
                method.getAnnotation(RequiredAttributes.class),
                method.getAnnotation(RequiredEventContext.class),
                bindings,
                fallback,
                failure,
                completed
                        && method.isAnnotationPresent(OnOutcome.class)
                        && method.getAnnotation(OnOutcome.class).value() == Outcome.FAILURE);
    }

    private static Function<FlowEvent, Object> buildParamBinding(
            Parameter parameter, Annotation[] annotations, boolean allowThrowable) {
        Class<?> type = parameter.getType();
        if (FlowEvent.class.isAssignableFrom(type)) {
            return event -> event;
        }
        if (allowThrowable && Throwable.class.isAssignableFrom(type)) {
            boolean root = false;
            for (Annotation annotation : annotations) {
                if (annotation instanceof FlowException flowException && flowException.value() == FlowException.Source.ROOT) {
                    root = true;
                }
            }
            final boolean rootCause = root;
            return event -> chooseThrowable(event, rootCause);
        }
        for (Annotation annotation : annotations) {
            if (annotation instanceof PullAllAttributes) {
                return event -> event.attributes().map();
            }
            if (annotation instanceof PullAllContextValues) {
                return FlowEvent::eventContext;
            }
            if (annotation instanceof PullAttribute pullAttribute) {
                return event -> coerce(event.attributes().map().get(pullAttribute.value()), type);
            }
            if (annotation instanceof PullContextValue pullContextValue) {
                return event -> coerce(event.eventContext().get(pullContextValue.value()), type);
            }
        }
        return null;
    }

    private record CompiledSink(List<CompiledHandler> handlers, List<CompiledHandler> fallbacks) {
        void dispatch(FlowEvent event) throws Exception {
            boolean anyMatched = false;
            boolean failed = "FAILED".equals(String.valueOf(event.eventContext().get("lifecycle")));
            boolean failureMatched = false;

            if (failed) {
                for (CompiledHandler handler : handlers) {
                    if (handler.flowFailure && handler.matches(event)) {
                        handler.invoke(event);
                        anyMatched = true;
                        failureMatched = true;
                    }
                }
            }

            for (CompiledHandler handler : handlers) {
                if (failed && handler.flowFailure) {
                    continue;
                }
                if (failed && handler.failureFinish && failureMatched) {
                    continue;
                }
                if (handler.matches(event)) {
                    handler.invoke(event);
                    anyMatched = true;
                }
            }

            if (!anyMatched) {
                for (CompiledHandler fallback : fallbacks) {
                    fallback.invoke(event);
                }
            }
        }
    }

    private static final class CompiledHandler {
        private final Object bean;
        private final Method method;
        private final List<String> classScopes;
        private final List<String> methodScopes;
        private final EnumSet<OnFlowLifecycle.Lifecycle> lifecycles;
        private final OnOutcome onOutcome;
        private final RequiredAttributes requiredAttributes;
        private final RequiredEventContext requiredEventContext;
        private final List<Function<FlowEvent, Object>> bindings;
        private final boolean fallback;
        private final boolean flowFailure;
        private final boolean failureFinish;

        private CompiledHandler(
                Object bean,
                Method method,
                List<String> classScopes,
                List<String> methodScopes,
                EnumSet<OnFlowLifecycle.Lifecycle> lifecycles,
                OnOutcome onOutcome,
                RequiredAttributes requiredAttributes,
                RequiredEventContext requiredEventContext,
                List<Function<FlowEvent, Object>> bindings,
                boolean fallback,
                boolean flowFailure,
                boolean failureFinish) {
            this.bean = bean;
            this.method = method;
            this.classScopes = classScopes;
            this.methodScopes = methodScopes;
            this.lifecycles = lifecycles;
            this.onOutcome = onOutcome;
            this.requiredAttributes = requiredAttributes;
            this.requiredEventContext = requiredEventContext;
            this.bindings = bindings;
            this.fallback = fallback;
            this.flowFailure = flowFailure;
            this.failureFinish = failureFinish;
        }

        private boolean matches(FlowEvent event) {
            String lifecycle = String.valueOf(event.eventContext().get("lifecycle"));
            OnFlowLifecycle.Lifecycle resolved = "STARTED".equals(lifecycle)
                    ? OnFlowLifecycle.Lifecycle.STARTED
                    : "FAILED".equals(lifecycle)
                            ? OnFlowLifecycle.Lifecycle.FAILED
                            : OnFlowLifecycle.Lifecycle.COMPLETED;
            if (!lifecycles.contains(resolved)) {
                return false;
            }
            if (onOutcome != null) {
                if (onOutcome.value() == Outcome.SUCCESS && resolved != OnFlowLifecycle.Lifecycle.COMPLETED) {
                    return false;
                }
                if (onOutcome.value() == Outcome.FAILURE && resolved != OnFlowLifecycle.Lifecycle.FAILED) {
                    return false;
                }
            }
            String name = event.name();
            if (!scopeMatches(classScopes, name) || !scopeMatches(methodScopes, name)) {
                return false;
            }
            if (!attributesPresent(event, requiredAttributes == null ? null : requiredAttributes.value())) {
                return false;
            }
            return contextPresent(event, requiredEventContext == null ? null : requiredEventContext.value());
        }

        private void invoke(FlowEvent event) {
            Object[] args = new Object[bindings.size()];
            for (int index = 0; index < bindings.size(); index++) {
                args[index] = bindings.get(index).apply(event);
            }
            ReflectionUtils.invokeMethod(method, bean, args);
        }
    }

    private static boolean attributesPresent(FlowEvent event, String[] required) {
        if (required == null || required.length == 0) {
            return true;
        }
        Map<String, Object> attributes = event.attributes().map();
        for (String key : required) {
            if (key == null || key.isBlank() || !attributes.containsKey(key)) {
                return false;
            }
        }
        return true;
    }

    private static boolean contextPresent(FlowEvent event, String[] required) {
        if (required == null || required.length == 0) {
            return true;
        }
        Map<String, Object> context = event.eventContext();
        for (String key : required) {
            if (key == null || key.isBlank() || !context.containsKey(key)) {
                return false;
            }
        }
        return true;
    }

    private static boolean scopeMatches(List<String> scopes, String name) {
        if (scopes == null || scopes.isEmpty()) {
            return true;
        }
        String candidate = name == null ? "" : name;
        for (String scope : scopes) {
            if (scope == null) {
                continue;
            }
            String trimmed = scope.trim();
            if (trimmed.isEmpty()) {
                return true;
            }
            if (trimmed.endsWith(".")) {
                if (candidate.startsWith(trimmed)) {
                    return true;
                }
            } else {
                if (candidate.equals(trimmed) || candidate.startsWith(trimmed + ".")) {
                    return true;
                }
                String cursor = candidate;
                while (true) {
                    if (cursor.equals(trimmed)) {
                        return true;
                    }
                    int split = cursor.lastIndexOf('.');
                    if (split < 0) {
                        break;
                    }
                    cursor = cursor.substring(0, split);
                }
            }
        }
        return false;
    }

    private static Throwable chooseThrowable(FlowEvent event, boolean rootCauseOnly) {
        Throwable throwable = event.throwable();
        if (!rootCauseOnly || throwable == null) {
            return throwable;
        }
        while (throwable.getCause() != null) {
            throwable = throwable.getCause();
        }
        return throwable;
    }

    private static Object coerce(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (targetType == String.class) {
            return String.valueOf(value);
        }
        return value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
