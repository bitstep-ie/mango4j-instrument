package ie.bitstep.mango.instrument.spring;

import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.core.processor.FlowProcessor;
import ie.bitstep.mango.instrument.spring.web.FlowWebInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link FlowWebInterceptor} as a Spring MVC interceptor so that
 * {@code @Flow}-annotated controller handler methods are automatically instrumented
 * without requiring any extra configuration from the application.
 *
 * <p>This configuration is imported by {@link MangoInstrumentationImportSelector} when
 * {@code jakarta.servlet.Filter} is on the classpath, and by
 * {@code MangoServletFlowAutoConfiguration} for Spring Boot applications.
 */
@Configuration(proxyBeanMethods = false)
public class MangoServletFlowConfiguration {

    /**
     * The interceptor is registered at the highest available precedence so that
     * the flow lifecycle is established before any application-level interceptors run.
     */
    static final int INTERCEPTOR_ORDER = Integer.MIN_VALUE + 100;

    @Bean
    public WebMvcConfigurer mangoFlowWebInterceptorConfigurer(
            FlowProcessor flowProcessor, FlowProcessorSupport flowProcessorSupport) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new FlowWebInterceptor(flowProcessor, flowProcessorSupport))
                        .order(INTERCEPTOR_ORDER);
            }
        };
    }
}
