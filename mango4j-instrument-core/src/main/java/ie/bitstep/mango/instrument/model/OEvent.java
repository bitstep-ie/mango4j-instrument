package ie.bitstep.mango.instrument.model;

public class OEvent {
    private final String name;
    private final long timeUnixNano;
    private long endTimeUnixNano;
    private long elapsedNanos;
    private final OAttributes attributes;
    private final String kind;

    public OEvent(String name, long timeUnixNano, OAttributes attributes, String kind) {
        this.name = name;
        this.timeUnixNano = timeUnixNano;
        this.attributes = attributes == null ? new OAttributes() : attributes;
        this.kind = kind;
    }

    public String name() {
        return name;
    }

    public long timeUnixNano() {
        return timeUnixNano;
    }

    public long endTimeUnixNano() {
        return endTimeUnixNano;
    }

    public void setEndTimeUnixNano(long endTimeUnixNano) {
        this.endTimeUnixNano = endTimeUnixNano;
    }

    public long elapsedNanos() {
        return elapsedNanos;
    }

    public void setElapsedNanos(long elapsedNanos) {
        this.elapsedNanos = elapsedNanos;
    }

    public OAttributes attributes() {
        return attributes;
    }

    public String kind() {
        return kind;
    }
}
