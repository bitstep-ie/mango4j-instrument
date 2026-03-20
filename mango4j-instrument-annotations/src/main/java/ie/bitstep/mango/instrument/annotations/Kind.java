package ie.bitstep.mango.instrument.annotations;

import io.opentelemetry.api.trace.SpanKind;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Kind {
    SpanKind value() default SpanKind.INTERNAL;
}
