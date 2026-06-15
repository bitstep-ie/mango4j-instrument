package ie.bitstep.mango.instrument.spring.boot;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;
import ie.bitstep.mango.instrument.spring.MangoInstrumentationConfiguration;

/** Spring Boot auto-configuration that registers the core mango4j instrumentation beans. */
@AutoConfiguration
@Import(MangoInstrumentationConfiguration.class)
public class MangoInstrumentationAutoConfiguration {}
