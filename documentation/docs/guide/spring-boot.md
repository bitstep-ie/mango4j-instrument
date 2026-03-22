# Spring Boot

The repo keeps plain Spring and Boot support separate on purpose.

## Modules

- `mango4j-instrument-spring`: core Spring runtime and `@EnableMangoInstrumentation`
- `mango4j-instrument-spring-boot`: Boot auto-configuration layer

## Why Split Them

- applications can use annotations without dragging in Boot
- runtime code stays reusable in non-Boot Spring applications
- Boot wiring remains thin and replaceable

## Recommended Usage

For Boot applications, depend on `mango4j-instrument-spring-boot`. It builds on the same Spring runtime module and keeps the enable-style programming model available.

For library code, depend on the annotations module only.
