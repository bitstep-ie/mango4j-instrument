package ie.bitstep.mango.instrument.spring;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Role;
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

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
public class MangoInstrumentationConfiguration {

	/** @return shared thread-local storage for active flow and step state */
	@Bean
	public FlowProcessorSupport flowProcessorSupport() {
		return new FlowProcessorSupport();
	}

	/**
	 * @param support shared thread-local state for active flows
	 * @param validator validates attributes/context values pushed via the programmatic API
	 * @return the flow context exposed to application code
	 */
	@Bean
	public FlowContext flowContext(FlowProcessorSupport support, FlowAttributeValidator validator) {
		return new FlowContext(support, validator);
	}

	/** @return registry that maps event types to their registered sink handlers */
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	@Bean
	public FlowHandlerRegistry flowHandlerRegistry() {
		return new FlowHandlerRegistry();
	}

	/**
	 * @param registry the handler registry sink events are dispatched to
	 * @return the async dispatch bus; closed on context shutdown
	 */
	@Bean(destroyMethod = "close")
	public AsyncDispatchBus asyncDispatchBus(FlowHandlerRegistry registry) {
		return new AsyncDispatchBus(registry);
	}

	/** @return validator that detects Hibernate entity misuse on flow attributes */
	@Bean
	public FlowAttributeValidator flowAttributeValidator() {
		return new HibernateEntityDetector(HibernateEntityLogLevel.ERROR);
	}

	/**
	 * @param asyncDispatchBus bus used to fan-out events to registered sink handlers
	 * @param support shared thread-local state for active flows
	 * @param validator validates flow attributes before processing
	 * @return the flow processor
	 */
	@Bean
	public FlowProcessor flowProcessor(
			AsyncDispatchBus asyncDispatchBus, FlowProcessorSupport support, FlowAttributeValidator validator) {
		return new DefaultFlowProcessor(asyncDispatchBus, support, validator);
	}

	/**
	 * @param processor processes flow lifecycle events
	 * @param support shared thread-local state for active flows
	 * @return the AspectJ aspect that intercepts {@code @Flow}-annotated methods
	 */
	@Bean
	public FlowAspect flowAspect(FlowProcessor processor, FlowProcessorSupport support) {
		return new FlowAspect(processor, support);
	}

	/**
	 * {@link BeanPostProcessor} that registers each {@link FlowSinkHandler} bean with the {@link FlowHandlerRegistry}
	 * as it is initialised.
	 *
	 * @param registryProvider lazy provider for the {@link FlowHandlerRegistry}
	 * @return the post-processor
	 */
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

	/**
	 * Scans the application context for {@link FlowSinkHandler} beans and registers them with the
	 * {@link FlowHandlerRegistry}.
	 *
	 * @param registryProvider lazy provider for the {@link FlowHandlerRegistry}
	 * @return the scanner
	 */
	@Bean
	public static FlowSinkScanner flowSinkScanner(ObjectProvider<FlowHandlerRegistry> registryProvider) {
		return new FlowSinkScanner(registryProvider);
	}
}
