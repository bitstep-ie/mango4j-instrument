package ie.bitstep.mango.instrument.spring;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

public class MangoInstrumentationImportSelector implements ImportSelector {
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        List<String> imports = new ArrayList<>();
        imports.add(MangoInstrumentationConfiguration.class.getName());

        if (ClassUtils.isPresent("jakarta.servlet.Filter", getClass().getClassLoader())) {
            imports.add(MangoServletTraceConfiguration.class.getName());
        }
        if (ClassUtils.isPresent("org.springframework.web.server.WebFilter", getClass().getClassLoader())) {
            imports.add(MangoWebFluxTraceConfiguration.class.getName());
        }
        return imports.toArray(String[]::new);
    }
}
