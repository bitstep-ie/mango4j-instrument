# Testing And Mutation Coverage

The runtime modules have unit-test coverage across the core model and Spring runtime behavior. PIT mutation testing is
also configured in the Maven build for the parts of the project where mutation coverage gives useful feedback.

## Commands

Run the normal test suite with:

```bash
mvn -q verify
```

Run mutation testing with:

```bash
mvn -q -Ppitest verify
```

## Scope

Mutation testing is focused on the runtime-heavy modules:

* `mango4j-instrument-core`
* `mango4j-instrument-spring`

The annotations module is intentionally excluded because it is mostly declarative API surface. The Boot module remains
thin configuration glue, so the main behavioral coverage lives in the Spring runtime tests.
