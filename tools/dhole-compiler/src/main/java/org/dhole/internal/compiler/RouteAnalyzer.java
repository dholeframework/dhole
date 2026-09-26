package org.dhole.internal.compiler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

/**
 * Derives {@link RouteRecord}s from a controller's {@code routes(Router)} body, for the typed form
 * {@code routes.<verb>("path").to(this::method)} only (METADATA_COMPILER.md §34.3).
 *
 * <p>Not a Java interpreter: the path must be a string literal or constant; group prefixes are
 * followed only through {@code router.group("constant", lambdaParameter -> ...)}. Parameter sources
 * are classified conservatively (PARAMETER_BINDING.md §38). Raw {@code Request} routes are ignored.
 */
final class RouteAnalyzer {

    static final String CONTROLLER = "org.dhole.web.Controller";
    private static final String ROUTER = "org.dhole.routing.Router";
    private static final String ROUTE_BUILDER = "org.dhole.routing.RouteBuilder";
    private static final String REQUEST = "org.dhole.http.Request";
    private static final Map<String, String> WRAPPERS = Map.of(
            "org.dhole.web.Path", "PATH",
            "org.dhole.web.Query", "QUERY",
            "org.dhole.web.Header", "HEADER",
            "org.dhole.web.Body", "BODY");
    private static final Set<String> SCALARS = Set.of(
            "java.lang.String", "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long",
            "java.lang.Float", "java.lang.Double", "java.lang.Boolean", "java.util.UUID",
            "java.time.LocalDate", "java.time.LocalDateTime", "java.time.Instant");
    private static final Set<String> VERBS = Set.of("get", "post", "put", "patch", "delete");
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");

    /**
     * A problem found in a controller, at a source position.
     */
    record Problem(Diagnostic diagnostic, Tree tree, CompilationUnitTree unit) {
    }

    private final Elements elements;
    private final Types types;
    private final Trees trees;
    private final List<Problem> problems = new ArrayList<>();

    RouteAnalyzer(Elements elements, Types types, Trees trees) {
        this.elements = elements;
        this.types = types;
        this.trees = trees;
    }

    List<Problem> problems() {
        return List.copyOf(problems);
    }

    /**
     * Returns the typed routes declared by {@code controller}'s {@code routes(Router)} method, found
     * in the controller or its superclasses; empty when that method is not in this compilation.
     */
    List<RouteRecord> analyze(TypeElement controller) {
        Optional<ExecutableElement> routes = routesMethod(controller);
        if (routes.isEmpty()) {
            return List.of();
        }
        TreePath path = trees.getPath(routes.get());
        if (path == null) {
            return List.of();
        }
        List<RouteRecord> records = new ArrayList<>();
        Scanner scanner = new Scanner(elements.getBinaryName(controller).toString(), path.getCompilationUnit(), records);
        scanner.routers.put(routes.get().getParameters().get(0), Optional.of(""));
        scanner.scan(path, null);
        return records;
    }

    private Optional<ExecutableElement> routesMethod(TypeElement type) {
        TypeElement current = type;
        while (current != null) {
            for (ExecutableElement method : ElementFilter.methodsIn(current.getEnclosedElements())) {
                if (method.getSimpleName().contentEquals("routes") && method.getParameters().size() == 1
                        && isType(method.getParameters().get(0).asType(), ROUTER)
                        && !method.getModifiers().contains(Modifier.ABSTRACT)) {
                    return Optional.of(method);
                }
            }
            TypeMirror superclass = current.getSuperclass();
            current = superclass.getKind() == TypeKind.DECLARED ? (TypeElement) types.asElement(superclass) : null;
        }
        return Optional.empty();
    }

    private final class Scanner extends TreePathScanner<Void, Void> {

        private final String controller;
        private final CompilationUnitTree unit;
        private final List<RouteRecord> records;
        private final Map<Element, Optional<String>> routers = new HashMap<>();

        Scanner(String controller, CompilationUnitTree unit, List<RouteRecord> records) {
            this.controller = controller;
            this.unit = unit;
            this.records = records;
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
            Element invoked = trees.getElement(getCurrentPath());
            if (invoked instanceof ExecutableElement method) {
                String owner = ((TypeElement) method.getEnclosingElement()).getQualifiedName().toString();
                if (owner.equals(ROUTER) && method.getSimpleName().contentEquals("group") && node.getArguments().size() == 2) {
                    group(node);
                } else if (owner.equals(ROUTE_BUILDER) && method.getSimpleName().contentEquals("to")) {
                    typedRoute(node);
                }
            }
            return super.visitMethodInvocation(node, unused);
        }

        private void group(MethodInvocationTree node) {
            if (!(node.getArguments().get(1) instanceof LambdaExpressionTree lambda) || lambda.getParameters().size() != 1) {
                return;
            }
            Optional<String> base = router(node);
            Optional<String> prefix = constant(node.getArguments().get(0));
            Optional<String> joined = base.isPresent() && prefix.isPresent()
                    ? Optional.of(join(base.get(), prefix.get()))
                    : Optional.empty();
            TreePath lambdaPath = new TreePath(getCurrentPath(), lambda);
            Element parameter = trees.getElement(new TreePath(lambdaPath, lambda.getParameters().get(0)));
            routers.put(parameter, joined.map(value -> value.equals("/") ? "" : value));
        }

        private void typedRoute(MethodInvocationTree to) {
            if (!(to.getMethodSelect() instanceof MemberSelectTree select)
                    || !(select.getExpression() instanceof MethodInvocationTree verbCall)) {
                return;
            }
            Element verbElement = trees.getElement(TreePath.getPath(unit, verbCall));
            if (!(verbElement instanceof ExecutableElement verb) || !VERBS.contains(verb.getSimpleName().toString())
                    || verbCall.getArguments().size() != 1) {
                return;
            }
            String method = verb.getSimpleName().toString().toUpperCase(Locale.ROOT);
            Optional<String> base = router(verbCall);
            Optional<String> literal = constant(verbCall.getArguments().get(0));
            if (base.isEmpty() || literal.isEmpty()) {
                problem(DiagnosticCode.ROUTE_PATH_UNKNOWN, verbCall, "The path of this typed " + method
                        + " route cannot be determined at build time. Typed routes need a constant path declared in"
                        + " routes() or in a group with a constant prefix; use routes." + method.toLowerCase(Locale.ROOT)
                        + "(path, request -> ...) for dynamic routes.");
                return;
            }
            String path = join(base.get(), literal.get());
            if (to.getArguments().size() != 1 || !(to.getArguments().get(0) instanceof MemberReferenceTree reference)
                    || reference.getMode() != MemberReferenceTree.ReferenceMode.INVOKE
                    || !(reference.getQualifierExpression() instanceof IdentifierTree qualifier)
                    || !qualifier.getName().contentEquals("this")) {
                problem(DiagnosticCode.ROUTE_HANDLER_UNSUPPORTED, to, "The handler of typed route " + method + " " + path
                        + " must be a method of this controller: .to(this::method).");
                return;
            }
            Element referenced = trees.getElement(TreePath.getPath(unit, reference));
            if (!(referenced instanceof ExecutableElement handler)) {
                return;
            }
            Optional<List<RouteRecord.Parameter>> parameters = classify(to, method, path, handler);
            Optional<String> response = typeName(elements, handler.getReturnType());
            if (response.isEmpty()) {
                problem(DiagnosticCode.BIND_TYPE_UNSUPPORTED, to, "The response type " + handler.getReturnType()
                        + " of route " + method + " " + path + " is not supported (type variables and wildcards).");
            }
            if (parameters.isPresent() && response.isPresent()) {
                long line = unit.getLineMap().getLineNumber(trees.getSourcePositions().getStartPosition(unit, to));
                records.add(new RouteRecord(controller, method, path, handler.getSimpleName().toString(),
                        parameters.get(), response.get(), Optional.of(new SourceLocation(relativePath(), line))));
            }
        }

        private Optional<List<RouteRecord.Parameter>> classify(Tree at, String method, String path,
                ExecutableElement handler) {
            Set<String> placeholders = new LinkedHashSet<>();
            Matcher matcher = PLACEHOLDER.matcher(path);
            while (matcher.find()) {
                placeholders.add(matcher.group(1));
            }
            String route = method + " " + path;
            List<RouteRecord.Parameter> parameters = new ArrayList<>();
            Set<String> boundPlaceholders = new LinkedHashSet<>();
            List<String> structured = new ArrayList<>();
            boolean explicitBody = false;
            boolean valid = true;
            for (VariableElement parameter : handler.getParameters()) {
                String name = parameter.getSimpleName().toString();
                TypeMirror type = parameter.asType();
                Optional<String> typeName = typeName(elements, type);
                if (typeName.isEmpty()) {
                    valid = fail(DiagnosticCode.BIND_TYPE_UNSUPPORTED, at, "Parameter '" + name + "' of route " + route
                            + " has the unsupported type " + type + " (type variables and wildcards).");
                    continue;
                }
                String source = null;
                if (isType(type, REQUEST)) {
                    source = "REQUEST";
                } else if (wrapper(type).isPresent()) {
                    source = WRAPPERS.get(wrapper(type).get());
                    List<? extends TypeMirror> arguments = ((DeclaredType) type).getTypeArguments();
                    if (arguments.size() != 1 || (!source.equals("BODY") && !scalar(arguments.get(0)))) {
                        valid = fail(DiagnosticCode.BIND_TYPE_UNSUPPORTED, at, "Parameter '" + name + "' of route " + route
                                + " has the unsupported type " + type + "."
                                + (source.equals("BODY") ? "" : " Path, Query and Header values must be scalars."));
                        continue;
                    }
                    if (source.equals("PATH")) {
                        if (!placeholders.contains(name)) {
                            valid = fail(DiagnosticCode.BIND_PATH_MISMATCH, at, "Parameter '" + name + "' of route "
                                    + route + " is a Path<T> but the route has no {" + name + "} placeholder.");
                            continue;
                        }
                        boundPlaceholders.add(name);
                    }
                    if (source.equals("BODY")) {
                        if (explicitBody) {
                            valid = fail(DiagnosticCode.BIND_MULTIPLE_BODIES, at, "Route " + route
                                    + " declares more than one Body<T> parameter.");
                            continue;
                        }
                        explicitBody = true;
                    }
                } else if (placeholders.contains(name)) {
                    if (!scalar(type)) {
                        valid = fail(DiagnosticCode.BIND_TYPE_UNSUPPORTED, at, "Path parameter '" + name + "' of route "
                                + route + " has the unsupported type " + type + "; path values must be scalars.");
                        continue;
                    }
                    source = "PATH";
                    boundPlaceholders.add(name);
                } else if (structured(type)) {
                    structured.add(name);
                    source = "BODY";
                } else {
                    valid = fail(DiagnosticCode.BIND_SOURCE_UNKNOWN, at, sourceUnknown(name, type, route,
                            "no path placeholder has this name and QUERY is never inferred"));
                    continue;
                }
                parameters.add(new RouteRecord.Parameter(name, source, typeName.get()));
            }
            if (!structured.isEmpty()) {
                String first = structured.get(0);
                if (explicitBody) {
                    valid = fail(DiagnosticCode.BIND_SOURCE_UNKNOWN, at, sourceUnknown(first, null, route,
                            "the body is already claimed by a Body<T> parameter"));
                } else if (structured.size() > 1) {
                    valid = fail(DiagnosticCode.BIND_MULTIPLE_BODIES, at, "Route " + route + " has more than one "
                            + "structured parameter (" + String.join(", ", structured) + "); only one can be the body.");
                } else if (!BODY_METHODS.contains(method)) {
                    valid = fail(DiagnosticCode.BIND_SOURCE_UNKNOWN, at, sourceUnknown(first, null, route,
                            "the body is only inferred on POST, PUT and PATCH; use Body<T> to read it on " + method));
                }
            }
            for (String placeholder : placeholders) {
                if (!boundPlaceholders.contains(placeholder)) {
                    valid = fail(DiagnosticCode.BIND_PATH_MISMATCH, at, "Placeholder {" + placeholder + "} of route "
                            + route + " has no handler parameter named '" + placeholder + "'.");
                }
            }
            return valid ? Optional.of(parameters) : Optional.empty();
        }

        private String sourceUnknown(String name, TypeMirror type, String route, String reason) {
            return "Cannot determine the source of parameter '" + name + "'"
                    + (type == null ? "" : " (" + type + ")") + " of route " + route + ": " + reason
                    + ". Use Query<T>, Header<T>, Path<T> or Body<T>.";
        }

        private Optional<String> router(MethodInvocationTree call) {
            if (!(call.getMethodSelect() instanceof MemberSelectTree select)) {
                return Optional.empty();
            }
            Element receiver = trees.getElement(TreePath.getPath(unit, select.getExpression()));
            return routers.getOrDefault(receiver, Optional.empty());
        }

        private Optional<String> constant(ExpressionTree expression) {
            if (expression instanceof LiteralTree literal && literal.getValue() instanceof String value) {
                return Optional.of(value);
            }
            Element element = trees.getElement(TreePath.getPath(unit, expression));
            if (element instanceof VariableElement variable && variable.getConstantValue() instanceof String value) {
                return Optional.of(value);
            }
            return Optional.empty();
        }

        private boolean fail(DiagnosticCode code, Tree at, String message) {
            problem(code, at, message);
            return false;
        }

        private void problem(DiagnosticCode code, Tree at, String message) {
            problems.add(new Problem(new Diagnostic(code, DiagnosticSeverity.ERROR, message), at, unit));
        }

        private String relativePath() {
            String file = Path.of(unit.getSourceFile().getName()).getFileName().toString();
            String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            return packageName.isEmpty() ? file : packageName.replace('.', '/') + "/" + file;
        }
    }

    /**
     * Joins a group prefix and a path exactly as the runtime router does.
     */
    static String join(String prefix, String path) {
        if (prefix.isEmpty() || prefix.equals("/")) {
            return path;
        }
        return path.equals("/") ? prefix : prefix + path;
    }

    /**
     * Renders a type for {@code routes.idx}: binary names, generic arguments without spaces,
     * {@code []} for arrays, {@code void}; empty for type variables and wildcards.
     */
    static Optional<String> typeName(Elements elements, TypeMirror type) {
        if (type.getKind().isPrimitive() || type.getKind() == TypeKind.VOID) {
            return Optional.of(type.toString());
        }
        if (type.getKind() == TypeKind.ARRAY) {
            return typeName(elements, ((ArrayType) type).getComponentType()).map(component -> component + "[]");
        }
        if (type.getKind() != TypeKind.DECLARED) {
            return Optional.empty();
        }
        DeclaredType declared = (DeclaredType) type;
        StringBuilder name = new StringBuilder(elements.getBinaryName((TypeElement) declared.asElement()));
        if (!declared.getTypeArguments().isEmpty()) {
            List<String> arguments = new ArrayList<>();
            for (TypeMirror argument : declared.getTypeArguments()) {
                Optional<String> rendered = typeName(elements, argument);
                if (rendered.isEmpty()) {
                    return Optional.empty();
                }
                arguments.add(rendered.get());
            }
            name.append('<').append(String.join(",", arguments)).append('>');
        }
        return Optional.of(name.toString());
    }

    private Optional<String> wrapper(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return Optional.empty();
        }
        String name = ((TypeElement) ((DeclaredType) type).asElement()).getQualifiedName().toString();
        return WRAPPERS.containsKey(name) ? Optional.of(name) : Optional.empty();
    }

    private boolean scalar(TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return type.getKind() != TypeKind.CHAR;
        }
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
        return element.getKind() == ElementKind.ENUM || SCALARS.contains(element.getQualifiedName().toString());
    }

    private boolean structured(TypeMirror type) {
        if (type.getKind() == TypeKind.ARRAY) {
            return true;
        }
        if (type.getKind() != TypeKind.DECLARED || scalar(type) || wrapper(type).isPresent() || isType(type, REQUEST)) {
            return false;
        }
        String name = ((TypeElement) ((DeclaredType) type).asElement()).getQualifiedName().toString();
        return !name.equals("java.util.Optional") && !name.startsWith("java.lang.");
    }

    private boolean isType(TypeMirror type, String qualifiedName) {
        return type.getKind() == TypeKind.DECLARED
                && ((TypeElement) ((DeclaredType) type).asElement()).getQualifiedName().contentEquals(qualifiedName);
    }
}
