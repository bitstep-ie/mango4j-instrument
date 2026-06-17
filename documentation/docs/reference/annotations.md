# Annotations

## Flow Definition

- `@Flow`: marks a root unit of work
- `@Step`: marks nested work inside a flow
- `@Kind`: sets the span kind metadata for an event

## Metadata Push

- `@PushAttribute`
- `@PushContextValue`

These annotate method parameters and push values into the active event.

## Sink Definition

- `@FlowSink`
- `@OnFlowStarted`
- `@OnFlowCompleted`
- `@OnFlowFailure`
- `@OnFlowSuccess`
- `@OnFlowLifecycle`
- `@OnFlowLifecycles`
- `@OnFlowNotMatched`
- `@OnFlowScope`
- `@OnFlowScopes`
- `@OnAllLifecycles`
- `@RequiredAttributes`
- `@RequiredEventContext`
- `@OrphanAlert`

## Sink Parameter Pull

- `@PullAttribute`
- `@PullContextValue`
- `@PullAllAttributes`
- `@PullAllContextValues`
- `@FlowException`

These are resolved by the Spring scanner when it compiles sink handlers.

## Semantics At A Glance

- `@Flow` and `@Step` both support `value` or `name`
- `@OnFlowScope` is repeatable and works on types or methods
- `@PushAttribute` and `@PushContextValue` forward values verbatim, so do not use them for secrets
- `@OnFlowFailure` is the failure-specific lifecycle hook
- `@OnFlowSuccess` is the success-specific lifecycle hook
- `@OnFlowCompleted` is the generic completion hook
- `@OrphanAlert` controls the log level used when a step is auto-promoted to a flow

## Small Examples

```java
@Flow("checkout.submit")
public String submit(@PushAttribute("user.id") String userId) {
    return "ok";
}
```

```java
import java.util.Map;

import ie.bitstep.mango.instrument.annotations.OnFlowCompleted;
import ie.bitstep.mango.instrument.annotations.OnFlowScope;
import ie.bitstep.mango.instrument.annotations.PullAllAttributes;
import ie.bitstep.mango.instrument.spring.annotations.FlowSink;

@FlowSink
@OnFlowScope("checkout.")
class CheckoutSink {

    @OnFlowCompleted
    void onCompleted(@PullAllAttributes Map<String, Object> attributes) {
    }
}
```
