# Getting Started

## Choose A Runtime

For plain Spring:

```xml
<dependency>
    <groupId>ie.bitstep.mango</groupId>
    <artifactId>mango4j-instrument-spring</artifactId>
    <version>0.1.1</version>
</dependency>
```

For Spring Boot:

```xml
<dependency>
    <groupId>ie.bitstep.mango</groupId>
    <artifactId>mango4j-instrument-spring-boot</artifactId>
    <version>0.1.1</version>
</dependency>
```

If you only want compile-time annotations, depend on `mango4j-instrument-annotations` instead.

## Enable Instrumentation

Use the Spring-style enable annotation:

```java
import ie.bitstep.mango.instrument.spring.EnableMangoInstrumentation;

@SpringBootApplication
@EnableMangoInstrumentation
public class DemoApplication {
}
```

The plain Spring module imports the instrumentation runtime directly. The Boot module adds auto-configuration on top of the same runtime.

## Add A Flow

```java
import ie.bitstep.mango.instrument.annotations.Flow;
import ie.bitstep.mango.instrument.annotations.PushAttribute;

@Service
class CheckoutService {

    @Flow(name = "checkout.submit")
    public String checkout(@PushAttribute("user.id") String userId) {
        return "ok";
    }
}
```

## Add A Nested Step

```java
import ie.bitstep.mango.instrument.annotations.Step;
import ie.bitstep.mango.instrument.annotations.PushAttribute;

@Service
class StockService {

    @Step("checkout.stock.reserve")
    public void reserve(@PushAttribute("sku") String sku) {
    }
}
```

## Register A Sink

```java
import java.util.Map;

import ie.bitstep.mango.instrument.annotations.OnFlowCompleted;
import ie.bitstep.mango.instrument.annotations.OnFlowStarted;
import ie.bitstep.mango.instrument.annotations.OnFlowScope;
import ie.bitstep.mango.instrument.annotations.PullAllAttributes;
import ie.bitstep.mango.instrument.model.FlowEvent;
import ie.bitstep.mango.instrument.spring.annotations.FlowSink;

@FlowSink
@OnFlowScope("checkout.")
class AuditSink {

    @OnFlowStarted
    void onStart(FlowEvent event) {
    }

    @OnFlowCompleted
    void onCompleted(@PullAllAttributes Map<String, Object> attributes) {
    }
}
```

## Next Steps

- [Flows And Steps](flows-and-steps.md)
- [Flow Sinks](flow-sinks.md)
- [Web Tracing](web-tracing.md)
