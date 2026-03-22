# Flows And Steps

## `@Flow`

`@Flow` marks a root operation. The runtime emits:

- a started event when execution begins
- a completed event when the method returns normally
- a failed event when the method exits by throwing

Flow names should be stable and descriptive. Dot-separated names work well because scope matching understands prefixes like `checkout.`.

## `@Step`

`@Step` is for nested work within an active flow. A step behaves like a smaller lifecycle event inside the current root flow.

Example:

```java
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
@Service
class StockService {

    @Step("checkout.stock.reserve")
    void reserve(@PushAttribute("sku") String sku) {
    }
}
```

## Important Spring AOP Constraint

Self-invocation does not pass through Spring proxies. If a method annotated with `@Step` is called from another method on the same bean, the aspect will not run.

Use cross-bean calls for nested steps if you need the runtime behavior.

## Metadata

You can enrich emitted events with:

- `@PushAttribute`
- `@PushContextValue`
- `@Kind`

Attributes are usually business metadata. Context values are usually runtime metadata that sinks may need for routing or correlation.
