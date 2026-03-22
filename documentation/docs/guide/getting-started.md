# Getting Started

## Choose A Runtime

For plain Spring:

```xml
<dependency>
    <groupId>ie.bitstep.mango</groupId>
    <artifactId>mango4j-instrument-spring</artifactId>
    <version>1.0.0</version>
</dependency>
```

For Spring Boot:

```xml
<dependency>
    <groupId>ie.bitstep.mango</groupId>
    <artifactId>mango4j-instrument-spring-boot</artifactId>
    <version>1.0.0</version>
</dependency>
```

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

@Service
class StockService {

    @Step("checkout.stock.reserve")
    public void reserve(String sku) {
    }
}
```

## Register A Sink

```java
import ie.bitstep.mango.instrument.annotations.OnFlowCompleted;
import ie.bitstep.mango.instrument.annotations.OnFlowStarted;
import ie.bitstep.mango.instrument.model.FlowEvent;
import ie.bitstep.mango.instrument.spring.annotations.FlowSink;

@FlowSink
class AuditSink {

    @OnFlowStarted
    void onStart(FlowEvent event) {
    }

    @OnFlowCompleted
    void onCompleted(FlowEvent event) {
    }
}
```
