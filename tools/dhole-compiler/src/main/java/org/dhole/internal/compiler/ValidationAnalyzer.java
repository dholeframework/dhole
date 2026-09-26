package org.dhole.internal.compiler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

/**
 * Derives {@link ValidationRecord}s from the {@code public static Rules<T> rules()} of
 * {@code Validatable} types (METADATA_COMPILER.md §34.4). Only the canonical shape is accepted: one
 * {@code return} of a fluent chain started by {@code Rules.forType(T.class)} whose {@code field(...)}
 * arguments are method references {@code T::accessor}. It also checks rule/type compatibility. Not a
 * Java interpreter: any other shape is a DHOLE-VAL diagnostic.
 */
final class ValidationAnalyzer {

    static final String VALIDATABLE = "org.dhole.validation.Validatable";
    private static final String RULES = "org.dhole.validation.Rules";
    private static final Set<String> TEXT_RULES = Set.of("notBlank", "minLength", "maxLength", "email");
    private static final Set<String> NUMBER_RULES = Set.of("min", "max", "positive");

    private final Elements elements;
    private final Types types;
    private final Trees trees;
    private final List<RouteAnalyzer.Problem> problems = new ArrayList<>();

    ValidationAnalyzer(Elements elements, Types types, Trees trees) {
        this.elements = elements;
        this.types = types;
        this.trees = trees;
    }

    List<RouteAnalyzer.Problem> problems() {
        return List.copyOf(problems);
    }

    Optional<ValidationRecord> analyze(TypeElement type) {
        Optional<ExecutableElement> rules = ElementFilter.methodsIn(type.getEnclosedElements()).stream()
                .filter(method -> method.getSimpleName().contentEquals("rules") && method.getParameters().isEmpty())
                .findFirst();
        TreePath typePath = trees.getPath(type);
        if (typePath == null) {
            return Optional.empty();
        }
        CompilationUnitTree unit = typePath.getCompilationUnit();
        if (rules.isEmpty() || !rules.get().getModifiers().contains(Modifier.STATIC)
                || !rules.get().getModifiers().contains(Modifier.PUBLIC)) {
            problem(unit, typePath.getLeaf(), DiagnosticCode.VAL_SHAPE, type.getSimpleName()
                    + " is Validatable but declares no public static Rules<" + type.getSimpleName() + "> rules().");
            return Optional.empty();
        }
        MethodTree method = trees.getTree(rules.get());
        BlockTree body = method.getBody();
        if (body == null || body.getStatements().size() != 1
                || !(body.getStatements().get(0) instanceof ReturnTree returned)
                || returned.getExpression() == null) {
            problem(unit, method, DiagnosticCode.VAL_SHAPE, shape(type));
            return Optional.empty();
        }
        List<MethodInvocationTree> chain = new ArrayList<>();
        ExpressionTree current = returned.getExpression();
        while (current instanceof MethodInvocationTree call && call.getMethodSelect() instanceof MemberSelectTree select
                && !isForType(unit, call)) {
            chain.add(0, call);
            current = select.getExpression();
        }
        if (!(current instanceof MethodInvocationTree start) || !isForType(unit, start)
                || !isClassLiteralOf(unit, start.getArguments().get(0), type)) {
            problem(unit, returned, DiagnosticCode.VAL_SHAPE, shape(type));
            return Optional.empty();
        }
        return fields(type, unit, chain);
    }

    private Optional<ValidationRecord> fields(TypeElement type, CompilationUnitTree unit, List<MethodInvocationTree> chain) {
        List<ValidationRecord.Field> fields = new ArrayList<>();
        Set<String> names = new HashSet<>();
        TypeMirror fieldType = null;
        boolean valid = true;
        for (MethodInvocationTree call : chain) {
            String name = ((MemberSelectTree) call.getMethodSelect()).getIdentifier().toString();
            if (name.equals("field")) {
                Optional<ExecutableElement> accessor = accessor(unit, call, type);
                if (accessor.isEmpty()) {
                    valid = false;
                    fieldType = null;
                    problem(unit, call, DiagnosticCode.VAL_FIELD, "field(...) of " + type.getSimpleName()
                            + ".rules() must be a method reference to a no-argument accessor of " + type.getSimpleName()
                            + ", for example " + type.getSimpleName() + "::name.");
                    continue;
                }
                String fieldName = fieldName(type, accessor.get());
                fieldType = accessor.get().getReturnType();
                if (!names.add(fieldName)) {
                    valid = false;
                    problem(unit, call, DiagnosticCode.VAL_DUPLICATE_FIELD, "Field '" + fieldName + "' of "
                            + type.getSimpleName() + " is declared more than once in rules().");
                }
                Optional<String> typeName = RouteAnalyzer.typeName(elements, fieldType);
                if (typeName.isEmpty()) {
                    valid = false;
                    problem(unit, call, DiagnosticCode.VAL_RULE_TYPE, "Field '" + fieldName + "' of "
                            + type.getSimpleName() + " has the unsupported type " + fieldType + ".");
                    fieldType = null;
                    continue;
                }
                fields.add(new ValidationRecord.Field(fieldName, typeName.get()));
            } else if (name.equals("check")) {
                fieldType = null;
            } else if (fieldType != null) {
                Optional<String> problem = incompatible(name, fieldType);
                if (problem.isPresent()) {
                    valid = false;
                    problem(unit, call, name.endsWith("ested") ? DiagnosticCode.VAL_NESTED : DiagnosticCode.VAL_RULE_TYPE,
                            problem.get() + " (" + type.getSimpleName() + "." + fields.get(fields.size() - 1).name() + ").");
                }
            }
        }
        if (!valid) {
            return Optional.empty();
        }
        long line = unit.getLineMap().getLineNumber(trees.getSourcePositions().getStartPosition(unit, trees.getTree(type)));
        return Optional.of(new ValidationRecord(elements.getBinaryName(type).toString(), fields,
                Optional.of(new SourceLocation(relativePath(unit), line))));
    }

    private Optional<String> incompatible(String rule, TypeMirror type) {
        TypeMirror boxed = type.getKind().isPrimitive() ? types.boxedClass((PrimitiveType) type).asType() : type;
        if (TEXT_RULES.contains(rule) && !assignable(boxed, "java.lang.CharSequence")) {
            return Optional.of("Rule " + rule + "() needs a text field, not " + type);
        }
        if (NUMBER_RULES.contains(rule) && !assignable(boxed, "java.lang.Number")) {
            return Optional.of("Rule " + rule + "() needs a number field, not " + type);
        }
        if (rule.equals("notEmpty") && type.getKind() != TypeKind.ARRAY && !assignable(boxed, "java.lang.CharSequence")
                && !assignable(boxed, "java.util.Collection") && !assignable(boxed, "java.util.Map")) {
            return Optional.of("Rule notEmpty() needs text, a collection, a map or an array, not " + type);
        }
        if (rule.equals("nested") && !assignable(boxed, VALIDATABLE)) {
            return Optional.of("Rule nested() needs a Validatable field, not " + type);
        }
        if (rule.equals("eachNested")) {
            Optional<TypeMirror> element = element(type);
            if (element.isEmpty() || !assignable(element.get(), VALIDATABLE)) {
                return Optional.of("Rule eachNested() needs a collection or array of Validatable values, not " + type);
            }
        }
        return Optional.empty();
    }

    private Optional<TypeMirror> element(TypeMirror type) {
        if (type.getKind() == TypeKind.ARRAY) {
            return Optional.of(((ArrayType) type).getComponentType());
        }
        if (type.getKind() == TypeKind.DECLARED && assignable(type, "java.util.Collection")
                && ((DeclaredType) type).getTypeArguments().size() == 1) {
            return Optional.of(((DeclaredType) type).getTypeArguments().get(0));
        }
        return Optional.empty();
    }

    private boolean assignable(TypeMirror type, String target) {
        TypeElement element = elements.getTypeElement(target);
        return element != null && types.isAssignable(types.erasure(type), types.erasure(element.asType()));
    }

    private Optional<ExecutableElement> accessor(CompilationUnitTree unit, MethodInvocationTree call, TypeElement type) {
        if (call.getArguments().size() != 1 || !(call.getArguments().get(0) instanceof MemberReferenceTree reference)
                || reference.getMode() != MemberReferenceTree.ReferenceMode.INVOKE) {
            return Optional.empty();
        }
        Element qualifier = trees.getElement(TreePath.getPath(unit, reference.getQualifierExpression()));
        Element referenced = trees.getElement(TreePath.getPath(unit, reference));
        if (!type.equals(qualifier) || !(referenced instanceof ExecutableElement method)
                || !method.getParameters().isEmpty() || method.getModifiers().contains(Modifier.STATIC)
                || method.getReturnType().getKind() == TypeKind.VOID) {
            return Optional.empty();
        }
        return Optional.of(method);
    }

    private static String fieldName(TypeElement type, ExecutableElement accessor) {
        String name = accessor.getSimpleName().toString();
        if (type.getKind() == ElementKind.RECORD) {
            for (RecordComponentElement component : type.getRecordComponents()) {
                if (component.getSimpleName().contentEquals(name)) {
                    return name;
                }
            }
        }
        for (String prefix : List.of("get", "is")) {
            if (name.length() > prefix.length() && name.startsWith(prefix)
                    && Character.isUpperCase(name.charAt(prefix.length()))) {
                return name.substring(prefix.length(), prefix.length() + 1).toLowerCase(Locale.ROOT)
                        + name.substring(prefix.length() + 1);
            }
        }
        return name;
    }

    private boolean isForType(CompilationUnitTree unit, MethodInvocationTree call) {
        Element element = trees.getElement(TreePath.getPath(unit, call));
        return element instanceof ExecutableElement method && method.getSimpleName().contentEquals("forType")
                && ((TypeElement) method.getEnclosingElement()).getQualifiedName().contentEquals(RULES)
                && call.getArguments().size() == 1;
    }

    private boolean isClassLiteralOf(CompilationUnitTree unit, ExpressionTree argument, TypeElement type) {
        return argument instanceof MemberSelectTree select && select.getIdentifier().contentEquals("class")
                && type.equals(trees.getElement(TreePath.getPath(unit, select.getExpression())));
    }

    private static String shape(TypeElement type) {
        return type.getSimpleName() + ".rules() must be a single 'return Rules.forType(" + type.getSimpleName()
                + ".class)...' fluent chain; conditional or computed field declarations are not supported.";
    }

    private void problem(CompilationUnitTree unit, Tree tree, DiagnosticCode code, String message) {
        problems.add(new RouteAnalyzer.Problem(new Diagnostic(code, DiagnosticSeverity.ERROR, message), tree, unit));
    }

    private static String relativePath(CompilationUnitTree unit) {
        String file = Path.of(unit.getSourceFile().getName()).getFileName().toString();
        String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
        return packageName.isEmpty() ? file : packageName.replace('.', '/') + "/" + file;
    }
}
