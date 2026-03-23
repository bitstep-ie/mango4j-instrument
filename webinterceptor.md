# Guide

## `@Flow` on Spring MVC Controllers

The following notes explain why `@Flow` on Spring MVC controller methods needs web-interceptor support. `@Flow` can be
applied to a `@Controller` or `@RestController` today, but the resulting telemetry may be incorrect because Spring MVC
can still change the final HTTP response after the aspect has finished.

The main issue is that `@ControllerAdvice` and exception handlers can alter both the actual HTTP status and the
response body after the controller method returns. Because of that, controller instrumentation cannot rely on the
aspect alone. We need a web interceptor.

## What Works Today

If you put `@Flow` on a controller method, `FlowAspect` still runs in the normal way. It creates a `FlowEvent`,
extracts values from `@PushAttribute` and `@PushContextValue`, records execution time, captures the Java return value,
and dispatches either `OK` or `ERROR`.

A simplified example looks like this:

```java
@RestController
class OrderController {

    @PostMapping("/orders")
    @Flow(name = "order.create")
    ResponseEntity<Order> create(@RequestBody OrderRequest request) {
        return ResponseEntity.ok(...);
    }
}
```

> **NOTES:**
>
> * The aspect does execute for this method.
> * A `FlowEvent` is created and published.
> * The Java return value is available because `pjp.proceed()` returns before Spring MVC writes the HTTP response.
> * This can make controller-level instrumentation look correct when it is not.

## What Is Missing

The problem is that Spring MVC has not finished processing the request when the aspect publishes the flow. After the
controller method returns, Spring may still need to serialize the body, apply `@ResponseStatus`, run
`@ControllerAdvice`, or map an exception into a different status and body.

That means the aspect is too early to know the final HTTP result.

### HTTP Response Status

At aspect time the response may not be committed yet, so the final status code is not reliable.

Examples:

| Scenario | What the flow sees | Actual HTTP response |
|---|---|---|
| Controller returns `ResponseEntity.ok(...)` | Success | 200 |
| Controller returns a POJO with `@ResponseStatus` | Success | Declared status |
| Controller throws and `@ControllerAdvice` maps it to `404` | Error | 404 |
| Controller throws and handler converts it to `200` | Error | 200 |

> **NOTES:**
>
> * This is the main gap with controller-level `@Flow`.
> * A flow can be marked as failed even though the final HTTP response is eventually successful.
> * A flow can also miss the final response status completely.
> * The same problem applies to response payloads because exception handlers may replace the original response body.

### HTTP Request Metadata

At the AOP layer we also do not have proper ownership of the incoming web request, so there is no clean place to record
typical HTTP metadata such as:

* HTTP method
* request URI
* route template
* headers
* query parameters

These are servlet-level concerns rather than plain method-interception concerns.

## `ResponseEntity` Is Only A Partial Workaround

If a controller returns `ResponseEntity<?>`, then the status code can be read from the returned object before the
response is written. That helps in a narrow set of cases, but it is not a real solution.

> **NOTES:**
>
> * It does not cover methods that use `@ResponseStatus`.
> * It does not cover exception remapping in `@ControllerAdvice`.
> * It pushes Spring MVC-specific handling into the flow aspect.
> * It still does not solve the wider request-metadata problem.

## Why A Web Interceptor Is Needed

If the library is going to support `@Flow` directly on controllers, then it needs a Spring MVC `HandlerInterceptor`
that works alongside the aspect.

A likely design would be:

1. `preHandle()` creates or prepares the `FlowEvent` and captures HTTP request metadata.
2. `FlowAspect` detects that the method belongs to a controller and reuses the existing event instead of publishing a
   new one.
3. `afterCompletion()` reads the final response status, updates the event, dispatches it, and clears thread-local
   state.

> **NOTES:**
>
> * This is needed because the final HTTP status and response data may only be known after Spring MVC completes the
>   request.
> * `@ControllerAdvice` and exception handlers are the main reason the aspect alone is not enough.
> * The event should be published only after the response outcome is final.

## Current Limitations

Until the interceptor exists, controller-level `@Flow` has some important limitations.

> **NOTES:**
>
> * The emitted telemetry may not match the final HTTP outcome.
> * `@ControllerAdvice` and exception handlers may change the HTTP status after the aspect has already published the
>   event.
> * Exception handlers may also replace the response body, so the captured Java return value may not match the actual
>   HTTP response payload.
> * HTTP request metadata such as method, URI, and route template is not owned cleanly at the aspect layer.
> * Any controller-level flow data should therefore be treated as incomplete until web-interceptor support is added.

## Current Recommendation

Controller-level `@Flow` needs web-interceptor support. Without that support, the emitted telemetry may not match the
real HTTP outcome because Spring MVC may still modify the status and response body after the aspect runs.
