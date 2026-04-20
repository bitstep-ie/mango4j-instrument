# Annotations

This page collects the main annotations used by Mango4j-instrument. The guide explains the normal workflow; this page is
intended as a quick reference when you already know which part of the model you are working with.

## Flow Definition

The annotations used to define work are:

* `@Flow`: marks a root unit of work.
* `@Step`: marks nested work inside a flow.
* `@Kind`: sets span-kind style metadata for an event.

Use `@Flow` for the operation your application would naturally name in logs, audit, or diagnostics. Use `@Step` for
important work inside that operation.

## Metadata Push

The annotations used to add data to an event are:

* `@PushAttribute`
* `@PushContextValue`

These annotations are placed on method parameters. When the annotated method is intercepted, the runtime reads the
parameter value and adds it to the current event.

> **NOTES:**
>
> * Attributes are usually business data such as order ids, user ids, or tenant ids.
>
> * Context values are usually runtime data such as trace ids, correlation ids, or routing values.

## Sink Definition

The annotations used to define sink behavior are:

* `@FlowSink`
* `@OnFlowStarted`
* `@OnFlowCompleted`
* `@OnFlowFailure`
* `@OnFlowLifecycle`
* `@OnFlowNotMatched`
* `@OnOutcome`
* `@OnFlowScope`
* `@OnFlowScopes`
* `@RequiredAttributes`
* `@RequiredEventContext`

`@FlowSink` marks a Spring bean as a sink. The other annotations describe which events its methods should receive.

## Sink Parameter Pull

The annotations used to bind sink parameters are:

* `@PullAttribute`
* `@PullContextValue`
* `@PullAllAttributes`
* `@PullAllContextValues`
* `@FlowException`

These are resolved by the Spring scanner when it compiles sink handlers. They let the handler declare the values it needs
instead of manually unpacking every `FlowEvent`.
