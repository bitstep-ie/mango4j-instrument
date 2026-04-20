# Lifecycle Semantics

This is the part of the model where the terms need to stay precise. Mango4j-instrument separates event lifecycle from
operation outcome, even though those ideas often line up in simple flows.

## Lifecycle

Lifecycle describes the phase of the event that was emitted by the runtime:

* `STARTED`
* `COMPLETED`
* `FAILED`

`@OnFlowStarted`, `@OnFlowCompleted`, `@OnFlowFailure`, and `@OnFlowLifecycle(...)` operate at this level. They answer the
question: which event phase is this handler interested in?

## Outcome

Outcome is intended to describe the semantic result of the operation. `@OnOutcome(...)` operates at this level.

For a simple root flow, lifecycle failure and failure outcome may look like the same thing. The difference matters once
there are nested steps or handled exceptions.

## Why They Are Different

A nested step can fail, the caller can catch the exception, and the root flow can still complete successfully.

For example:

1. `checkout.submit` starts.
2. `checkout.stock.reserve` throws.
3. The checkout flow catches the exception and falls back.
4. `checkout.submit` completes successfully.

In that case the stock reservation step emitted a failed lifecycle event, but the root checkout flow did not necessarily
have a final failure outcome.

> **NOTE:** In the current runtime implementation `@OnOutcome(FAILURE)` overlaps heavily with failed lifecycle matching.
> Conceptually, lifecycle and outcome are different. In the present runtime, failure outcome matching is narrower than
> ideal and maps closely to failed lifecycle events.

For handlers that are explicitly about failed events, prefer:

* `@OnFlowFailure`
* `@OnFlowLifecycle(FAILED)`

Use `@OnOutcome(...)` when the handler is meant to express operation outcome rather than event phase.
