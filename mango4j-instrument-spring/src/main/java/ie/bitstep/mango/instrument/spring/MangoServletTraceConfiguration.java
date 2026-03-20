package ie.bitstep.mango.instrument.spring;

import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.spring.web.TraceContextFilter;
import jakarta.servlet.Filter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MangoServletTraceConfiguration {
    @Bean(name = "mangoTraceContextFilter")
    public Filter mangoTraceContextFilter(FlowProcessorSupport support) {
        return new TraceContextFilter(support);
    }
}
