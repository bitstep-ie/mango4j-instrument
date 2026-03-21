package ie.bitstep.mango.instrument.spring.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ie.bitstep.mango.instrument.annotations.Flow;
import ie.bitstep.mango.instrument.annotations.OrphanAlert;
import ie.bitstep.mango.instrument.annotations.PushAttribute;
import ie.bitstep.mango.instrument.annotations.PushContextValue;
import ie.bitstep.mango.instrument.annotations.Step;
import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.core.processor.FlowMeta;
import ie.bitstep.mango.instrument.core.processor.FlowProcessor;
import ie.bitstep.mango.instrument.model.FlowEvent;
import ie.bitstep.mango.instrument.model.OEvent;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

class FlowAspectUnitTest {

    /**
     * This test demonstrates the intended semantic distinction:
     * a nested flow can complete successfully inside an already-active parent flow, and the parent context remains active.
     * See FlowAspectStepBehaviorTest for the converse case where a nested step fails but the root flow recovers and completes.
     */
    @Test
    void nested_flow_keeps_parent_context_and_records_meta() throws Throwable {
        FlowProcessorSupport support = new FlowProcessorSupport();
        RecordingProcessor processor = new RecordingProcessor(support);
        FlowAspect aspect = new FlowAspect(processor, support);
        FlowEvent parent = FlowEvent.builder().name("parent").build();
        support.push(parent);
        Method method = Samples.class.getDeclaredMethod("nestedFlow", String.class, int.class);

        Object result = aspect.aroundFlow(joinPoint(method, new Object[] {"alice", 2}, () -> "ok"));

        assertThat(result).isEqualTo("ok");
        assertThat(processor.started).hasSize(1);
        assertThat(processor.completed).hasSize(1);
        assertThat(processor.started.get(0).meta.kind()).isNull();
        assertThat(processor.completed.get(0).meta.statusCode()).isEqualTo("OK");
        assertThat(processor.completed.get(0).meta.statusMessage()).isNull();
        assertThat(processor.completed.get(0).attrs).containsEntry("user.id", "alice");
        assertThat(processor.completed.get(0).context).containsEntry("cart.size", 2);
        assertThat(support.currentContext()).isSameAs(parent);
    }

    @Test
    void orphan_step_failure_preserves_existing_error_attribute() throws Throwable {
        FlowProcessorSupport support = new FlowProcessorSupport();
        RecordingProcessor processor = new RecordingProcessor(support);
        FlowAspect aspect = new FlowAspect(processor, support);
        Method method = Samples.class.getDeclaredMethod("orphanWithErrorAttr", String.class);

        assertThatThrownBy(() -> aspect.aroundStep(joinPoint(method, new Object[] {"manual"}, () -> {
                    throw new IllegalStateException("boom");
                })))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(processor.failed).hasSize(1);
        RecordedCall failed = processor.failed.get(0);
        assertThat(failed.attrs).containsEntry("error", "manual");
        assertThat(failed.meta.statusCode()).isEqualTo("ERROR");
        assertThat(failed.meta.statusMessage()).isEqualTo("boom");
        assertThat(support.currentContext()).isNull();
    }

    @Test
    void in_flow_step_failure_records_internal_kind_and_error_update() throws Throwable {
        FlowProcessorSupport support = new FlowProcessorSupport();
        RecordingProcessor processor = new RecordingProcessor(support);
        FlowAspect aspect = new FlowAspect(processor, support);
        FlowEvent active = FlowEvent.builder().name("root").build();
        support.push(active);
        Method method = Samples.class.getDeclaredMethod("internalStep", String.class, String.class);

        assertThatThrownBy(() -> aspect.aroundStep(joinPoint(method, new Object[] {"SKU-1", "ie"}, () -> {
                    throw new IllegalArgumentException("bad");
                })))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bad");

        assertThat(active.attributes().map()).containsEntry("sku", "SKU-1");
        assertThat(active.eventContext()).containsEntry("country", "ie");
        assertThat(active.events()).hasSize(1);
        OEvent event = active.events().get(0);
        assertThat(event.kind()).isEqualTo("INTERNAL");
        assertThat(event.attributes().map())
                .containsEntry("sku", "SKU-1")
                .containsEntry("error", "IllegalArgumentException");
        assertThat(support.currentContext()).isSameAs(active);
    }

    @Test
    void orphan_step_uses_explicit_step_name_when_present() throws Throwable {
        FlowProcessorSupport support = new FlowProcessorSupport();
        RecordingProcessor processor = new RecordingProcessor(support);
        FlowAspect aspect = new FlowAspect(processor, support);
        Method method = Samples.class.getDeclaredMethod("namedStep");

        Object result = aspect.aroundStep(joinPoint(method, new Object[0], () -> "done"));

        assertThat(result).isEqualTo("done");
        assertThat(processor.started).singleElement().satisfies(call -> assertThat(call.name()).isEqualTo("explicit-step"));
        assertThat(processor.completed).singleElement().satisfies(call -> {
            assertThat(call.name()).isEqualTo("explicit-step");
            assertThat(call.meta.statusCode()).isEqualTo("OK");
        });
        assertThat(support.currentContext()).isNull();
    }

    private static ProceedingJoinPoint joinPoint(Method method, Object[] args, ProceedCallback callback) {
        MethodSignature signature = (MethodSignature) Proxy.newProxyInstance(
                MethodSignature.class.getClassLoader(),
                new Class<?>[] {MethodSignature.class},
                methodSignatureHandler(method));
        return (ProceedingJoinPoint) Proxy.newProxyInstance(
                ProceedingJoinPoint.class.getClassLoader(),
                new Class<?>[] {ProceedingJoinPoint.class},
                joinPointHandler(signature, args, callback));
    }

    private static InvocationHandler methodSignatureHandler(Method method) {
        return (proxy, invoked, args) -> switch (invoked.getName()) {
            case "getMethod" -> method;
            case "getReturnType" -> method.getReturnType();
            case "toShortString" -> method.getDeclaringClass().getSimpleName() + "." + method.getName() + "(..)";
            case "toLongString", "toString" -> method.toString();
            case "getName" -> method.getName();
            case "getDeclaringType" -> method.getDeclaringClass();
            case "getDeclaringTypeName" -> method.getDeclaringClass().getName();
            case "getParameterTypes" -> method.getParameterTypes();
            case "getParameterNames" -> new String[method.getParameterCount()];
            case "getExceptionTypes" -> method.getExceptionTypes();
            case "getModifiers" -> method.getModifiers();
            default -> defaultValue(invoked.getReturnType());
        };
    }

    private static InvocationHandler joinPointHandler(
            MethodSignature signature, Object[] args, ProceedCallback callback) {
        return (proxy, invoked, ignored) -> switch (invoked.getName()) {
            case "getArgs" -> args;
            case "getSignature" -> signature;
            case "toShortString" -> signature.toShortString();
            case "toLongString", "toString" -> signature.toString();
            case "getKind" -> "method-execution";
            case "proceed" -> callback.proceed();
            default -> defaultValue(invoked.getReturnType());
        };
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return 0;
    }

    @FunctionalInterface
    interface ProceedCallback {
        Object proceed() throws Throwable;
    }

    static class RecordingProcessor implements FlowProcessor {
        private final FlowProcessorSupport support;
        private final List<FlowEvent> stack = new ArrayList<>();
        final List<RecordedCall> started = new ArrayList<>();
        final List<RecordedCall> completed = new ArrayList<>();
        final List<RecordedCall> failed = new ArrayList<>();

        RecordingProcessor(FlowProcessorSupport support) {
            this.support = support;
        }

        @Override
        public void onFlowStarted(String name, Map<String, Object> extraAttrs, Map<String, Object> extraContext, FlowMeta meta) {
            started.add(new RecordedCall(name, extraAttrs, extraContext, meta, null));
            FlowEvent event = FlowEvent.builder().name(name).build();
            event.attributes().map().putAll(extraAttrs);
            event.eventContext().putAll(extraContext);
            stack.add(event);
            support.push(event);
        }

        @Override
        public void onFlowCompleted(String name, Map<String, Object> extraAttrs, Map<String, Object> extraContext, FlowMeta meta) {
            completed.add(new RecordedCall(name, extraAttrs, extraContext, meta, null));
            FlowEvent current = support.currentContext();
            if (current != null) {
                support.pop(current);
            }
            if (!stack.isEmpty()) {
                stack.remove(stack.size() - 1);
            }
        }

        @Override
        public void onFlowFailed(
                String name, Throwable error, Map<String, Object> extraAttrs, Map<String, Object> extraContext, FlowMeta meta) {
            failed.add(new RecordedCall(name, extraAttrs, extraContext, meta, error));
            FlowEvent current = support.currentContext();
            if (current != null) {
                support.pop(current);
            }
            if (!stack.isEmpty()) {
                stack.remove(stack.size() - 1);
            }
        }
    }

    record RecordedCall(String name, Map<String, Object> attrs, Map<String, Object> context, FlowMeta meta, Throwable error) {
    }

    static class Samples {
        @Flow
        String nestedFlow(@PushAttribute("user.id") String userId, @PushContextValue("cart.size") int size) {
            return "ignored";
        }

        @Step
        @OrphanAlert(OrphanAlert.Level.NONE)
        void orphanWithErrorAttr(@PushAttribute("error") String error) {
        }

        @Step
        void internalStep(@PushAttribute("sku") String sku, @PushContextValue("country") String country) {
        }

        @Step(name = "explicit-step")
        String namedStep() {
            return "ignored";
        }
    }
}
