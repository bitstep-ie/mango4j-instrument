# Guide


## Introduction

Mango4j-instrument provides a small annotation-driven model for describing meaningful work in a Spring application. The
library turns method execution into structured lifecycle events and gives application code a consistent way to attach
business attributes, runtime context, and failure details to those events.

The important design point is that the annotations describe application intent rather than a specific observability
product. You can send the resulting events to logs, metrics, traces, audit tables, test assertions, or a custom internal
processor. The application code still says the same thing: this method is a flow, this method is a step, these parameter
values should be visible to the instrumentation layer, and these sink methods should react to matching events.

The following instructions use Spring Boot-style examples because that is the most common way developers will use the
library. The same programming model is also available to plain Spring applications through the
`mango4j-instrument-spring` module.

## What The Library Gives You

The main annotations are:

* `@Flow` for a root unit of work.
* `@Step` for nested work inside a flow.
* `@PushAttribute` and `@PushContextValue` for adding data to the active event.
* `@Kind` for describing the span role metadata attached to an event.
* `@FlowSink` for declaring event handlers.

The Spring runtime then uses AOP to intercept annotated methods, build `FlowEvent` objects, and dispatch those events to
the matching sink methods.

> **NOTES:**
>
> * Application and library code can depend on `mango4j-instrument-annotations` without depending on Spring.
>
> * The runtime code lives separately so the annotation API can stay lightweight and reusable.
>
> * This project does not export telemetry by itself. It creates a structured event model that you can bridge into the
>   telemetry, audit, or diagnostics approach used by your application.

## Typical Flow

A typical integration looks like this:

1. Add the Spring or Spring Boot runtime dependency.
2. Enable the instrumentation runtime.
3. Annotate a controller, service, or job method with `@Flow`.
4. Annotate nested service operations with `@Step` where you need more detail.
5. Add attributes or context values from method parameters.
6. Register one or more `@FlowSink` beans to consume the events.

This keeps the instrumentation visible at the place where the application work happens, while still allowing the
event-handling logic to live in its own sink class.

## Runtime Shape

At runtime the Spring module:

* Intercepts `@Flow` and `@Step` methods.
* Tracks the current flow context.
* Builds started, completed, and failed `FlowEvent` instances.
* Compiles sink methods discovered on `@FlowSink` beans.
* Dispatches events to handlers that match lifecycle, scope, outcome, and required data filters.
* Optionally imports inbound trace headers into the current context for servlet or WebFlux applications.

For many applications the most important part of this model is that the business code does not need to know where the
event will eventually go. It only needs to mark the application work clearly.
