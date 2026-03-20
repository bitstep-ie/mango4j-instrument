package ie.bitstep.mango.instrument.spring.boot;

import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.spring.webflux.TraceContextWebFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.WebFilter;

@AutoConfiguration
@ConditionalOnClass(WebFilter.class)
public class MangoWebFluxTraceAutoConfiguration {
    @Bean(name = "mangoWebFluxTraceContextFilter")
    @ConditionalOnMissingBean(name = "mangoWebFluxTraceContextFilter")
    public WebFilter mangoWebFluxTraceContextFilter(FlowProcessorSupport support) {
        return new TraceContextWebFilter(support);
    }
}
