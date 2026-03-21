package ie.bitstep.mango.instrument.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Applies an outcome filter to a sink handler.
 *
 * <p>This is intended to model success or failure as an outcome classification, which is conceptually distinct from the
 * lifecycle of an individual emitted event. For example, a nested step can emit a failed lifecycle event and still be
 * recovered by its caller, allowing the overall root flow to finish successfully.
 *
 * <p>In the current runtime implementation, {@link Outcome#FAILURE} overlaps heavily with
 * {@link OnFlowLifecycle.Lifecycle#FAILED}. That overlap is an implementation detail of the current matcher rather than
 * the intended semantic distinction, so prefer documenting your intent clearly and avoid combining both annotations
 * unless you specifically want the redundancy.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnOutcome {
    Outcome value();
}
