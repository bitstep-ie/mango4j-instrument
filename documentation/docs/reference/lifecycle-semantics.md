# Lifecycle Semantics

This is the part of the model that needs the most explicit documentation.

## Lifecycle

Lifecycle is about the emitted event phase:

- `STARTED`
- `COMPLETED`
- `FAILED`

`@OnFlowLifecycle(...)` and `@OnFlowFailure` operate at this level.

## Outcome

Outcome is intended to be a semantic classification of success or failure.

`@OnOutcome(...)` operates at this level.

## Why They Are Different

A nested step can fail, the caller can catch the exception, and the root flow can still complete successfully.

Example:

1. `checkout.submit` starts
2. `checkout.stock.reserve` throws
3. the flow catches the exception and falls back
4. `checkout.submit` completes successfully

In that case:

- the step emitted a failed lifecycle event
- the root flow did not necessarily have a final failure outcome

## Current Runtime Detail

The current implementation overlaps `@OnOutcome(FAILURE)` heavily with failed lifecycle matching.

That means:

- conceptually, lifecycle and outcome are different
- in the present runtime, failure outcome matching is narrower than ideal and maps closely to failed lifecycle events

Prefer:

- `@OnFlowFailure`, or
- `@OnFlowLifecycle(FAILED)`

for handlers that are explicitly about failure events.
