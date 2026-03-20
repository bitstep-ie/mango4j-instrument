package ie.bitstep.mango.instrument.spring.aspect;

import ie.bitstep.mango.instrument.annotations.Flow;
import ie.bitstep.mango.instrument.annotations.Kind;
import ie.bitstep.mango.instrument.annotations.OrphanAlert;
import ie.bitstep.mango.instrument.annotations.Step;
import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.core.processor.FlowMeta;
import ie.bitstep.mango.instrument.core.processor.FlowProcessor;
import ie.bitstep.mango.instrument.model.FlowEvent;
import ie.bitstep.mango.instrument.model.OAttributes;
import ie.bitstep.mango.instrument.spring.processor.AttributeParamExtractor;
import ie.bitstep.mango.instrument.spring.processor.AttributeParamExtractor.AttrCtx;
import io.opentelemetry.api.trace.SpanKind;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class FlowAspect {
    private static final String ERROR = "error";
    private final FlowProcessor processor;
    private final FlowProcessorSupport support;

    public FlowAspect(FlowProcessor processor, FlowProcessorSupport support) {
        this.processor = processor;
        this.support = support;
    }

    @Around("@annotation(ie.bitstep.mango.instrument.annotations.Flow) && execution(* *(..))")
    public Object aroundFlow(ProceedingJoinPoint joinPoint) throws Throwable {
        String name = resolveFlowName(joinPoint);
        AttrCtx attrCtx = AttributeParamExtractor.extract(joinPoint);
        FlowMeta meta = buildMeta(joinPoint, null);
        boolean returnsVoid = ((MethodSignature) joinPoint.getSignature()).getReturnType() == Void.TYPE;
        boolean rootFlow = support.currentContext() == null;

        processor.onFlowStarted(name, attrCtx.attributes(), attrCtx.context(), meta);
        try {
            Object result = joinPoint.proceed();
            if (!returnsVoid) {
                FlowEvent current = support.currentContext();
                if (current != null) {
                    current.setReturnValue(result);
                }
            }
            processor.onFlowCompleted(
                    name, attrCtx.attributes(), attrCtx.context(), buildMeta(joinPoint, new StatusHint("OK", null)));
            return result;
        } catch (Throwable throwable) {
            FlowEvent current = support.currentContext();
            if (current != null) {
                current.clearReturnValue();
            }
            Map<String, Object> attrs = new LinkedHashMap<>(attrCtx.attributes());
            attrs.put(ERROR, throwable.toString());
            processor.onFlowFailed(
                    name,
                    throwable,
                    attrs,
                    attrCtx.context(),
                    buildMeta(joinPoint, new StatusHint("ERROR", throwable.getMessage())));
            throw throwable;
        } finally {
            if (rootFlow) {
                support.cleanupThreadLocals();
            }
        }
    }

    @Around("@annotation(ie.bitstep.mango.instrument.annotations.Step) && execution(* *(..))")
    public Object aroundStep(ProceedingJoinPoint joinPoint) throws Throwable {
        String name = resolveStepName(joinPoint);
        AttrCtx attrCtx = AttributeParamExtractor.extract(joinPoint);
        FlowEvent context = support.currentContext();
        if (context == null) {
            return handleOrphanStep(joinPoint, name, attrCtx);
        }
        return handleInFlowStep(joinPoint, name, attrCtx, context);
    }

    private Object handleOrphanStep(ProceedingJoinPoint joinPoint, String name, AttrCtx attrCtx) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        OrphanAlert orphanAlert = method.getAnnotation(OrphanAlert.class);
        support.logOrphanStep(name, orphanAlert != null ? orphanAlert.value() : OrphanAlert.Level.ERROR);

        try {
            processor.onFlowStarted(name, attrCtx.attributes(), attrCtx.context(), buildMeta(joinPoint, null));
            Object result = joinPoint.proceed();
            processor.onFlowCompleted(
                    name, attrCtx.attributes(), attrCtx.context(), buildMeta(joinPoint, new StatusHint("OK", null)));
            return result;
        } catch (Throwable throwable) {
            Map<String, Object> attrs = new LinkedHashMap<>(attrCtx.attributes());
            attrs.putIfAbsent(ERROR, throwable.getClass().getSimpleName());
            processor.onFlowFailed(
                    name,
                    throwable,
                    attrs,
                    attrCtx.context(),
                    buildMeta(joinPoint, new StatusHint("ERROR", throwable.getMessage())));
            throw throwable;
        } finally {
            support.cleanupThreadLocals();
        }
    }

    private Object handleInFlowStep(ProceedingJoinPoint joinPoint, String name, AttrCtx attrCtx, FlowEvent context)
            throws Throwable {
        context.attributes().map().putAll(attrCtx.attributes());
        context.eventContext().putAll(attrCtx.context());
        long startNano = System.nanoTime();
        long startEpochNanos = support.unixNanos(Instant.now());
        context.beginStepEvent(
                name,
                startEpochNanos,
                startNano,
                new OAttributes(new LinkedHashMap<>(attrCtx.attributes())),
                extractStepKind(joinPoint));
        try {
            Object result = joinPoint.proceed();
            context.endStepEvent(support.unixNanos(Instant.now()), System.nanoTime(), null);
            return result;
        } catch (Throwable throwable) {
            Map<String, Object> updates = new LinkedHashMap<>();
            updates.put(ERROR, throwable.getClass().getSimpleName());
            context.endStepEvent(support.unixNanos(Instant.now()), System.nanoTime(), updates);
            throw throwable;
        }
    }

    private static String resolveFlowName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Flow annotation = method.getAnnotation(Flow.class);
        if (annotation != null) {
            if (!annotation.name().isBlank()) {
                return annotation.name();
            }
            if (!annotation.value().isBlank()) {
                return annotation.value();
            }
        }
        return signature.toShortString();
    }

    private static String resolveStepName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Step annotation = method.getAnnotation(Step.class);
        if (annotation != null) {
            if (!annotation.name().isBlank()) {
                return annotation.name();
            }
            if (!annotation.value().isBlank()) {
                return annotation.value();
            }
        }
        return signature.toShortString();
    }

    private static String extractStepKind(ProceedingJoinPoint joinPoint) {
        Kind annotation = ((MethodSignature) joinPoint.getSignature()).getMethod().getAnnotation(Kind.class);
        return annotation != null && annotation.value() != null ? annotation.value().name() : SpanKind.INTERNAL.name();
    }

    private static FlowMeta buildMeta(ProceedingJoinPoint joinPoint, StatusHint statusHint) {
        Kind annotation = ((MethodSignature) joinPoint.getSignature()).getMethod().getAnnotation(Kind.class);
        FlowMeta.Builder builder = FlowMeta.builder();
        if (annotation != null && annotation.value() != null) {
            builder.kind(annotation.value().name());
        }
        if (statusHint != null) {
            builder.status(statusHint.code(), statusHint.message());
        }
        return builder.build();
    }

    private record StatusHint(String code, String message) {
    }
}
