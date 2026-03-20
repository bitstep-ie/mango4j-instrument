package ie.bitstep.mango.instrument.core.sinks;

import ie.bitstep.mango.instrument.model.FlowEvent;

@FunctionalInterface
public interface FlowSinkHandler {
    void handle(FlowEvent event) throws Exception;
}
