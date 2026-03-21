package ie.bitstep.mango.instrument.spring.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class HibernateEntityDetectorTest {

    @jakarta.persistence.Entity
    static class EntityType {
        String id = "1";
    }

    static class Wrapper {
        Object payload;

        Wrapper(Object payload) {
            this.payload = payload;
        }
    }

    static class Node {
        Object value;
        Node next;

        Node(Object value) {
            this.value = value;
        }
    }

    static class ParentHolder {
        Object inherited;
    }

    static class ChildHolder extends ParentHolder {
        transient Object ignoredTransient;
        static Object ignoredStatic;
    }

    static class ProxyMarker$HibernateProxy$Thing {
        String id = "1";
    }

    static class ThreadHolder {
        Thread thread = new Thread();
    }

    static class PrivateEntityHolder {
        private final Object nested = new EntityType();
    }

    enum SampleEnum {
        VALUE
    }

    @Test
    void throws_when_nested_entity_is_detected() {
        Wrapper wrapper = new Wrapper(List.of(Map.of("entity", new EntityType()), Optional.empty()));

        assertThatThrownBy(() ->
                HibernateEntityDetector.checkNotHibernateEntity("payload", wrapper, HibernateEntityLogLevel.ERROR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload");
    }

    @Test
    void allows_terminal_and_non_entity_nested_values() {
        Wrapper wrapper = new Wrapper(List.of(Map.of("number", 1, "text", "ok"), Optional.of("value")));

        assertThatCode(() ->
                HibernateEntityDetector.checkNotHibernateEntity("payload", wrapper, HibernateEntityLogLevel.ERROR))
                .doesNotThrowAnyException();
    }

    @Test
    void handles_cycles_without_throwing_for_non_entities() {
        Node first = new Node("ok");
        Node second = new Node(Optional.of("value"));
        first.next = second;
        second.next = first;

        assertThatCode(() ->
                HibernateEntityDetector.checkNotHibernateEntity("payload", first, HibernateEntityLogLevel.ERROR))
                .doesNotThrowAnyException();
    }

    @Test
    void warn_and_info_modes_do_not_throw_when_entity_is_present() {
        assertThatCode(() ->
                HibernateEntityDetector.checkNotHibernateEntity("payload", new EntityType(), HibernateEntityLogLevel.WARN))
                .doesNotThrowAnyException();
        assertThatCode(() ->
                HibernateEntityDetector.checkNotHibernateEntity("payload", new EntityType(), HibernateEntityLogLevel.INFO))
                .doesNotThrowAnyException();
    }

    @Test
    void traverses_superclass_fields_but_ignores_static_and_transient_fields() {
        ChildHolder holder = new ChildHolder();
        holder.inherited = new EntityType();
        holder.ignoredTransient = new EntityType();
        ChildHolder.ignoredStatic = new EntityType();

        assertThatThrownBy(() ->
                HibernateEntityDetector.checkNotHibernateEntity("payload", holder, HibernateEntityLogLevel.ERROR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload.inherited");
    }

    @Test
    void allows_terminal_arrays_and_optional_empty_values() {
        Object[] payload = {UUID.randomUUID(), "ok", Optional.empty(), new int[] {1, 2, 3}};

        assertThatCode(() ->
                HibernateEntityDetector.checkNotHibernateEntity("payload", payload, HibernateEntityLogLevel.ERROR))
                .doesNotThrowAnyException();
    }

    @Test
    void uses_error_level_when_detector_is_constructed_with_null() {
        HibernateEntityDetector detector = new HibernateEntityDetector(null);

        assertThatThrownBy(() -> detector.validate("payload", new EntityType()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload");
    }

    @Test
    void uses_error_level_when_static_check_receives_null_level() {
        assertThatThrownBy(() -> HibernateEntityDetector.checkNotHibernateEntity("payload", new EntityType(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload");
    }

    @Test
    void ignores_null_values_and_terminal_types_via_validator_entrypoint() {
        HibernateEntityDetector detector = new HibernateEntityDetector(HibernateEntityLogLevel.ERROR);

        assertThatCode(() -> detector.validate("payload", null)).doesNotThrowAnyException();
        assertThatCode(() -> detector.validate("payload", SampleEnum.VALUE)).doesNotThrowAnyException();
        assertThatCode(() -> detector.validate("payload", HibernateEntityDetector.class)).doesNotThrowAnyException();
    }

    @Test
    void detects_proxy_marker_names_and_allows_other_terminal_types() {
        assertThatThrownBy(() -> HibernateEntityDetector.checkNotHibernateEntity(
                        "payload", new ProxyMarker$HibernateProxy$Thing(), HibernateEntityLogLevel.ERROR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ProxyMarker$HibernateProxy$Thing");

        assertThatCode(() ->
                HibernateEntityDetector.checkNotHibernateEntity("payload", java.util.Date.from(Instant.now()), null))
                .doesNotThrowAnyException();
        assertThatCode(() ->
                HibernateEntityDetector.checkNotHibernateEntity("payload", Instant.now(), HibernateEntityLogLevel.ERROR))
                .doesNotThrowAnyException();
        assertThatCode(() ->
                HibernateEntityDetector.checkNotHibernateEntity("payload", new Object(), HibernateEntityLogLevel.ERROR))
                .doesNotThrowAnyException();
        assertThatCode(() ->
                HibernateEntityDetector.checkNotHibernateEntity("payload", new ThreadHolder(), HibernateEntityLogLevel.ERROR))
                .doesNotThrowAnyException();
    }

    @Test
    void inspects_private_fields_when_reflection_access_needs_to_be_opened() {
        assertThatThrownBy(() -> HibernateEntityDetector.checkNotHibernateEntity(
                        "payload", new PrivateEntityHolder(), HibernateEntityLogLevel.ERROR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload.nested");
    }

    @Test
    void ignores_broken_runtime_annotations_when_entity_introspection_fails() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path tempDir = Files.createTempDirectory("hibernate-detector-broken-annotation");
        Path packageDir = Files.createDirectories(tempDir.resolve("dynamic"));
        Path annotationSource = packageDir.resolve("BrokenMarker.java");
        Path targetSource = packageDir.resolve("BrokenAnnotatedType.java");

        Files.writeString(
                annotationSource,
                """
                package dynamic;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE)
                public @interface BrokenMarker {
                }
                """);
        Files.writeString(
                targetSource,
                """
                package dynamic;

                @BrokenMarker
                public class BrokenAnnotatedType {
                }
                """);

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            boolean compiled = compiler.getTask(
                            null,
                            fileManager,
                            null,
                            List.of("-d", tempDir.toString()),
                            null,
                            fileManager.getJavaFileObjects(annotationSource.toFile(), targetSource.toFile()))
                    .call();
            if (!compiled) {
                throw new IllegalStateException("dynamic compilation failed");
            }
        }

        Files.delete(tempDir.resolve("dynamic/BrokenMarker.class"));

        try (URLClassLoader classLoader =
                new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> brokenType = Class.forName("dynamic.BrokenAnnotatedType", true, classLoader);
            Object instance = brokenType.getDeclaredConstructor().newInstance();

            assertThatCode(() -> HibernateEntityDetector.checkNotHibernateEntity(
                            "payload", instance, HibernateEntityLogLevel.ERROR))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void private_type_helpers_cover_remaining_terminal_and_field_cases() throws Exception {
        Method isTerminal = HibernateEntityDetector.class.getDeclaredMethod("isTerminal", Class.class);
        Method getAllFields = HibernateEntityDetector.class.getDeclaredMethod("getAllFields", Class.class);
        isTerminal.setAccessible(true);
        getAllFields.setAccessible(true);

        assertThatCode(() -> isTerminal.invoke(null, int.class)).doesNotThrowAnyException();
        assertThatCode(() -> isTerminal.invoke(null, BigDecimal.class)).doesNotThrowAnyException();
        assertThatCode(() -> getAllFields.invoke(null, new Object[] {null})).doesNotThrowAnyException();
        assertThatCode(() -> getAllFields.invoke(null, Object.class)).doesNotThrowAnyException();
        assertThatCode(() -> getAllFields.invoke(null, ChildHolder.class)).doesNotThrowAnyException();
    }
}
