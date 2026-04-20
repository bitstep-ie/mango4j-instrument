# Modules

Mango4j-instrument is split into small modules so that applications can choose the amount of runtime support they need.
The annotations are kept separate from the Spring runtime, and the Spring runtime is kept separate from the Boot
auto-configuration layer.

## mango4j-instrument-annotations

This module contains only annotation types and minimal API-level semantics.

Use this in application or library code that should remain independent of the Spring runtime. This is the right
dependency for shared modules that want to expose instrumentation annotations without deciding how the final application
will run them.

## mango4j-instrument-core

This module contains the runtime-neutral event model and support code, including:

* `FlowEvent`
* Processor support
* Dispatch infrastructure
* Validation

Code in this module is not tied to Spring.

## mango4j-instrument-spring

This module contains the Spring runtime, including:

* AOP aspect implementation.
* Sink scanning and binding.
* Servlet and WebFlux trace filters.
* Spring configuration and `@EnableMangoInstrumentation`.

Use this module when you want the instrumentation runtime in a plain Spring application.

## mango4j-instrument-spring-boot

This module contains Boot-specific auto-configuration on top of the Spring runtime.

Use this module for Spring Boot applications. It keeps the same annotation model and runtime behavior while making the
wiring fit naturally into a Boot application.
