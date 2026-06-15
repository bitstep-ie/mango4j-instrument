package ie.bitstep.mango.instrument.spring;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MangoInstrumentationImportSelectorTest {

	private static MangoInstrumentationImportSelector withAbsentClasses(Set<String> absent) {
		return new MangoInstrumentationImportSelector() {
			@Override
			protected ClassLoader classLoader() {
				return new ClassLoader(Thread.currentThread().getContextClassLoader()) {
					@Override
					protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
						if (absent.contains(name)) {
							throw new ClassNotFoundException(name);
						}
						return super.loadClass(name, resolve);
					}
				};
			}
		};
	}

	@Test
	void includes_all_configs_when_all_optional_deps_are_present() {
		String[] result = new MangoInstrumentationImportSelector().selectImports(null);

		assertThat(result)
				.contains(
						MangoInstrumentationConfiguration.class.getName(),
						MangoServletTraceConfiguration.class.getName(),
						MangoServletFlowConfiguration.class.getName(),
						MangoWebFluxTraceConfiguration.class.getName());
	}

	@Test
	void omits_servlet_configs_when_jakarta_servlet_is_absent() {
		MangoInstrumentationImportSelector selector = withAbsentClasses(
				Set.of("jakarta.servlet.Filter", "org.springframework.web.servlet.HandlerInterceptor"));

		String[] result = selector.selectImports(null);

		assertThat(result)
				.contains(
						MangoInstrumentationConfiguration.class.getName(),
						MangoWebFluxTraceConfiguration.class.getName())
				.doesNotContain(
						MangoServletTraceConfiguration.class.getName(), MangoServletFlowConfiguration.class.getName());
	}

	@Test
	void omits_webflux_config_when_webfilter_is_absent() {
		MangoInstrumentationImportSelector selector =
				withAbsentClasses(Set.of("org.springframework.web.server.WebFilter"));

		String[] result = selector.selectImports(null);

		assertThat(result)
				.contains(
						MangoInstrumentationConfiguration.class.getName(),
						MangoServletTraceConfiguration.class.getName(),
						MangoServletFlowConfiguration.class.getName())
				.doesNotContain(MangoWebFluxTraceConfiguration.class.getName());
	}

	@Test
	void returns_only_base_config_when_no_optional_deps_are_present() {
		MangoInstrumentationImportSelector selector = withAbsentClasses(Set.of(
				"jakarta.servlet.Filter",
				"org.springframework.web.servlet.HandlerInterceptor",
				"org.springframework.web.server.WebFilter"));

		String[] result = selector.selectImports(null);

		assertThat(result).containsExactly(MangoInstrumentationConfiguration.class.getName());
	}
}
