# Modules

## `mango4j-instrument-annotations`

Contains only annotation types and minimal API-level semantics.

Use this in application or library code that should remain independent of the Spring runtime.

## `mango4j-instrument-core`

Contains:

- `FlowEvent`
- processor support
- dispatch infrastructure
- validation

This is the runtime-neutral model.

## `mango4j-instrument-spring`

Contains:

- AOP aspect implementation
- sink scanning and binding
- servlet and WebFlux trace filters
- Spring configuration and enable annotation

## `mango4j-instrument-spring-boot`

Contains Boot-specific auto-configuration on top of the Spring runtime.
