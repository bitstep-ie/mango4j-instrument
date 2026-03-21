package ie.bitstep.mango.instrument.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Matches a sink method against the lifecycle of the emitted {@link ie.bitstep.mango.instrument.model.FlowEvent}.
 *
 * <p>This is event-oriented rather than final-outcome-oriented. A nested step or flow can emit a {@link Lifecycle#FAILED}
 * event even if an outer caller catches the exception and the overall root flow later completes successfully.
 *
 * <p>Use this when the handler should react to a specific emitted lifecycle event. Use {@link OnOutcome} when the
 * handler should express success or failure as an outcome classification instead.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnFlowLifecycle {
    Lifecycle value();

    enum Lifecycle {
        STARTED,
        COMPLETED,
        FAILED
    }
}
