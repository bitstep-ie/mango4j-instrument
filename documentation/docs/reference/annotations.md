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
- `@OnFlowLifecycle`
- `@OnFlowNotMatched`
- `@OnOutcome`
- `@OnFlowScope`
- `@OnFlowScopes`
- `@RequiredAttributes`
- `@RequiredEventContext`

## Sink Parameter Pull

- `@PullAttribute`
- `@PullContextValue`
- `@PullAllAttributes`
- `@PullAllContextValues`
- `@FlowException`

These are resolved by the Spring scanner when it compiles sink handlers.
