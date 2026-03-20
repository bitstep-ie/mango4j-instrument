package ie.bitstep.mango.instrument.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class OAttributes {
    private final Map<String, Object> attributes;

    public OAttributes() {
        this(new LinkedHashMap<>());
    }

    public OAttributes(Map<String, Object> attributes) {
        this.attributes = new LinkedHashMap<>(attributes == null ? Map.of() : attributes);
    }

    public void put(String key, Object value) {
        attributes.put(key, value);
    }

    public Map<String, Object> map() {
        return attributes;
    }
}
