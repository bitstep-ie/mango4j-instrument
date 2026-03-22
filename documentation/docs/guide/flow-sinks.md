# Flow Sinks

`@FlowSink` beans are scanned by the Spring runtime and compiled into event handlers.

## Basic Sink

```java
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

## Parameter Binding

Sink methods can bind:

- the full `FlowEvent`
- pulled attributes via `@PullAttribute`
- pulled context values via `@PullContextValue`
- all attributes via `@PullAllAttributes`
- all context via `@PullAllContextValues`
- failures via `Throwable` plus `@FlowException`

## Fallbacks

`@OnFlowNotMatched` methods run when a sink is in play but none of its normal handlers matched the event.

That is useful for assertions, diagnostics, or “missed route” bookkeeping.
