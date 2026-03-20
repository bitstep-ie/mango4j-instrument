package ie.bitstep.mango.instrument.spring.validation;

import ie.bitstep.mango.instrument.validation.FlowAttributeValidator;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HibernateEntityDetector implements FlowAttributeValidator {
    private static final Logger log = LoggerFactory.getLogger(HibernateEntityDetector.class);
    private static final Set<Class<?>> SIMPLE_TYPES = Set.of(
            Boolean.class,
            Byte.class,
            Short.class,
            Integer.class,
            Long.class,
            Float.class,
            Double.class,
            Character.class);
    private static final Set<String> HIBERNATE_PROXY_MARKERS = Set.of("$$_javassist_", "_$$_jvst", "$HibernateProxy$");
    private static final Set<String> ENTITY_ANNOTATIONS =
            Set.of("org.hibernate.annotations.Entity", "jakarta.persistence.Entity", "javax.persistence.Entity");

    private final HibernateEntityLogLevel logLevel;

    public HibernateEntityDetector(HibernateEntityLogLevel logLevel) {
        this.logLevel = logLevel == null ? HibernateEntityLogLevel.ERROR : logLevel;
    }

    @Override
    public void validate(String key, Object value) {
        checkNotHibernateEntity(key, value, logLevel);
    }

    public static void checkNotHibernateEntity(String key, Object value, HibernateEntityLogLevel level) {
        if (value == null) {
            return;
        }
        detect(key, key, value, new IdentityHashMap<>(), level == null ? HibernateEntityLogLevel.ERROR : level);
    }

    private static void detect(
            String root,
            String path,
            Object value,
            Map<Object, Boolean> visited,
            HibernateEntityLogLevel level) {
        if (value == null || visited.containsKey(value)) {
            return;
        }
        Class<?> type = value.getClass();
        if (isHibernateEntity(type)) {
            String message = String.format(
                    "Hibernate entity detected in flow attribute '%s' at path '%s'. Class: %s",
                    root, path, type.getName());
            if (level == HibernateEntityLogLevel.ERROR) {
                throw new IllegalArgumentException(message);
            }
            if (level == HibernateEntityLogLevel.WARN) {
                log.warn(message);
            } else {
                log.info(message);
            }
            return;
        }
        if (isTerminal(type)) {
            return;
        }

        visited.put(value, Boolean.TRUE);

        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                detect(root, path + "[" + entry.getKey() + "]", entry.getValue(), visited, level);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            int index = 0;
            for (Object entry : iterable) {
                detect(root, path + "[" + index + "]", entry, visited, level);
                index++;
            }
            return;
        }
        if (type.isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                detect(root, path + "[" + index + "]", java.lang.reflect.Array.get(value, index), visited, level);
            }
            return;
        }
        if (value instanceof Optional<?> optional) {
            optional.ifPresent(nested -> detect(root, path + ".value", nested, visited, level));
            return;
        }

        for (Field field : getAllFields(type)) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            try {
                if (!field.canAccess(value)) {
                    field.setAccessible(true);
                }
                detect(root, path + "." + field.getName(), field.get(value), visited, level);
            } catch (IllegalAccessException | InaccessibleObjectException ignored) {
            }
        }
    }

    private static boolean isHibernateEntity(Class<?> type) {
        for (String marker : HIBERNATE_PROXY_MARKERS) {
            if (type.getName().contains(marker)) {
                return true;
            }
        }
        try {
            for (java.lang.annotation.Annotation annotation : type.getAnnotations()) {
                if (ENTITY_ANNOTATIONS.contains(annotation.annotationType().getName())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean isTerminal(Class<?> type) {
        return type.isPrimitive()
                || SIMPLE_TYPES.contains(type)
                || CharSequence.class.isAssignableFrom(type)
                || Number.class.isAssignableFrom(type)
                || Enum.class.isAssignableFrom(type)
                || UUID.class.isAssignableFrom(type)
                || Date.class.isAssignableFrom(type)
                || TemporalAccessor.class.isAssignableFrom(type)
                || Class.class.isAssignableFrom(type);
    }

    private static Iterable<Field> getAllFields(Class<?> type) {
        if (type == null || Object.class.equals(type)) {
            return Collections.emptyList();
        }
        Deque<Field> fields = new LinkedList<>();
        Class<?> current = type;
        while (current != null && !Object.class.equals(current)) {
            Collections.addAll(fields, current.getDeclaredFields());
            current = current.getSuperclass();
        }
        return fields;
    }
}
