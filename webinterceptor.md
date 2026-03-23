# `@Flow` on Spring `@Controller` / `@RestController` — Known Gap & Options

> **Status:** Open design decision — implementation required before `@Flow` usage on controllers is recommended or supported.
>
> **TL;DR:** `@Flow` works on controller methods but **silently misses the HTTP response status code**. This document explains why, what is lost, and the two paths forward: (A) extend the framework with a `HandlerInterceptor`, or (B) explicitly disallow `@Flow` directly on controller methods and require it on a downstream service layer instead.

---

## 1. What Works Today

When `@Flow` is placed on a `@RestController` method the `FlowAspect` fires correctly:

- `FlowEvent` is created and pushed onto the thread-local stack.
- `@PushAttribute` / `@PushContextValue` parameter values are extracted.
- The Java **return value** of the controller method is captured in `FlowEvent.returnValue` (serialised under `"return"` in the payload) — because `pjp.proceed()` returns the raw Java object before Spring MVC serialises it.
- `elapsedNanos` is calculated.
- Trace context is propagated from MDC (populated by `TraceContextFilter`).
- On success the event is dispatched with status `OK`; on exception with status `ERROR`.

---

## 2. What Is Silently Missing

### 2.1 HTTP Response Status Code

The AOP `@Around` advice in `FlowAspect.aroundFlow()` wraps the controller method invocation:

```
[AOP around starts]
  controller method executes  → pjp.proceed() returns
  FlowAspect dispatches FlowEvent (COMPLETED / FAILED)
[AOP around ends]

[Spring MVC then:]
  serialises return value to JSON
  invokes @ControllerAdvice / @ExceptionHandler (if exception)
  writes HTTP headers + body to socket
  commits response
```

At the point `FlowAspect` dispatches the event the HTTP response **has not been committed**. `HttpServletResponse.getStatus()` at this moment reflects the default (`200`) regardless of what Spring MVC will ultimately write — particularly in the following cases:

| Scenario | What `FlowAspect` sees | Actual HTTP response |
|---|---|---|
| Controller returns `ResponseEntity(200)` | `OK` (via return value cast — see §2.2) | 200 |
| Controller returns plain POJO (status set by `@ResponseStatus`) | `OK` (exception not thrown) | Whatever `@ResponseStatus` declared |
| Controller throws exception handled by `@ControllerAdvice` returning `404` | `ERROR` (exception propagated) | 404 — but the flow records no status code |
| Controller throws exception handled by `@ControllerAdvice` returning `200` (swallowed) | `ERROR` | 200 — flow incorrectly shows FAILED |

### 2.2 Partial Workaround: `ResponseEntity` Return Type

If the controller returns `ResponseEntity<T>`, the status **can** be read from the return value:

```java
Object result = pjp.proceed();
if (result instanceof ResponseEntity<?> re) {
    int status = re.getStatusCode().value();
}
```

This works because `ResponseEntity` is a plain Java object available before serialisation. However it is not a general solution:

- It does not cover `@ResponseStatus`-annotated methods returning plain POJOs.
- It does not cover exception paths where `@ControllerAdvice` remaps the HTTP status after the AOP advice has already unwound.
- It requires the framework to hard-code a dependency on `ResponseEntity`, which is a Spring MVC implementation detail.

### 2.3 HTTP Request Metadata

`FlowAspect` has no access to the raw `HttpServletRequest` and therefore cannot capture:

- HTTP method (`GET`, `POST`, …)
- Request URI / route template (e.g. `/api/users/{id}`)
- Query parameters or headers

These are available only at the servlet / `HandlerInterceptor` layer, not at the AOP method level.

---

## 3. Comparison: enigma-insights `@Insight`

The enigma-insights library solves this with a two-component design:

```
[Servlet layer]  InsightWebInterceptor.preHandle()
  → creates AppInsight, captures HTTP method + URI + route template

  [AOP layer]  InsightsInterceptor
    → detects @Controller, reuses existing AppInsight (does NOT create a new one)
    → executes method, sets SUCCESS / FAILURE + execution time
    → does NOT publish (controller special case)

[Servlet layer]  InsightWebInterceptor.afterCompletion()
  → reads response.getStatus() after response is committed
  → adds HTTP status code to AppInsight
  → publishes AppInsight
  → clears ThreadLocal
```

The interceptor owns the `AppInsight` lifecycle for controllers; the AOP only updates it mid-flight. This is why enigma-insights explicitly checks `isController()` in its `InsightsProcessor` and throws `IllegalStateException` if the interceptor has not run first.

Obsinity currently has no equivalent `HandlerInterceptor`. `TraceContextFilter` only propagates trace headers into MDC — it does not create or own `FlowEvent`s.

---

## 4. Options

### Option A — Extend the Framework (Recommended if HTTP context is required)

Add a `FlowWebInterceptor` implementing Spring's `HandlerInterceptor`:

**`preHandle`**
- Resets the thread-local stack (clean slate per request).
- If the controller method carries `@Flow`, pushes a `FlowEvent` onto the stack and enriches it with HTTP request metadata (method, URI, route template).

**AOP `FlowAspect.aroundFlow()`** (modified)
- Detects `@Controller` / `@RestController` on the target class.
- If controller: **reuses** the existing `FlowEvent` from the stack (created by `preHandle`) rather than pushing a new one. Does not dispatch — that is the interceptor's responsibility.
- If not controller: behaviour unchanged.

**`afterCompletion`**
- Reads `response.getStatus()` after the response is committed.
- Adds HTTP status code to the `FlowEvent` attributes.
- Determines `OK` vs `ERROR` based on 4xx / 5xx.
- Dispatches the `FlowEvent`.
- Clears the thread-local.

**Implications**
- `@Flow` on a controller method **requires** `FlowWebInterceptor` to be registered. If the interceptor is absent, the AOP advice must throw a clear `IllegalStateException` (same contract as enigma-insights).
- `FlowWebInterceptor` registration should be automatic via `CollectionAutoConfiguration` (alongside `TraceContextFilter`), gated on `obsinity.collection.web.interceptor.enabled=true` (default `true`).
- `TraceContextFilter` remains as a safety-net for ThreadLocal cleanup and MDC propagation, unchanged.

**What is gained**
- HTTP method, URI, route template in every controller `FlowEvent`.
- Accurate HTTP response status code, including statuses remapped by `@ControllerAdvice`.
- `FlowEvent.status` correctly reflects the HTTP outcome, not just whether the Java method threw.

**What it costs**
- Adds `@Controller` detection logic to `FlowAspect` (coupling to Spring MVC concepts).
- `@Flow` on a controller gains a lifecycle dependency on the interceptor — the annotation alone is no longer sufficient.
- Two-component coordination must be documented clearly for library consumers.
- WebFlux reactive controllers are not covered by a `HandlerInterceptor`; a separate `WebFilter` extension would be needed.

---

### Option B — Explicitly Disallow `@Flow` on Controllers

Enforce at aspect invocation time that `@Flow` is never placed directly on a `@Controller` / `@RestController` method. Require the flow to be declared on the **service layer** called by the controller.

**Pattern:**

```java
// ❌ Not supported — will throw IllegalStateException at runtime
@RestController
public class OrderController {
    @PostMapping("/orders")
    @Flow(name = "order.create")
    public ResponseEntity<Order> create(@RequestBody OrderRequest req) { ... }
}

// ✅ Correct pattern — @Flow on the service layer
@RestController
public class OrderController {
    private final OrderService orderService;

    @PostMapping("/orders")
    public ResponseEntity<Order> create(@RequestBody OrderRequest req) {
        return ResponseEntity.ok(orderService.createOrder(req));
    }
}

@Service
public class OrderService {
    @Flow(name = "order.create")
    public Order createOrder(OrderRequest req) { ... }
}
```

**Enforcement mechanism**

Add an early guard in `FlowAspect.aroundFlow()`:

```java
@Around("@annotation(com.obsinity.collection.api.annotations.Flow) && execution(* *(..))")
public Object aroundFlow(ProceedingJoinPoint pjp) throws Throwable {
    Class<?> targetClass = pjp.getTarget().getClass();
    if (AnnotationUtils.findAnnotation(targetClass, Controller.class) != null) {
        throw new IllegalStateException(
            "@Flow is not supported directly on @Controller / @RestController methods. " +
            "Move @Flow to the service layer. " +
            "See documentation/controller-flow-gap.md for the rationale and alternatives.");
    }
    // ... existing logic unchanged
}
```

**What is gained**
- Zero framework complexity — no `HandlerInterceptor`, no two-component coordination.
- `@Flow` remains a purely AOP, Spring-MVC-agnostic annotation.
- Consumers get a fast-fail with a clear error message at first request rather than silently incomplete telemetry.

**What it costs**
- HTTP method, URI, route template, and response status code are **never** captured by the flow framework for inbound HTTP requests — consumers must add these manually via `FlowContext.putAttr()` if needed.
- Architecturally forces a service-layer boundary, which is generally good practice but may not suit all codebases.

---

## 5. Decision Criteria

| Requirement | Option A | Option B |
|---|---|---|
| HTTP status code in flow telemetry | ✅ | ❌ (must be set manually) |
| HTTP request metadata (method, URI, route) | ✅ | ❌ (must be set manually) |
| Accurate status with `@ControllerAdvice` remapping | ✅ | ✅ (exception captured on service layer) |
| Framework complexity | Higher | Lower |
| `@Flow` remains purely AOP / MVC-agnostic | ❌ | ✅ |
| Fast-fail on misuse | ✅ (interceptor absent → `IllegalStateException`) | ✅ (controller detected → `IllegalStateException`) |
| WebFlux / reactive controllers | Requires additional `WebFilter` work | ✅ (AOP works on reactive methods unchanged) |

---

## 6. Current Recommendation

**Until this gap is resolved, `@Flow` must not be placed directly on `@Controller` / `@RestController` methods in production code.**

The telemetry will appear to work but will produce silently incomplete events — specifically:
- Missing HTTP response status code.
- Missing HTTP request metadata (method, URI, route template).
- Incorrect `FlowEvent.status` in any scenario involving `@ControllerAdvice` error remapping.

**Place `@Flow` on the service method invoked by the controller.** This is the safe, correct path today regardless of which option is ultimately chosen, and reflects the same layering pattern recommended for service-level observability.

---

## 7. Implementation Notes (if Option A is chosen)

1. Create `FlowWebInterceptor implements HandlerInterceptor` in `obsinity-collection-spring/.../web/`.
2. Register it in `CollectionAutoConfiguration` via a `WebMvcConfigurer` bean, ordered ahead of default interceptors (precedence `-101` matches enigma-insights convention).
3. Modify `FlowAspect.aroundFlow()` to call `isController(targetClass)` (Spring `AnnotationUtils.findAnnotation(clazz, Controller.class)`):
   - `true` → reuse `support.currentContext()`, throw `IllegalStateException` if stack is empty.
   - `false` → existing push/pop/dispatch logic unchanged.
4. `afterCompletion` dispatches the event and calls `support.cleanupThreadLocals()`. `TraceContextFilter` retains its own cleanup call as a safety net.
5. Gate the entire interceptor behind `obsinity.collection.web.interceptor.enabled` (default `true`) consistent with `obsinity.collection.trace.enabled` on `TraceContextFilter`.

---

## 8. Related Files

| File | Relevance |
|---|---|
| `obsinity-collection-spring/.../aspect/FlowAspect.java` | Where controller detection or enforcement must be added |
| `obsinity-collection-spring/.../web/TraceContextFilter.java` | Existing servlet filter — pattern for Option A interceptor |
| `obsinity-collection-spring/.../autoconfigure/CollectionAutoConfiguration.java` | Where `FlowWebInterceptor` bean and `WebMvcConfigurer` would be registered (Option A) |
| `obsinity-collection-spring/.../autoconfigure/TraceAutoConfiguration.java` | Pattern to follow for conditional interceptor registration (Option A) |
| `enigma-insights/.../internal/InsightWebInterceptor.java` | Reference implementation of the two-component pattern (Option A) |
| `enigma-insights/.../internal/InsightsProcessor.java` | Reference for `isController()` detection and interceptor-dependency enforcement |
| `documentation/collection-sdk.md` | SDK overview — update §Instrumentation Building Blocks when either option is implemented |
