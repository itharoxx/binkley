package hm.binkley.annotation.processing;

import freemarker.cache.URLTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import hm.binkley.annotation.YamlGenerate;
import hm.binkley.util.YamlHelper;
import org.kohsuke.MetaInfServices;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static freemarker.template.Configuration.VERSION_2_3_21;
import static freemarker.template.TemplateExceptionHandler.DEBUG_HANDLER;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static java.util.regex.Pattern.compile;
import static javax.lang.model.SourceVersion.RELEASE_8;
import static javax.lang.model.element.ElementKind.INTERFACE;
import static javax.lang.model.element.ElementKind.PACKAGE;

/**
 * {@code YamlGenerateProcessor} <b>needs documentation</b>.
 *
 * @author <a href="mailto:binkley@alumni.rice.edu">B. K. Oxley (binkley)</a>
 * @todo Needs documentation.
 * @todo Parse "freemarker.version" from Maven to construct latest Version
 */
@SupportedAnnotationTypes("hm.binkley.annotation.YamlGenerate")
@SupportedSourceVersion(RELEASE_8)
@MetaInfServices(Processor.class)
public final class YamlGenerateProcessor
        extends AbstractProcessor {
    private static final Pattern space = compile("\\s+");
    // In context, system class loader does not have maven class path
    private final ResourcePatternResolver loader
            = new PathMatchingResourcePatternResolver(
            YamlGenerateProcessor.class.getClassLoader());
    // TODO: How to configure the builder with implicits?
    private final Yaml yaml = YamlHelper.builder().build();
    private final Configuration freemarker;

    // TODO: Rework to avoid writeable state
    private YamlGenerateMesseger out;

    public YamlGenerateProcessor() {
        freemarker = new Configuration(VERSION_2_3_21);
        freemarker.setTemplateLoader(new ResourceTemplateLoader());
        freemarker.setDefaultEncoding(UTF_8.name());
        // Dump stacktrace as only called during compilation, not runtime
        freemarker.setTemplateExceptionHandler(DEBUG_HANDLER);
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations,
            final RoundEnvironment roundEnv) {
        for (final Element element : roundEnv
                .getElementsAnnotatedWith(YamlGenerate.class)) {
            out = YamlGenerateMesseger
                    .from(processingEnv.getMessager(), element);

            if (INTERFACE != element.getKind()) {
                out.error("%@ only supported on interfaces");
                continue;
            }

            if (PACKAGE != element.getEnclosingElement().getKind()) {
                out.error("%@ only supports top-level interfaces");
                continue;
            }

            // Use both annotation and mirror:
            // - Annotation is easier for accessing members
            // - Mirror is needed for messenger

            final AnnotationMirror aMirror = oneOnly(element);
            if (null == aMirror)
                continue;

            out = out.withAnnotation(aMirror,
                    annotationValue(aMirror, "template"));

            final YamlGenerate anno = element
                    .getAnnotation(YamlGenerate.class);
            final String[] inputs = anno.inputs();
            final String namespace = anno.namespace();

            try {
                final Resource ftl = loader.getResource(anno.template());
                out = out.withTemplate(ftl);
                final Template template = freemarker
                        .getTemplate(anno.template());

                try {
                    final Name packaj = processingEnv.getElementUtils()
                            .getName(namespace);

                    for (final String input : inputs)
                        process(element, template, packaj, input);
                } catch (final Exception e) {
                    out.error(e, "Cannot process");
                }
            } catch (final Exception e) {
                out.error(e, "Cannot create template");
            }
        }

        return true;
    }

    private AnnotationMirror oneOnly(final Element element) {
        // TODO: How to do this without resorting to toString()?
        final List<AnnotationMirror> found = element.
                getAnnotationMirrors().stream().
                filter(m -> m.getAnnotationType().
                        toString().
                        equals(YamlGenerate.class.getCanonicalName())).
                collect(Collectors.<AnnotationMirror>toList());

        switch (found.size()) {
        case 1:
            return found.get(0);
        case 0:
            out.error("%@ missing from element");
            return null;
        default:
            out.error("%@ only supports 1 occurrence");
            return null;
        }
    }

    private static AnnotationValue annotationValue(
            final AnnotationMirror aMirror, final String param) {
        return aMirror.getElementValues().entrySet().stream().
                filter(e -> e.getKey().getSimpleName().contentEquals(param)).
                map(Map.Entry::getValue).
                findAny().
                orElse(null);
    }

    private void process(final Element cause, final Template template,
            final Name packaj, final String input)
            throws IOException {
        for (final Loaded loaded : loadAll(input)) {
            out = out.withYaml(loaded.whence);
            for (final Entry<String, Object> each : loaded.doc.entrySet()) {
                final String key = each.getKey();
                final String[] names = space.split(key);
                final String name;
                final String parent;
                switch (names.length) {
                case 1:
                    name = names[0];
                    parent = null;
                    break;
                case 2:
                    name = names[0];
                    parent = names[1];
                    break;
                default:
                    out.error(
                            "Classes have at most one parent for '%s' in %s",
                            key, loaded.whence.getURI());
                    return;
                }

                final Object value = each.getValue();
                try {
                    if (value instanceof List) {
                        if (null != parent) {
                            out.error(
                                    "Enums cannot have a parent for '%s' in %s",
                                    key, loaded.whence.getURI());
                            return;
                        }
                        buildEnum(cause, template, loaded.whence, packaj,
                                name, cast(value));
                    } else if (value instanceof Map)
                        buildClass(cause, template, loaded.whence, packaj,
                                name, parent, cast(value));
                    else
                        out.error(
                                "%@ only supports list (enum) and map (class), not (%s) %s",
                                value.getClass(), value);
                } catch (final Exception e) {
                    out.error(e, "Failed for %s -> %s", name, value);
                }
            }
        }
    }

    private void buildEnum(final Element cause, final Template template,
            final Resource whence, final Name packaj, final String name,
            final List<String> values)
            throws IOException, TemplateException {
        final String fullName = fullName(packaj, name);
        try (final Writer writer = new OutputStreamWriter(
                processingEnv.getFiler().createSourceFile(fullName, cause)
                        .openOutputStream())) {
            out.note("Writing enum %s", fullName);
            template.process(
                    modelEnum(template.getName(), whence.getURI().toString(),
                            packaj.toString(), name, null, values), writer);
        }
    }

    private static Map<String, Object> modelEnum(final String ftl,
            final String yml, final String packaj, final String name,
            final String parent, final List<?> values) {
        final Map<String, Object> model = commonModel(ftl, yml, packaj, name,
                parent);
        model.put("type", Enum.class.getSimpleName());
        // TODO: Support doc strings on enums in the YAML
        model.put("values", values);
        return model;
    }

    private void buildClass(final Element cause, final Template template,
            final Resource whence, final Name packaj, final String name,
            final String parent, final Map<String, Map<String, Object>> data)
            throws IOException, TemplateException {
        final String fullName = fullName(packaj, name);
        try (final Writer writer = new OutputStreamWriter(
                processingEnv.getFiler().createSourceFile(fullName, cause)
                        .openOutputStream())) {
            template.process(
                    modelClass(template.getName(), whence.getURI().toString(),
                            packaj.toString(), name, parent, data), writer);
        }
    }

    /** @todo Yucky code */
    private static Map<String, Object> modelClass(final String ftl,
            final String yml, final String packaj, final String name,
            final String parent,
            final Map<String, Map<String, Object>> data) {
        final Map<String, Object> model = commonModel(ftl, yml, packaj, name,
                parent);
        model.put("type", Class.class.getSimpleName());
        if (null == data)
            model.put("data", emptyMap());
        else {
            for (final Entry<String, Map<String, Object>> datum : data
                    .entrySet()) {
                final Map<String, Object> props = datum.getValue();
                final TypedValue pair = model(datum.getKey(),
                        (String) props.get("type"), props.get("value"));
                props.put("type", pair.type);
                props.put("value", pair.value);
            }
            model.put("data", data);
        }
        return model;
    }

    private static final class TypedValue {
        private final String type;
        private final Object value;

        private TypedValue(final String type, final Object value) {
            this.type = type;
            this.value = value;
        }
    }

    private static TypedValue model(final String key, final String type,
            final Object value) {
        if (null == value) {
            if (null == type)
                throw new IllegalStateException(
                        format("Missing value and type for '%s", key));
            switch (type) {
            case "int":
                return new TypedValue("int", 0);
            case "double":
                return new TypedValue("double", 0.0d);
            case "list":
                return new TypedValue("list", new ArrayList<>(0));
            case "map":
                return new TypedValue("map", new LinkedHashMap<>(0));
            default:
                return new TypedValue(type, null);
            }
        } else if (null != type)
            // TODO: Actually should be OK for implicit UDT (e.g., Dice)
            throw new IllegalStateException(
                    format("TODO: Both value and type are provided for '%s'",
                            key));
        else if (value instanceof String)
            return new TypedValue("text", value);
        else if (value instanceof Integer)
            return new TypedValue("int", value);
        else if (value instanceof Double)
            return new TypedValue("double", value);
        else if (value instanceof List)
            return new TypedValue("list", value);
        else if (value instanceof Map)
            return new TypedValue("map", value);
        else
            throw new IllegalStateException(
                    format("TODO: Support UDT of '%s' for '%s'",
                            value.getClass().getName(), key));
    }

    private static Map<String, Object> commonModel(final String ftl,
            final String yml, final String packaj, final String name,
            final String parent) {
        return new HashMap<String, Object>() {{
            put("generator", YamlGenerateProcessor.class.getName());
            put("now", Instant.now().toString());
            put("comments", format("From '%s' using '%s'", yml, ftl));
            put("package", packaj);
            put("name", name);
            put("parent", parent);
        }};
    }

    private static String fullName(final Name packaj, final String name) {
        return 0 == packaj.length() ? name : packaj + "." + name;
    }

    private List<Loaded> loadAll(final String pattern) {
        final List<Loaded> docs = new ArrayList<>();
        try {
            for (final Resource resource : loader.getResources(pattern))
                try (final InputStream in = resource.getInputStream()) {
                    for (final Object doc : yaml.loadAll(in))
                        docs.add(new Loaded(resource, cast(doc)));
                }
        } catch (final IOException e) {
            out.error(e, "Cannot load");
        }
        return docs;
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(final Object o) {
        return (T) o;
    }

    private static final class Loaded {
        private final Resource whence;
        private final Map<String, Object> doc;

        private Loaded(final Resource whence, final Map<String, Object> doc) {
            this.whence = whence;
            this.doc = doc;
        }

        @Override
        public String toString() {
            return whence.getDescription() + ": " + doc;
        }
    }

    private class ResourceTemplateLoader
            extends URLTemplateLoader {
        @Override
        protected URL getURL(final String name) {
            final Resource resource = loader.getResource(name);
            try {
                return resource.exists() ? resource.getURL() : null;
            } catch (final IOException e) {
                out.error(e, "Cannot load FTL template from '%s'",
                        resource.getDescription());
                return null;
            }
        }
    }
}
