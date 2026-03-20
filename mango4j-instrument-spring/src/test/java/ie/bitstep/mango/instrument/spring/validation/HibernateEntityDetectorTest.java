package ie.bitstep.mango.instrument.spring.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
}
