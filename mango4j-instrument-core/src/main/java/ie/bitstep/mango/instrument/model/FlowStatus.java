package ie.bitstep.mango.instrument.model;

import io.opentelemetry.api.trace.StatusCode;

public record FlowStatus(StatusCode code, String message) {}
