package ie.bitstep.mango.instrument.spring;

import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.spring.webflux.TraceContextWebFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;

@Configuration(proxyBeanMethods = false)
public class MangoWebFluxTraceConfiguration {
    @Bean(name = "mangoWebFluxTraceContextFilter")
    public WebFilter mangoWebFluxTraceContextFilter(FlowProcessorSupport support) {
        return new TraceContextWebFilter(support);
    }
}
