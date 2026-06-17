# Flow Sinks

`@FlowSink` beans are scanned by the Spring runtime and compiled into event handlers.

## Basic Sink

```java
import java.util.Map;

import ie.bitstep.mango.instrument.annotations.OnFlowCompleted;
import ie.bitstep.mango.instrument.annotations.OnFlowStarted;
import ie.bitstep.mango.instrument.annotations.OnFlowScope;
import ie.bitstep.mango.instrument.annotations.PullAllAttributes;
import ie.bitstep.mango.instrument.model.FlowEvent;
import ie.bitstep.mango.instrument.spring.annotations.FlowSink;

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

## Scope Matching

Use `@OnFlowScope` at class or method level to limit which flow names match.

- `"checkout."` matches `checkout.submit` and `checkout.stock.reserve`
- `"checkout"` matches `checkout` and nested names under `checkout.`
- blank scope matches anything
- `@OnFlowScope` is repeatable, so a sink can listen to more than one flow prefix

## Parameter Binding

Sink methods can bind:

- the full `FlowEvent`
- pulled attributes via `@PullAttribute`
- pulled context values via `@PullContextValue`
- all attributes via `@PullAllAttributes`
- all context via `@PullAllContextValues`
- failures via `Throwable` plus `@FlowException`

## Success And Failure Handlers

Use `@OnFlowSuccess` for success-specific callbacks and `@OnFlowFailure` for failure-specific callbacks.

```java
import ie.bitstep.mango.instrument.annotations.FlowException;
import ie.bitstep.mango.instrument.annotations.OnFlowFailure;
import ie.bitstep.mango.instrument.annotations.OnFlowScope;
import ie.bitstep.mango.instrument.annotations.OnFlowSuccess;
import ie.bitstep.mango.instrument.annotations.PullAttribute;
import ie.bitstep.mango.instrument.annotations.PullContextValue;
import ie.bitstep.mango.instrument.spring.annotations.FlowSink;

@FlowSink
@OnFlowScope("checkout.")
class CheckoutStatusSink {

    @OnFlowSuccess
    void onSuccess() {
    }

    @OnFlowFailure
    void onFailure(
            @FlowException Throwable error,
            @PullAttribute("user.id") String userId,
            @PullContextValue("tenant.id") String tenantId) {
    }
}
```

`@OnFlowCompleted` is useful when you want to run after the flow completes without saying success or failure in the handler name.

## Fallbacks

`@OnFlowNotMatched` methods run when a sink is in play but none of its normal handlers matched the event.

That is useful for assertions, diagnostics, or missed-route bookkeeping.
