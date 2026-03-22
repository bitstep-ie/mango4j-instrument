<h1 style="display:none;">mango4j-instrument</h1>

<figure markdown="span">
    ![Logo](./assets/mango-with-text-black.png#only-light)
    ![Logo](./assets/mango-with-text-white.png#only-dark)
    <figcaption>mango4j-instrument</figcaption>
</figure>

`mango4j-instrument` is a Spring-focused instrumentation library for capturing flows, steps, metadata, failures, and inbound trace context without committing your application to a single telemetry backend.

## Start Here

- [Guide Overview](guide/overview.md)
- [Getting Started](guide/getting-started.md)
- [Flows And Steps](guide/flows-and-steps.md)
- [Flow Sinks](guide/flow-sinks.md)
- [Web Tracing](guide/web-tracing.md)
- [Lifecycle Semantics](reference/lifecycle-semantics.md)

## Modules

- `mango4j-instrument-annotations`: pure annotations and API surface
- `mango4j-instrument-core`: event model, processor support, validation, and dispatch
- `mango4j-instrument-spring`: Spring AOP runtime, sink scanning, and web tracing
- `mango4j-instrument-spring-boot`: Boot auto-configuration layer

## Design Goals

- Keep application annotations lightweight and reusable.
- Let runtime support evolve independently from the annotation jar.
- Support plain Spring and Spring Boot from the same programming model.
- Preserve clear lifecycle semantics for started, completed, and failed events.

## Example

```java
@RestController
class CheckoutController {

    @Flow(name = "checkout.submit")
    public String checkout(@PushAttribute("user.id") String userId) {
        return "ok";
    }
}
```
