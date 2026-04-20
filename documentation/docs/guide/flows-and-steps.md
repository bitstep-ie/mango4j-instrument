# Flows And Steps

Mango4j-instrument uses two main annotations to describe application execution: `@Flow` and `@Step`. The distinction is
important because it gives your instrumentation model a hierarchy.

## Flow

`@Flow` marks a root operation. A root operation is the unit of work that you would usually want to talk about when
explaining what the application just did. Examples might be `checkout.submit`, `payment.capture`, `customer.register`,
or `report.generate`.

When a flow method runs, the runtime emits:

* A started event when execution begins.
* A completed event when the method returns normally.
* A failed event when the method exits by throwing.

Flow names should be stable and descriptive. Dot-separated names are recommended because they work naturally with scope
matching. For example, a sink scoped to `checkout.` can receive `checkout.submit`, `checkout.stock.reserve`, and other
related checkout events.

## Step

`@Step` marks nested work within an active flow. Steps are useful when a root operation has internal operations that are
important enough to track separately.

```java
import ie.bitstep.mango.instrument.annotations.Flow;
import org.springframework.stereotype.Service;

@Service
class CheckoutService {

    private final StockService stockService;

    CheckoutService(StockService stockService) {
        this.stockService = stockService;
    }

    @Flow(name = "checkout.submit")
    public void checkout(String sku) {
        stockService.reserve(sku);
    }
}
```

```java
import ie.bitstep.mango.instrument.annotations.PushAttribute;
import ie.bitstep.mango.instrument.annotations.Step;
import org.springframework.stereotype.Service;

@Service
class StockService {

    @Step("checkout.stock.reserve")
    void reserve(@PushAttribute("sku") String sku) {
    }
}
```

The result is a root checkout flow with a nested stock reservation step. A sink can listen to both, only the root flow,
only failures, only a particular scope, or only events that contain required metadata.

> **NOTES:**
>
> * Steps should be used for meaningful nested work, not every private helper method.
>
> * A step outside an active flow can still be emitted as an event, but the model is most useful when steps are part of a
>   root flow.
>
> * Spring proxy rules still apply. Cross-bean calls are the safest way to make nested steps visible to the runtime.

## Metadata

You can enrich emitted events by annotating method parameters:

* `@PushAttribute` adds business metadata to the event.
* `@PushContextValue` adds runtime context to the event.
* `@Kind` sets span-kind style metadata for consumers that care about that distinction.

Attributes are usually things you would want to search, display, or assert against, such as a user id, order id, sku, or
tenant id. Context values are usually values used by infrastructure code, such as correlation ids, trace ids, routing
values, or runtime flags.
