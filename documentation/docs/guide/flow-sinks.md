# Flow Sinks

Flow sinks are how an application reacts to the events emitted by `@Flow` and `@Step` methods. A sink is just a Spring
bean annotated with `@FlowSink`. The runtime scans the bean, finds handler methods, and calls the handlers whose filters
match the event.

## Basic Sink

```java
import ie.bitstep.mango.instrument.annotations.OnFlowCompleted;
import ie.bitstep.mango.instrument.annotations.OnFlowScope;
import ie.bitstep.mango.instrument.annotations.OnFlowStarted;
import ie.bitstep.mango.instrument.annotations.PullAllAttributes;
import ie.bitstep.mango.instrument.model.FlowEvent;
import ie.bitstep.mango.instrument.spring.annotations.FlowSink;

import java.util.Map;

@FlowSink
@OnFlowScope("checkout.")
class CheckoutSink {

    @OnFlowStarted
    void onStarted(FlowEvent event) {
    }

    @OnFlowCompleted
    void onCompleted(@PullAllAttributes Map<String, Object> attributes) {
    }
}
```

This sink receives started and completed events under the `checkout.` scope. The completed handler demonstrates parameter
binding by asking for all attributes rather than the entire event.

## Scope Matching

Use `@OnFlowScope` at class or method level to limit which flow names match.

* `"checkout."` matches `checkout.submit` and `checkout.stock.reserve`.
* `"checkout"` matches `checkout` and nested names under `checkout.`.
* A blank scope matches anything.

Class-level scope is useful when the whole sink is dedicated to one area of the application. Method-level scope is useful
when a sink contains handlers for several related areas.

## Parameter Binding

Sink methods can bind the full event or ask the runtime to pull specific values from the event.

Supported parameters include:

* `FlowEvent` for the full event.
* `@PullAttribute` for one attribute.
* `@PullContextValue` for one context value.
* `@PullAllAttributes` for all attributes.
* `@PullAllContextValues` for all context values.
* `Throwable` together with `@FlowException` for failure details.

> **NOTES:**
>
> * Parameter binding keeps sinks small. A handler that only needs a user id does not need to take the full event and
>   parse it manually.
>
> * Required attribute and required context filters can be used when a handler should only run if specific data is
>   present.
>
> * Failure handlers should prefer `@OnFlowFailure` or `@OnFlowLifecycle(FAILED)` when the handler is specifically about
>   failed lifecycle events.

## Fallbacks

`@OnFlowNotMatched` methods run when a sink was considered for an event but none of its normal handlers matched.

This is useful for assertions, diagnostics, or missed-route bookkeeping. For example, a test sink can fail fast when a
flow event was emitted but did not match the handler that the test expected.
