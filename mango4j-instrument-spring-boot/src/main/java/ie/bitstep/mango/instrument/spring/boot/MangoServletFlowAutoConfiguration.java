package ie.bitstep.mango.instrument.spring.boot;

import ie.bitstep.mango.instrument.spring.MangoServletFlowConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot auto-configuration that registers {@link MangoServletFlowConfiguration}
 * when Spring MVC is on the classpath.
 *
 * <p>This enables zero-configuration instrumentation of {@code @Flow}-annotated Spring
 * MVC controller methods in Boot applications.
 */
@AutoConfiguration(after = MangoInstrumentationAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.web.servlet.HandlerInterceptor")
@ConditionalOnMissingBean(name = "mangoFlowWebInterceptorConfigurer")
@Import(MangoServletFlowConfiguration.class)
public class MangoServletFlowAutoConfiguration {
}
