# Spring Boot

Mango4j-instrument keeps plain Spring support and Spring Boot support separate on purpose. The annotation model and
runtime behavior are the same, but the Boot module adds the auto-configuration layer that Boot applications expect.

## Modules

The Spring-facing modules are:

* `mango4j-instrument-spring`: the core Spring runtime, AOP aspect, sink scanner, web trace filters, and
  `@EnableMangoInstrumentation`.
* `mango4j-instrument-spring-boot`: Boot auto-configuration on top of the Spring runtime.

This split means that non-Boot Spring applications are not forced to take a Boot dependency, and shared application
libraries can depend on annotations without dragging in runtime wiring.

## Recommended Usage

For Boot applications, depend on `mango4j-instrument-spring-boot`. It builds on the same Spring runtime module and keeps
the enable-style programming model available.

For plain Spring applications, depend on `mango4j-instrument-spring` and enable instrumentation explicitly.

For library code, depend on `mango4j-instrument-annotations` only. Library code should usually expose instrumentation
annotations without deciding which runtime the final application must use.
