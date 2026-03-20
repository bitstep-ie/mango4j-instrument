package ie.bitstep.mango.instrument.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional clarity marker for Throwable parameters in failure handlers.
 *
 * <p>Handlers for {@code @OnFlowFailure} (or {@code @OnFlowLifecycle(FAILED)}) can declare a
 * {@link Throwable} parameter without any annotation. This annotation is purely for readability
 * and, if specified, may indicate whether the direct exception or the root cause should be
 * injected.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlowException {
    Source value() default Source.CAUSE;

    enum Source {
        CAUSE,
        ROOT
    }
}
