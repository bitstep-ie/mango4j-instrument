# Web Tracing

The Spring runtime can import inbound trace context for both servlet and WebFlux applications.

## Supported Runtime Paths

- servlet filter: `TraceContextFilter`
- WebFlux filter: `TraceContextWebFilter`

These filters populate the current flow context from inbound headers so downstream sinks and processors can see:

- trace id
- span id
- parent span id
- tracestate when present

## Header Formats

The runtime currently supports common W3C and B3 patterns exercised by the test suite.

## When It Activates

`@EnableMangoInstrumentation` imports the trace configuration conditionally when the relevant servlet or reactive web classes are on the classpath.

That means:

- plain service applications do not pull in web filters
- servlet apps get the servlet filter
- reactive apps get the WebFlux filter

## What It Does Not Do

This module captures and propagates trace context into the `FlowEvent` model. It does not itself export spans to a tracing backend.
