# Security Remediation

Findings from a source review of `mango4j-instrument` (all main modules), ordered by severity.

## 1. Medium — Unsanitized trace headers flow into MDC/logs

**Location:** `mango4j-instrument-spring/src/main/java/ie/bitstep/mango/instrument/spring/AbstractTraceContextFilter.java` (`applyTraceHeaders`, `parseTraceparent`, `parseB3Single`), consumed by `TraceContextFilter` and `TraceContextWebFilter`.

**Issue:** `traceparent`, `tracestate`, `b3`, and `X-B3-*` headers are fully attacker-controlled. They are written into SLF4J MDC with minimal validation:
- `parseTraceparent`/`parseB3Single` only check field **lengths** (32/16 chars) — never that the content is actually hex.
- `tracestate` has **no length cap** before `MDC.put(TRACESTATE, tracestate)`.
- No check for control characters before logging.

**Impact:** Log forging/injection if the log encoder doesn't neutralize embedded delimiters; log-volume amplification via oversized `tracestate` repeated into every log line for the request.

**Suggested fix:**
- Validate `traceId` is 32 hex chars and `spanId`/`parentSpanId` are 16 hex chars (reject/drop otherwise instead of passing through).
- Cap `tracestate` length to the W3C-recommended ~512 bytes; truncate or drop if exceeded.
- Strip or reject control characters (`\r`, `\n`, other non-printable) from any header value before `MDC.put`.
- Apply the same validation in both `parseTraceparent` and `parseB3Single` (currently duplicated logic — consider a shared validator method).

## 2. Medium — Unbounded recursive reflection walk in `HibernateEntityDetector`

**Location:** `mango4j-instrument-spring/src/main/java/ie/bitstep/mango/instrument/spring/validation/HibernateEntityDetector.java` (`detect`, `detectFields`, `detectArray`).

**Issue:** Mutually recursive traversal of an object graph via reflection (`field.setAccessible(true)` on every field), guarded only against cycles (`IdentityHashMap`). No depth limit, no limit on collection/array/map size. Runs on every `@PushAttribute`/`@PushContextValue` parameter via `AttributeParamExtractor.addToMap`.

**Impact:** Deeply nested attacker-influenced input (e.g. a request body deserialized into nested generic collections) can trigger a `StackOverflowError`. Wide collections cause excessive CPU/string-allocation, since each entry builds a new path string. This is an algorithmic-complexity / stack-exhaustion DoS vector wherever a `@PushAttribute`-annotated parameter is bound to request-influenced data.

**Suggested fix:**
- Add a `maxDepth` parameter (e.g. 20–30) threaded through `detect`/`detectFields`/`detectArray`; once exceeded, log at debug and return without recursing further.
- Add a max-elements-scanned cap for `Map`/`Iterable`/array traversal (e.g. stop after the first N entries) to bound CPU cost on large collections.
- Consider making both limits configurable on `HibernateEntityDetector`'s constructor with sane defaults.

## 3. Low — No redaction/allowlist for telemetry data

**Location:** `mango4j-instrument-spring/src/main/java/ie/bitstep/mango/instrument/spring/aspect/FlowAspect.java:64-65` (`attrs.put(ERROR, throwable.toString())`); more generally `AttributeParamExtractor` and `FlowContext`.

**Issue:** Full exception messages (`throwable.toString()`) and any value passed to `@PushAttribute`/`FlowContext.put` are forwarded verbatim to every registered `FlowSinkHandler`, with no PII/secret scrubbing or allowlist/denylist mechanism. A careless annotation on a sensitive parameter (token, password, raw SQL with bound values in the message) leaks it to whatever the sink forwards to (logs, OTel exporters, etc.).

**Suggested fix:**
- Document this prominently (the library is "trust the caller" by design) so adopters know `@PushAttribute`/`FlowContext.put` is not safe for secrets.
- Consider an opt-in masking primitive, e.g. a `@Sensitive` marker or a pluggable `AttributeRedactor` hook invoked before dispatch, so app teams can centrally scrub known-sensitive keys.
- Consider truncating `throwable.toString()` to the exception type only, or making message inclusion configurable, since stack-trace/exception messages are a common leak vector.

## 4. Low — Inconsistent entity-leak guardrail coverage

**Location:** `mango4j-instrument-core/src/main/java/ie/bitstep/mango/instrument/context/FlowContext.java` (`put`, `putAttr`, `putAllAttrs`, `putContext`, `putAllContext`) vs. `AttributeParamExtractor.addToMap`.

**Issue:** `HibernateEntityDetector.checkNotHibernateEntity` is only invoked from the annotation-driven path (`AttributeParamExtractor`). The programmatic `FlowContext` API bypasses it entirely, so `flowContext.put("user", entity)` gets none of the protection that `@PushAttribute` provides on a method parameter.

**Suggested fix:** Route `FlowContext`'s put methods through the same `FlowAttributeValidator` (already available as a Spring bean) before storing the value, so the guardrail applies uniformly regardless of which API an app uses.

## 5. Informational — Trust model for inbound trace context

**Location:** `AbstractTraceContextFilter` / propagation generally.

**Note:** Inbound `traceId`/`spanId`/`tracestate` are accepted at face value and become part of the propagated trace/log correlation data, consistent with standard W3C/B3 propagation semantics used industry-wide. No code change suggested — call out in docs that trace headers must never be used for authorization/trust decisions, since a client can inject arbitrary well-formed values to splice into the trace graph.

## Already mitigated (for reference)

`AsyncDispatchBus`'s per-sink queue (`mango4j-instrument-core/.../dispatch/AsyncDispatchBus.java`) was previously unbounded, allowing memory exhaustion under sustained load or a slow sink. It is now capped at `MAX_QUEUE_DEPTH = 10_000` with drop + `log.warn` on overflow. No further action needed.
