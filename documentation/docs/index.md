<h1 style="display:none;">mango4j-instrument</h1>

<figure markdown="span">
    ![Logo](./assets/mango-with-text-black.png#only-light)
    ![Logo](./assets/mango-with-text-white.png#only-dark)
    <figcaption>mango4j-instrument</figcaption>
</figure>

`mango4j-instrument` is a Spring-focused instrumentation library for capturing flows, steps, metadata, failures, and inbound trace context without tying your application to a single telemetry backend.

## Start Here

- [Guide](guide/guide.md)
- [Getting Started](guide/getting-started.md)
- [Flows And Steps](guide/flows-and-steps.md)
- [Flow Sinks](guide/flow-sinks.md)
- [Web Tracing](guide/web-tracing.md)
- [Event Model](reference/event-model.md)
- [Lifecycle Semantics](reference/lifecycle-semantics.md)

## Modules

- `mango4j-instrument-annotations`: reusable annotations and API surface
- `mango4j-instrument-core`: event model, processor support, validation, and dispatch
- `mango4j-instrument-spring`: Spring AOP runtime, sink scanning, and trace filters
- `mango4j-instrument-spring-boot`: Boot auto-configuration layer

## Design Goals

- Keep application annotations lightweight and reusable.
- Let runtime support evolve independently from the annotation jar.
- Support plain Spring and Spring Boot from the same programming model.
- Preserve clear lifecycle semantics for started, completed, and failed events.

## Example

```java
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import ie.bitstep.mango.instrument.annotations.Flow;
import ie.bitstep.mango.instrument.annotations.PushAttribute;
import ie.bitstep.mango.instrument.annotations.Step;

@RestController
class CheckoutController {

    private final StockService stockService;

    CheckoutController(StockService stockService) {
        this.stockService = stockService;
    }

    @Flow("checkout.submit")
    public String checkout(@PushAttribute("user.id") String userId, String sku) {
        stockService.reserve(sku);
        return "ok";
    }
}

@Service
class StockService {

    @Step("checkout.stock.reserve")
    public void reserve(@PushAttribute("sku") String sku) {
    }
}
```
