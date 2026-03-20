package ie.bitstep.mango.instrument.model;

import io.opentelemetry.api.trace.StatusCode;

public record OStatus(StatusCode code, String message) {
}
