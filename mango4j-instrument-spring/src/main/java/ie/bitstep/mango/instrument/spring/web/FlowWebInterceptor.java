package ie.bitstep.mango.instrument.spring.web;

import ie.bitstep.mango.instrument.annotations.Flow;
import ie.bitstep.mango.instrument.annotations.Kind;
import ie.bitstep.mango.instrument.core.FlowProcessorSupport;
import ie.bitstep.mango.instrument.core.processor.FlowMeta;
import ie.bitstep.mango.instrument.core.processor.FlowProcessor;
import ie.bitstep.mango.instrument.model.FlowEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * A Spring MVC {@link HandlerInterceptor} that integrates {@link Flow}-annotated controller
 * methods with the mango4j-instrument flow lifecycle.
 *
 * <p>When a request arrives at a controller method that carries {@code @Flow}:
 * <ol>
 *   <li>{@code preHandle} — calls {@link FlowProcessor#onFlowStarted} with the resolved flow
 *       name and HTTP metadata (method, URI, URL mapping) stored in the event context.</li>
 *   <li>{@code afterCompletion} — calls {@link FlowProcessor#onFlowCompleted} (or
 *       {@link FlowProcessor#onFlowFailed} when an exception was raised or the response
 *       carries a 4xx/5xx status) and records the HTTP status code in the event context.</li>
 * </ol>
 *
 * <p>Non-{@code @Flow} handler methods pass through without any instrumentation.
 *
 * <p>Because this interceptor creates the {@link FlowEvent} <em>before</em> the AOP
 * {@code FlowAspect} runs, the aspect detects an already-active flow and records only the
 * nested step/span — it does <strong>not</strong> start a duplicate root flow.
 *
 * @see FlowProcessor
 * @see ie.bitstep.mango.instrument.spring.aspect.FlowAspect
 */
public class FlowWebInterceptor implements HandlerInterceptor {

    static final String CTX_HTTP_KEY = "http";
    static final String CTX_HTTP_METHOD = "method";
    static final String CTX_HTTP_URI = "uri";
    static final String CTX_HTTP_MAPPING = "mapping";
    static final String CTX_HTTP_STATUS = "status";

    private static final Logger log = LoggerFactory.getLogger(FlowWebInterceptor.class);

    /**
     * Request attribute used to pass the resolved {@link Flow} name from {@code preHandle}
     * through to {@code afterCompletion} without extra state.
     */
    private static final String ATTR_FLOW_NAME = FlowWebInterceptor.class.getName() + ".flowName";

    private final FlowProcessor processor;
    private final FlowProcessorSupport support;

    public FlowWebInterceptor(FlowProcessor processor, FlowProcessorSupport support) {
        this.processor = processor;
        this.support = support;
    }

    // -------------------------------------------------------------------------
    // HandlerInterceptor
    // -------------------------------------------------------------------------

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        support.cleanupThreadLocals(); // ensure a clean slate for every inbound request

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        Method method = handlerMethod.getMethod();
        Flow flow = method.getAnnotation(Flow.class);
        if (flow == null) {
            return true;
        }

        String flowName = resolveFlowName(flow, method);
        request.setAttribute(ATTR_FLOW_NAME, flowName);

        FlowMeta meta = buildStartMeta(method);
        Map<String, Object> httpCtx = buildHttpContext(request);

        processor.onFlowStarted(flowName, Map.of(), httpCtx, meta);

        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) {

        String flowName = (String) request.getAttribute(ATTR_FLOW_NAME);
        if (flowName == null) {
            return; // handler was not @Flow-annotated
        }

        try {
            FlowEvent current = support.currentContext();

            int statusCode = response.getStatus();
            boolean clientOrServerError = statusCode >= 400;

            Map<String, Object> httpCtx = Map.of(CTX_HTTP_STATUS, String.valueOf(statusCode));

            if (ex != null || clientOrServerError) {
                FlowMeta meta = FlowMeta.builder()
                        .status("ERROR", ex != null ? ex.getMessage() : String.valueOf(statusCode))
                        .build();
                processor.onFlowFailed(flowName, ex, Map.of(), httpCtx, meta);
            } else {
                FlowMeta meta = FlowMeta.builder().status("OK", null).build();
                processor.onFlowCompleted(flowName, Map.of(), httpCtx, meta);
            }
        } catch (Exception completionEx) {
            log.error("FlowWebInterceptor.afterCompletion failed for flow '{}'", flowName, completionEx);
        } finally {
            support.cleanupThreadLocals();
            request.removeAttribute(ATTR_FLOW_NAME);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String resolveFlowName(Flow flow, Method method) {
        if (!flow.name().isBlank()) {
            return flow.name();
        }
        if (!flow.value().isBlank()) {
            return flow.value();
        }
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    private static FlowMeta buildStartMeta(Method method) {
        Kind kindAnnotation = method.getAnnotation(Kind.class);
        FlowMeta.Builder builder = FlowMeta.builder();
        if (kindAnnotation != null && kindAnnotation.value() != null) {
            builder.kind(kindAnnotation.value().name());
        } else {
            builder.kind("SERVER"); // controllers are SERVER spans by default
        }
        return builder.build();
    }

    private static Map<String, Object> buildHttpContext(HttpServletRequest request) {
        String mapping = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return Map.of(
                CTX_HTTP_KEY, Map.of(
                        CTX_HTTP_METHOD, request.getMethod(),
                        CTX_HTTP_URI, request.getRequestURI(),
                        CTX_HTTP_MAPPING, mapping != null ? mapping : request.getRequestURI()));
    }
}
