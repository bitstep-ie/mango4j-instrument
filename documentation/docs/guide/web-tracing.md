# Web Tracing

The Spring runtime can import inbound trace context for servlet and WebFlux applications. This is intended to make trace
or correlation information available to flow events without requiring each controller or service method to parse headers
manually.

## Supported Runtime Paths

The runtime includes two web integration paths:

* `TraceContextFilter` for servlet applications.
* `TraceContextWebFilter` for WebFlux applications.

These filters populate the current flow context from inbound headers so downstream sinks and processors can see values
such as:

* Trace id.
* Span id.
* Parent span id.
* Tracestate when present.

## Header Formats

The runtime currently supports the common W3C and B3 patterns exercised by the test suite. The imported values become
context values on the active instrumentation event, which means sink methods can pull them using the normal context
binding annotations.

## When It Activates

`@EnableMangoInstrumentation` imports the trace configuration conditionally when the relevant servlet or reactive web
classes are on the classpath.

That means:

* Plain service applications do not pull in web filters.
* Servlet apps get the servlet filter.
* Reactive apps get the WebFlux filter.

> **NOTE:** The web tracing support captures and propagates trace context into the `FlowEvent` model. It does not export
> spans to a tracing backend by itself. Applications that need backend export should implement that in a sink or bridge.
