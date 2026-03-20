package ie.bitstep.mango.instrument.spring.boot;

import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.spring.web.TraceContextFilter;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(Filter.class)
public class MangoServletTraceAutoConfiguration {
    @Bean(name = "mangoTraceContextFilter")
    @ConditionalOnMissingBean(name = "mangoTraceContextFilter")
    public Filter mangoTraceContextFilter(FlowProcessorSupport support) {
        return new TraceContextFilter(support);
    }
}
