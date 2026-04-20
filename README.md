# Mango4j Instrument

[![CI](https://github.com/bitstep-ie/mango4j-instrument/actions/workflows/ci.yml/badge.svg)](https://github.com/bitstep-ie/mango4j-instrument/actions/workflows/ci.yml)
[![CodeQL](https://github.com/bitstep-ie/mango4j-instrument/actions/workflows/codeql.yml/badge.svg)](https://github.com/bitstep-ie/mango4j-instrument/actions/workflows/codeql.yml)
[![Dependabot](https://github.com/bitstep-ie/mango4j-instrument/actions/workflows/dependabot/dependabot-updates/badge.svg)](https://github.com/bitstep-ie/mango4j-instrument/actions/workflows/dependabot/dependabot-updates)


<br />
<div align="center">
    <a href="https://github.com/bitstep-ie/mango4j-instrument">
    <picture>
        <source srcset="documentation/docs/assets/mango-with-text-black.png" media="(prefers-color-scheme: light)">
        <source srcset="documentation/docs/assets/mango-with-text-white.png" media="(prefers-color-scheme: dark)">
        <img src="documentation/docs/assets/mango-with-text-black.png" alt="mango Logo">
    </picture>
    </a>
    <h3 align="center">mango4j-instrument</h3>
    <p align="center">
        Annotation-based flow and step instrumentation for Spring applications.
        <br />
        <a href="https://bitstep-ie.github.io/mango4j-instrument/latest" target="_blank"><strong>Explore the Official Guide &raquo;</strong></a>
        <br />
        <br />
        <a href="https://github.com/bitstep-ie/mango4j-examples" target="_blank">View Example Application</a>
        &middot;
        <a href="https://github.com/bitstep-ie/mango4j-instrument/issues/new?template=bug_report.md" target="_blank">
            Report Bug
        </a>
        &middot;
        <a href="https://github.com/bitstep-ie/mango4j-instrument/issues/new?template=feature_request.md" target="_blank">
            Request Feature
        </a>
    </p>
</div>
<br />



# Introduction

Mango4j-instrument is a small framework for adding structured instrumentation to Java applications without coupling the
application code to a particular telemetry backend. It uses simple annotations to mark root operations, nested steps,
metadata values, and lifecycle event handlers. The runtime then emits flow events which can be consumed by whatever sink
your application needs, whether that is logging, audit, assertions in tests, a custom metrics bridge, or a tracing
adapter.

This library is not a tracing system or observability vendor integration. It is a programming model and runtime for
describing meaningful application work. Just like Spring Boot is not a web application, Mango4j-instrument is not a
telemetry backend. It gives your application a stable vocabulary for flows, steps, attributes, context values, and
lifecycle events while leaving the downstream processing choice up to you.

The following is a basic quick start guide to getting started with the library. For more complete instructions please
read [the official guide](https://bitstep-ie.github.io/mango4j-instrument/latest/guide/overview/) instead.

You can also check out the mango4j examples repository for working Spring applications which show how the Mango
libraries are intended to be used together:
[mango4j-examples](https://github.com/bitstep-ie/mango4j-examples).

## Annotations

The main annotations that developers will use are `@Flow`, `@Step`, `@PushAttribute`, `@PushContextValue`, `@Kind`, and
`@FlowSink`.

# Getting Started

Add the Spring runtime dependency to your pom:

```xml
<dependency>
    <groupId>ie.bitstep.mango</groupId>
    <artifactId>mango4j-instrument-spring</artifactId>
    <version>1.0.0</version>
</dependency>
```

If your application is a Spring Boot application you can use the Boot integration instead:

```xml
<dependency>
    <groupId>ie.bitstep.mango</groupId>
    <artifactId>mango4j-instrument-spring-boot</artifactId>
    <version>1.0.0</version>
</dependency>
```

Enable instrumentation in your application config:

```java
import ie.bitstep.mango.instrument.spring.EnableMangoInstrumentation;

@SpringBootApplication
@EnableMangoInstrumentation
public class DemoApplication {
}
```

Add a root flow to code that represents a meaningful unit of work:

```java
import ie.bitstep.mango.instrument.annotations.Flow;
import ie.bitstep.mango.instrument.annotations.PushAttribute;

@RestController
class CheckoutController {

    @Flow(name = "demo.checkout")
    public String checkout(@PushAttribute("user.id") String userId) {
        return "ok";
    }
}
```

Add nested steps for work that should be visible inside the root flow:

```java
import ie.bitstep.mango.instrument.annotations.PushAttribute;
import ie.bitstep.mango.instrument.annotations.Step;

@Component
class StockService {

    @Step("demo.stock.verify")
    public void verify(@PushAttribute("sku") String sku) {
    }
}
```

Create a sink when your application needs to react to emitted lifecycle events:

```java
import ie.bitstep.mango.instrument.annotations.OnFlowCompleted;
import ie.bitstep.mango.instrument.annotations.OnFlowScope;
import ie.bitstep.mango.instrument.annotations.OnFlowStarted;
import ie.bitstep.mango.instrument.annotations.PullAllAttributes;
import ie.bitstep.mango.instrument.model.FlowEvent;
import ie.bitstep.mango.instrument.spring.annotations.FlowSink;

import java.util.Map;

@FlowSink
@OnFlowScope("demo.")
public class DemoSink {

    @OnFlowStarted
    public void onStart(FlowEvent event) {
    }

    @OnFlowCompleted
    public void onCompleted(@PullAllAttributes Map<String, Object> attrs) {
    }
}
```

> **NOTES:**
>
> * Flow names should be stable. Dot-separated names such as `checkout.submit` or `payment.capture` work well because
>   sink scope matching can use those prefixes.
>
> * `@Flow` is intended for root units of work. `@Step` is intended for nested work inside a flow.
>
> * `@PushAttribute` is normally used for business metadata that should appear on the event. `@PushContextValue` is
>   normally used for runtime context that sinks may need for correlation or routing.
>
> * The Spring runtime uses Spring AOP. Self-invocation will not pass through the Spring proxy, so an annotated method
>   called from another method on the same bean will not be intercepted.

# Lifecycle Semantics

The lifecycle annotations are deliberately explicit. `@OnFlowStarted`, `@OnFlowCompleted`, `@OnFlowFailure`, and
`@OnFlowLifecycle(...)` match the phase of the event that was emitted.

`@OnOutcome(...)` is intended to describe semantic success or failure of the operation. That is a related idea, but it is
not the same as the event lifecycle. A nested step can fail, be handled by the caller, and still allow the root flow to
complete successfully.

> **NOTE:** In the current runtime implementation `@OnOutcome(FAILURE)` overlaps heavily with failed lifecycle matching.
> Treat that as a current implementation detail rather than the conceptual model. Prefer `@OnFlowFailure` or
> `@OnFlowLifecycle(FAILED)` for handlers that are explicitly about failed events.

Please see [the official guide](https://bitstep-ie.github.io/mango4j-instrument/latest/guide/overview/) for a more
in-depth explanation of all supported features.

## Local Test And Mutation Coverage

To run the normal test suite:

```bash
mvn -q verify
```

To run mutation testing:

```bash
mvn -q -Ppitest verify
```

Mutation testing is focused on the runtime-heavy modules, especially `mango4j-instrument-core` and
`mango4j-instrument-spring`.
