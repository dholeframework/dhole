package org.dhole.internal.compiler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.StandardLocation;

import com.sun.source.util.Trees;

/**
 * Dhole metadata compiler integration for {@code javac}.
 *
 * <p>Observes the types being compiled structurally; application code needs no annotations and is
 * never loaded. With {@code -Adhole.application=<fully qualified class>} it writes
 * {@code META-INF/dhole/components.idx} for every concrete class in the application root package
 * and its subpackages. Without the option the compilation is a library compilation and nothing is
 * written. Claims no annotations, so other processors are unaffected.
 */
public final class MetadataProcessor extends AbstractProcessor {

    public static final String APPLICATION_OPTION = "dhole.application";

    private final Set<String> compiledTypes = new TreeSet<>();
    private Optional<String> application = Optional.empty();
    private boolean failed;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        String option = processingEnv.getOptions().get(APPLICATION_OPTION);
        if (option == null) {
            return;
        }
        if (!SourceVersion.isName(option)) {
            report(new Diagnostic(DiagnosticCode.INVALID_APPLICATION, DiagnosticSeverity.ERROR,
                    "Invalid -A" + APPLICATION_OPTION + " value '" + option
                            + "': expected a fully qualified class name such as com.acme.shop.App"));
            return;
        }
        application = Optional.of(option);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of(APPLICATION_OPTION);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (application.isEmpty() || failed) {
            return false;
        }
        if (!round.processingOver()) {
            for (TypeElement type : ElementFilter.typesIn(round.getRootElements())) {
                collect(type);
            }
            return false;
        }
        generate();
        return false;
    }

    private void collect(TypeElement type) {
        compiledTypes.add(type.getQualifiedName().toString());
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed instanceof TypeElement nested) {
                collect(nested);
            }
        }
    }

    private void generate() {
        String applicationClass = application.orElseThrow();
        TypeElement applicationType = processingEnv.getElementUtils().getTypeElement(applicationClass);
        if (applicationType == null) {
            report(new Diagnostic(DiagnosticCode.INVALID_APPLICATION, DiagnosticSeverity.ERROR,
                    "Application class " + applicationClass + " configured with -A" + APPLICATION_OPTION
                            + " does not exist"));
            return;
        }
        ApplicationRoot root = new ApplicationRoot(applicationClass,
                processingEnv.getElementUtils().getPackageOf(applicationType).getQualifiedName().toString());
        ComponentAnalyzer analyzer = new ComponentAnalyzer(processingEnv.getElementUtils(),
                processingEnv.getTypeUtils(), trees());
        List<ComponentRecord> records = new ArrayList<>();
        for (String name : compiledTypes) {
            TypeElement type = processingEnv.getElementUtils().getTypeElement(name);
            if (type != null && ComponentAnalyzer.isConcreteClass(type)
                    && root.contains(processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString())) {
                records.add(analyzer.analyze(type));
            }
        }
        write(ComponentIndexWriter.LOCATION, ComponentIndexWriter.write(records));

        Trees trees = trees();
        List<RouteRecord> routes = new ArrayList<>();
        if (trees != null) {
            RouteAnalyzer routeAnalyzer = new RouteAnalyzer(processingEnv.getElementUtils(),
                    processingEnv.getTypeUtils(), trees);
            for (ComponentRecord record : records) {
                if (record.supertypes().contains(RouteAnalyzer.CONTROLLER)) {
                    routes.addAll(routeAnalyzer.analyze(
                            processingEnv.getElementUtils().getTypeElement(record.type().replace('$', '.'))));
                }
            }
            for (RouteAnalyzer.Problem problem : routeAnalyzer.problems()) {
                failed = true;
                trees.printMessage(javax.tools.Diagnostic.Kind.ERROR, problem.diagnostic().format(), problem.tree(),
                        problem.unit());
            }
        }
        write(RouteIndexWriter.LOCATION, RouteIndexWriter.write(routes));

        List<ValidationRecord> validations = new ArrayList<>();
        if (trees != null) {
            ValidationAnalyzer validationAnalyzer = new ValidationAnalyzer(processingEnv.getElementUtils(),
                    processingEnv.getTypeUtils(), trees);
            for (ComponentRecord record : records) {
                if (record.supertypes().contains(ValidationAnalyzer.VALIDATABLE)) {
                    validationAnalyzer.analyze(
                            processingEnv.getElementUtils().getTypeElement(record.type().replace('$', '.')))
                            .ifPresent(validations::add);
                }
            }
            for (RouteAnalyzer.Problem problem : validationAnalyzer.problems()) {
                failed = true;
                trees.printMessage(javax.tools.Diagnostic.Kind.ERROR, problem.diagnostic().format(), problem.tree(),
                        problem.unit());
            }
        }
        write(ValidationRecord.LOCATION, ValidationRecord.write(validations));
    }

    private void write(String location, String index) {
        try (OutputStream output = processingEnv.getFiler()
                .createResource(StandardLocation.CLASS_OUTPUT, "", location)
                .openOutputStream()) {
            output.write(index.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            report(new Diagnostic(null, DiagnosticSeverity.ERROR, "Unable to write " + location + ": " + e.getMessage()));
        }
    }

    private Trees trees() {
        try {
            return Trees.instance(processingEnv);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void report(Diagnostic diagnostic) {
        if (diagnostic.severity() == DiagnosticSeverity.ERROR) {
            failed = true;
        }
        processingEnv.getMessager().printMessage(
                diagnostic.severity() == DiagnosticSeverity.ERROR
                        ? javax.tools.Diagnostic.Kind.ERROR
                        : javax.tools.Diagnostic.Kind.WARNING,
                diagnostic.format());
    }
}
