package ie.bitstep.mango.instrument.spring.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import ie.bitstep.mango.instrument.annotations.FlowException;
import ie.bitstep.mango.instrument.annotations.OnAllLifecycles;
import ie.bitstep.mango.instrument.annotations.OnFlowCompleted;
import ie.bitstep.mango.instrument.annotations.OnFlowLifecycle;
import ie.bitstep.mango.instrument.annotations.OnFlowScope;
import ie.bitstep.mango.instrument.annotations.OnFlowScopes;
import ie.bitstep.mango.instrument.annotations.OnFlowStarted;
import ie.bitstep.mango.instrument.annotations.OnOutcome;
import ie.bitstep.mango.instrument.annotations.Outcome;
import ie.bitstep.mango.instrument.annotations.PullAllAttributes;
import ie.bitstep.mango.instrument.annotations.PullAllContextValues;
import ie.bitstep.mango.instrument.annotations.PullAttribute;
import ie.bitstep.mango.instrument.annotations.PullContextValue;
import ie.bitstep.mango.instrument.model.FlowEvent;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class FlowSinkScannerInternalsTest {

    @Test
    void helper_methods_cover_matching_and_coercion_edges() throws Exception {
        Method scopeMatches = FlowSinkScanner.class.getDeclaredMethod("scopeMatches", List.class, String.class);
        Method attributesPresent =
                FlowSinkScanner.class.getDeclaredMethod("attributesPresent", FlowEvent.class, String[].class);
        Method contextPresent =
                FlowSinkScanner.class.getDeclaredMethod("contextPresent", FlowEvent.class, String[].class);
        Method chooseThrowable =
                FlowSinkScanner.class.getDeclaredMethod("chooseThrowable", FlowEvent.class, boolean.class);
        Method coerce = FlowSinkScanner.class.getDeclaredMethod("coerce", Object.class, Class.class);
        Method nullToEmpty = FlowSinkScanner.class.getDeclaredMethod("nullToEmpty", String.class);
        Method extractScopes = FlowSinkScanner.class.getDeclaredMethod("extractScopes", Annotation[].class);
        Method extractLifecycles = FlowSinkScanner.class.getDeclaredMethod("extractLifecycles", Annotation[].class);
        Method buildParamBinding =
                FlowSinkScanner.class.getDeclaredMethod("buildParamBinding", Parameter.class, Annotation[].class, boolean.class);

        scopeMatches.setAccessible(true);
        attributesPresent.setAccessible(true);
        contextPresent.setAccessible(true);
        chooseThrowable.setAccessible(true);
        coerce.setAccessible(true);
        nullToEmpty.setAccessible(true);
        extractScopes.setAccessible(true);
        extractLifecycles.setAccessible(true);
        buildParamBinding.setAccessible(true);

        FlowEvent event = FlowEvent.builder().name("orders.checkout.pay").build();
        event.attributes().put("order.id", 123);
        event.attributes().put("sku", "SKU-1");
        event.eventContext().put("tenant.id", "bitstep");
        Throwable root = new IllegalArgumentException("root");
        event.setThrowable(new RuntimeException("wrap", root));
        Method annotated = Samples.class.getDeclaredMethod("annotated", String.class, Throwable.class, Throwable.class);
        Method flowEventMethod = Samples.class.getDeclaredMethod("flowEvent", FlowEvent.class);
        Method allAttrsMethod = Samples.class.getDeclaredMethod("allAttributes", Map.class);
        Method allContextMethod = Samples.class.getDeclaredMethod("allContext", Map.class);
        Method contextValueMethod = Samples.class.getDeclaredMethod("contextValue", String.class);
        Method noBindingMethod = Samples.class.getDeclaredMethod("noBinding", String.class);
        Parameter attrParam = annotated.getParameters()[0];
        Parameter throwableParam = annotated.getParameters()[1];
        Parameter rootThrowableParam = annotated.getParameters()[2];
        Parameter flowEventParam = flowEventMethod.getParameters()[0];
        Parameter allAttrsParam = allAttrsMethod.getParameters()[0];
        Parameter allContextParam = allContextMethod.getParameters()[0];
        Parameter contextValueParam = contextValueMethod.getParameters()[0];
        Parameter noBindingParam = noBindingMethod.getParameters()[0];
        @SuppressWarnings("unchecked")
        Function<FlowEvent, Object> attrBinding = (Function<FlowEvent, Object>) buildParamBinding.invoke(
                null, attrParam, attrParam.getAnnotations(), false);
        @SuppressWarnings("unchecked")
        Function<FlowEvent, Object> throwableBinding = (Function<FlowEvent, Object>) buildParamBinding.invoke(
                null, throwableParam, throwableParam.getAnnotations(), false);
        @SuppressWarnings("unchecked")
        Function<FlowEvent, Object> rootThrowableBinding = (Function<FlowEvent, Object>) buildParamBinding.invoke(
                null, rootThrowableParam, rootThrowableParam.getAnnotations(), true);
        @SuppressWarnings("unchecked")
        Function<FlowEvent, Object> flowEventBinding = (Function<FlowEvent, Object>) buildParamBinding.invoke(
                null, flowEventParam, flowEventParam.getAnnotations(), false);
        @SuppressWarnings("unchecked")
        Function<FlowEvent, Object> allAttrsBinding = (Function<FlowEvent, Object>) buildParamBinding.invoke(
                null, allAttrsParam, allAttrsParam.getAnnotations(), false);
        @SuppressWarnings("unchecked")
        Function<FlowEvent, Object> allContextBinding = (Function<FlowEvent, Object>) buildParamBinding.invoke(
                null, allContextParam, allContextParam.getAnnotations(), false);
        @SuppressWarnings("unchecked")
        Function<FlowEvent, Object> contextValueBinding = (Function<FlowEvent, Object>) buildParamBinding.invoke(
                null, contextValueParam, contextValueParam.getAnnotations(), false);
        @SuppressWarnings("unchecked")
        Function<FlowEvent, Object> noBinding = (Function<FlowEvent, Object>) buildParamBinding.invoke(
                null, noBindingParam, noBindingParam.getAnnotations(), false);
        @SuppressWarnings("unchecked")
        List<String> scopes = (List<String>) extractScopes.invoke(
                null, (Object) ScopedType.class.getAnnotations());
        @SuppressWarnings("unchecked")
        Set<OnFlowLifecycle.Lifecycle> lifecycles = (Set<OnFlowLifecycle.Lifecycle>) extractLifecycles.invoke(
                null, (Object) LifecycleType.class.getAnnotations());
        Method directLifecycleMethod = DirectLifecycleType.class.getDeclaredMethod("started");
        @SuppressWarnings("unchecked")
        Set<OnFlowLifecycle.Lifecycle> directLifecycles = (Set<OnFlowLifecycle.Lifecycle>) extractLifecycles.invoke(
                null, (Object) directLifecycleMethod.getAnnotations());

        assertThat(scopeMatches.invoke(null, List.of("orders.checkout"), "orders.checkout.pay")).isEqualTo(true);
        assertThat(scopeMatches.invoke(null, Arrays.asList(null, "orders.checkout."), "orders.checkout.pay"))
                .isEqualTo(true);
        assertThat(scopeMatches.invoke(null, List.of(), "orders.checkout.pay")).isEqualTo(true);
        assertThat(scopeMatches.invoke(null, List.of("orders.checkout"), "orders.refund")).isEqualTo(false);
        assertThat(scopeMatches.invoke(null, List.of("orders.checkout"), null)).isEqualTo(false);
        assertThat(attributesPresent.invoke(null, event, (Object) new String[] {null})).isEqualTo(false);
        assertThat(attributesPresent.invoke(null, event, (Object) null)).isEqualTo(true);
        assertThat(contextPresent.invoke(null, event, (Object) new String[] {" "})).isEqualTo(false);
        assertThat(contextPresent.invoke(null, event, (Object) null)).isEqualTo(true);
        assertThat(chooseThrowable.invoke(null, event, true)).isSameAs(root);
        assertThat(chooseThrowable.invoke(null, event, false)).isSameAs(event.throwable());
        assertThat(chooseThrowable.invoke(null, FlowEvent.builder().name("empty").build(), true)).isNull();
        assertThat(coerce.invoke(null, 123, String.class)).isEqualTo("123");
        assertThat(coerce.invoke(null, 123, Long.class)).isEqualTo(123);
        assertThat(coerce.invoke(null, null, String.class)).isNull();
        assertThat(nullToEmpty.invoke(null, new Object[] {null})).isEqualTo("");
        assertThat(scopes).containsExactly("", "orders.");
        assertThat(lifecycles).containsExactlyInAnyOrder(
                OnFlowLifecycle.Lifecycle.STARTED,
                OnFlowLifecycle.Lifecycle.COMPLETED,
                OnFlowLifecycle.Lifecycle.FAILED);
        assertThat(directLifecycles).containsExactly(OnFlowLifecycle.Lifecycle.STARTED);
        assertThat(attrBinding.apply(event)).isEqualTo("SKU-1");
        assertThat(throwableBinding).isNull();
        assertThat(rootThrowableBinding.apply(event)).isSameAs(root);
        assertThat(flowEventBinding.apply(event)).isSameAs(event);
        assertThat(allAttrsBinding.apply(event)).isEqualTo(event.attributes().map());
        assertThat(allContextBinding.apply(event)).isEqualTo(event.eventContext());
        assertThat(contextValueBinding.apply(event)).isEqualTo("bitstep");
        assertThat(noBinding).isNull();
    }

    @Test
    void compile_handler_covers_direct_lifecycle_and_binding_variants() throws Exception {
        FlowSinkScanner scanner = new FlowSinkScanner(new ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry());
        Method compileHandler = FlowSinkScanner.class.getDeclaredMethod(
                "compileHandler", Object.class, Method.class, List.class, Set.class);
        compileHandler.setAccessible(true);

        SampleSink sink = new SampleSink();
        Method lifecycleMethod = SampleSink.class.getDeclaredMethod(
                "onFailedLifecycle", Throwable.class, String.class, java.util.Map.class, java.util.Map.class);
        Object compiled = compileHandler.invoke(
                scanner,
                sink,
                lifecycleMethod,
                List.of("orders"),
                Set.of(OnFlowLifecycle.Lifecycle.FAILED, OnFlowLifecycle.Lifecycle.COMPLETED));

        assertThat(compiled).isNotNull();

        Method matches = compiled.getClass().getDeclaredMethod("matches", FlowEvent.class);
        Method invoke = compiled.getClass().getDeclaredMethod("invoke", FlowEvent.class);
        matches.setAccessible(true);
        invoke.setAccessible(true);

        FlowEvent failed = FlowEvent.builder().name("orders.checkout").build();
        failed.eventContext().put("lifecycle", "FAILED");
        failed.eventContext().put("trace.id", "trace-1");
        failed.attributes().put("sku", "SKU-1");
        IllegalArgumentException root = new IllegalArgumentException("root");
        failed.setThrowable(new RuntimeException("wrap", root));

        assertThat(matches.invoke(compiled, failed)).isEqualTo(true);
        invoke.invoke(compiled, failed);

        assertThat(sink.throwable).isSameAs(root);
        assertThat(sink.contextValue).isEqualTo("trace-1");
        assertThat(sink.attributes).containsEntry("sku", "SKU-1");
        assertThat(sink.context).containsEntry("lifecycle", "FAILED");

        FlowEvent completed = FlowEvent.builder().name("orders.checkout").build();
        completed.eventContext().put("lifecycle", "COMPLETED");
        completed.eventContext().put("trace.id", "trace-2");
        assertThat(matches.invoke(compiled, completed)).isEqualTo(false);

        Method noLifecycleMethod = SampleSink.class.getDeclaredMethod("withoutLifecycle", String.class);
        assertThat(compileHandler.invoke(scanner, sink, noLifecycleMethod, List.of(), Set.of())).isNull();

        Method invalidBindingMethod = SampleSink.class.getDeclaredMethod("invalidBinding", Integer.class);
        assertThat(compileHandler.invoke(scanner, sink, invalidBindingMethod, List.of(), Set.of())).isNull();

        Method failureFinishMethod = SampleSink.class.getDeclaredMethod("onCompletedFailureOutcome", Throwable.class);
        Object failureFinishCompiled =
                compileHandler.invoke(scanner, sink, failureFinishMethod, List.of(), Set.of(OnFlowLifecycle.Lifecycle.FAILED));
        assertThat(failureFinishCompiled).isNotNull();

        Method failureFinishMatches = failureFinishCompiled.getClass().getDeclaredMethod("matches", FlowEvent.class);
        Method failureFinishInvoke = failureFinishCompiled.getClass().getDeclaredMethod("invoke", FlowEvent.class);
        failureFinishMatches.setAccessible(true);
        failureFinishInvoke.setAccessible(true);

        FlowEvent failedCompletion = FlowEvent.builder().name("orders.checkout").build();
        failedCompletion.eventContext().put("lifecycle", "FAILED");
        RuntimeException wrapper = new RuntimeException("wrap", new IllegalArgumentException("root"));
        failedCompletion.setThrowable(wrapper);

        assertThat(failureFinishMatches.invoke(failureFinishCompiled, failedCompletion)).isEqualTo(true);
        failureFinishInvoke.invoke(failureFinishCompiled, failedCompletion);
        assertThat(sink.failureFinishThrowable).isSameAs(wrapper);

        Object mismatchedProxy = Proxy.newProxyInstance(
                Runnable.class.getClassLoader(), new Class<?>[] {Runnable.class}, (proxy, method, args) -> null);
        Method startedHandler = SampleSink.class.getDeclaredMethod("startedHandler");
        assertThat(compileHandler.invoke(scanner, mismatchedProxy, startedHandler, List.of(), Set.of())).isNull();
    }

    @OnFlowScopes({@OnFlowScope(""), @OnFlowScope("orders.")})
    static class ScopedType {
    }

    @OnAllLifecycles
    static class LifecycleType {
    }

    static class DirectLifecycleType {
        @OnFlowLifecycle(OnFlowLifecycle.Lifecycle.STARTED)
        void started() {
        }
    }

    static class Samples {
        void annotated(
                @PullAttribute("sku") String sku,
                Throwable ignored,
                @FlowException(FlowException.Source.ROOT) Throwable root) {
        }

        void flowEvent(FlowEvent event) {
        }

        void allAttributes(@PullAllAttributes Map<String, Object> attributes) {
        }

        void allContext(@PullAllContextValues Map<String, Object> context) {
        }

        void contextValue(@PullContextValue("tenant.id") String tenantId) {
        }

        void noBinding(@Deprecated String ignored) {
        }
    }

    static class SampleSink {
        Throwable throwable;
        Throwable failureFinishThrowable;
        String contextValue;
        java.util.Map<String, Object> attributes;
        java.util.Map<String, Object> context;

        @OnFlowLifecycle(OnFlowLifecycle.Lifecycle.FAILED)
        @OnOutcome(Outcome.FAILURE)
        void onFailedLifecycle(
                @FlowException(FlowException.Source.ROOT) Throwable throwable,
                @PullContextValue("trace.id") String traceId,
                @PullAllAttributes java.util.Map<String, Object> attributes,
                @PullAllContextValues java.util.Map<String, Object> context) {
            this.throwable = throwable;
            this.contextValue = traceId;
            this.attributes = attributes;
            this.context = context;
        }

        void withoutLifecycle(@PullAttribute("sku") String sku) {
        }

        @OnFlowCompleted
        void invalidBinding(Integer count) {
        }

        @OnFlowCompleted
        @OnOutcome(Outcome.FAILURE)
        void onCompletedFailureOutcome(Throwable throwable) {
            this.failureFinishThrowable = throwable;
        }

        @OnFlowStarted
        void startedHandler() {
        }
    }
}
