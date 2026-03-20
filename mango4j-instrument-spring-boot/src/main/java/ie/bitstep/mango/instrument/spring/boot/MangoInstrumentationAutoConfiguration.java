package ie.bitstep.mango.instrument.spring.boot;

import ie.bitstep.mango.instrument.spring.MangoInstrumentationConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(MangoInstrumentationConfiguration.class)
public class MangoInstrumentationAutoConfiguration {
}
