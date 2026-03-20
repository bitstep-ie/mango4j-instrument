package ie.bitstep.mango.instrument.spring;

import ie.bitstep.mango.instrument.context.FlowContext;
import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.core.dispatch.AsyncDispatchBus;
import ie.bitstep.mango.instrument.core.processor.DefaultFlowProcessor;
import ie.bitstep.mango.instrument.core.processor.FlowProcessor;
import ie.bitstep.mango.instrument.core.sinks.FlowHandlerRegistry;
import ie.bitstep.mango.instrument.core.sinks.FlowSinkHandler;
import ie.bitstep.mango.instrument.spring.aspect.FlowAspect;
import ie.bitstep.mango.instrument.spring.scanner.FlowSinkScanner;
import ie.bitstep.mango.instrument.spring.validation.HibernateEntityDetector;
import ie.bitstep.mango.instrument.spring.validation.HibernateEntityLogLevel;
import ie.bitstep.mango.instrument.validation.FlowAttributeValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Role;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
public class MangoInstrumentationConfiguration {

    @Bean
    public FlowProcessorSupport flowProcessorSupport() {
        return new FlowProcessorSupport();
    }

    @Bean
    public FlowContext flowContext(FlowProcessorSupport support) {
        return new FlowContext(support);
    }

    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    public FlowHandlerRegistry flowHandlerRegistry() {
        return new FlowHandlerRegistry();
    }

    @Bean(destroyMethod = "close")
    public AsyncDispatchBus asyncDispatchBus(FlowHandlerRegistry registry) {
        return new AsyncDispatchBus(registry);
    }

    @Bean
    public FlowAttributeValidator flowAttributeValidator() {
        return new HibernateEntityDetector(HibernateEntityLogLevel.ERROR);
    }

    @Bean
    public FlowProcessor flowProcessor(
            AsyncDispatchBus asyncDispatchBus,
            FlowProcessorSupport support,
            FlowAttributeValidator validator) {
        return new DefaultFlowProcessor(asyncDispatchBus, support, validator);
    }

    @Bean
    public FlowAspect flowAspect(FlowProcessor processor, FlowProcessorSupport support) {
        return new FlowAspect(processor, support);
    }

    @Bean
    public static BeanPostProcessor flowSinkHandlerRegistrar(ObjectProvider<FlowHandlerRegistry> registryProvider) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof FlowSinkHandler sink) {
                    registryProvider.getObject().register(sink);
                }
                return bean;
            }
        };
    }

    @Bean
    public static FlowSinkScanner flowSinkScanner(ObjectProvider<FlowHandlerRegistry> registryProvider) {
        return new FlowSinkScanner(registryProvider);
    }
}
