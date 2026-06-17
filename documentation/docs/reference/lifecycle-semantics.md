# Lifecycle Semantics

This section describes the lifecycle events emitted by a flow and the annotations used to match them.

## Lifecycle Events

Flow handlers react to one of three lifecycle states:

- `STARTED`
- `COMPLETED`
- `FAILED`

Use these annotations to listen for them:

- `@OnFlowStarted`
- `@OnFlowCompleted`
- `@OnFlowSuccess`
- `@OnFlowFailure`
- `@OnFlowLifecycle(...)`

## When To Use Each One

- `@OnFlowStarted` for setup, logging, and request bookkeeping
- `@OnFlowCompleted` for work that should run when the flow reaches completion
- `@OnFlowSuccess` for success-specific callbacks
- `@OnFlowFailure` for failure-specific callbacks
- `@OnFlowLifecycle(...)` when you want to name the lifecycle value directly in the handler

## Plain English

`@OnFlowSuccess` means the flow finished successfully.

`@OnFlowFailure` means the flow failed.

`@OnFlowLifecycle(OnFlowLifecycle.Lifecycle.STARTED)` means the handler should run when the started event is emitted.

## Example

```java
import java.util.Map;

import ie.bitstep.mango.instrument.annotations.OnFlowCompleted;
import ie.bitstep.mango.instrument.annotations.OnFlowFailure;
import ie.bitstep.mango.instrument.annotations.OnFlowScope;
import ie.bitstep.mango.instrument.annotations.PullAllAttributes;
import ie.bitstep.mango.instrument.spring.annotations.FlowSink;

@FlowSink
@OnFlowScope("checkout.")
class CheckoutLifecycleSink {

    @OnFlowCompleted
    void onCheckoutCompleted(@PullAllAttributes Map<String, Object> attributes) {
    }

    @OnFlowFailure
    void onCheckoutFailed(Throwable error) {
    }
}
```
