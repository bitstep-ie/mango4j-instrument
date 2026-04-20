# Getting Started

The following is the shortest path to using Mango4j-instrument in a Spring application. The examples use Spring Boot, but
the core runtime is plain Spring and can be used without Boot.

## Choose A Runtime

For a plain Spring application add:

```xml
<dependency>
    <groupId>ie.bitstep.mango</groupId>
    <artifactId>mango4j-instrument-spring</artifactId>
    <version>1.0.0</version>
</dependency>
```

For a Spring Boot application add:

```xml
<dependency>
    <groupId>ie.bitstep.mango</groupId>
    <artifactId>mango4j-instrument-spring-boot</artifactId>
    <version>1.0.0</version>
</dependency>
```

> **NOTES:**
>
> * Use the Boot module for application projects that already run with Spring Boot.
>
> * Use the plain Spring module when you want the instrumentation runtime without Boot auto-configuration.
>
> * Use only the annotations module in shared application libraries that should not depend on any Spring runtime.

## Enable Instrumentation

Add the enable annotation to your application configuration:

```java
import ie.bitstep.mango.instrument.spring.EnableMangoInstrumentation;

@SpringBootApplication
@EnableMangoInstrumentation
public class DemoApplication {
}
```

This imports the Spring runtime configuration, including the AOP support that intercepts annotated methods and the sink
scanner that discovers `@FlowSink` beans.

## Add A Flow

A flow should represent a root unit of application work. In a web application this is often a controller or service
method. In a batch application it might be a job step or scheduled operation.

```java
import ie.bitstep.mango.instrument.annotations.Flow;
import ie.bitstep.mango.instrument.annotations.PushAttribute;
import org.springframework.stereotype.Service;

@Service
class CheckoutService {

    @Flow(name = "checkout.submit")
    public String checkout(@PushAttribute("user.id") String userId) {
        return "ok";
    }
}
```

When the method is invoked through the Spring proxy, the runtime emits a started event before execution begins and then a
completed or failed event depending on how the method exits.

## Add A Nested Step

Steps describe work inside a flow. They are useful when a root operation has meaningful internal operations that should
be visible to your sinks.

```java
import ie.bitstep.mango.instrument.annotations.PushAttribute;
import ie.bitstep.mango.instrument.annotations.Step;
import org.springframework.stereotype.Service;

@Service
class StockService {

    @Step("checkout.stock.reserve")
    public void reserve(@PushAttribute("sku") String sku) {
    }
}
```

> **NOTE:** Spring AOP does not intercept self-invocation. If an annotated method is called from another method on the
> same bean, the runtime will not see that call. Put nested steps on another Spring bean when you need the interception
> behavior.

## Register A Sink

Sinks are normal Spring beans annotated with `@FlowSink`. The runtime scans them and compiles methods marked with sink
handler annotations.

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

This is enough for the sink to receive matching lifecycle events. More specific handlers can add scope filters, required
attribute filters, context filters, and parameter binding.
