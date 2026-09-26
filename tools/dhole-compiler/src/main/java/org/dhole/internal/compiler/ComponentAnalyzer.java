package org.dhole.internal.compiler;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
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
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

/**
 * Derives {@link ComponentRecord}s from source elements, applying the dependency container's
 * constructor rule structurally: exactly one public constructor of a public, top-level or static
 * nested class; parameterized constructor dependencies are not supported. No class is loaded.
 */
final class ComponentAnalyzer {

    private final Elements elements;
    private final Types types;
    private final Trees trees;

    /**
     * @param trees javac source trees for source locations, or {@code null} when unavailable
     */
    ComponentAnalyzer(Elements elements, Types types, Trees trees) {
        this.elements = elements;
        this.types = types;
        this.trees = trees;
    }

    /**
     * Whether the type is a concrete class or record, the only kinds that can provide components.
     */
    static boolean isConcreteClass(TypeElement type) {
        return (type.getKind() == ElementKind.CLASS || type.getKind() == ElementKind.RECORD)
                && !type.getModifiers().contains(Modifier.ABSTRACT);
    }

    ComponentRecord analyze(TypeElement type) {
        String name = elements.getBinaryName(type).toString();
        List<String> supertypes = supertypes(type);
        if (type.getNestingKind() == NestingKind.MEMBER && !type.getModifiers().contains(Modifier.STATIC)) {
            return unusable(name, "not-static-nested", supertypes, location(type));
        }
        if (!type.getModifiers().contains(Modifier.PUBLIC)) {
            return unusable(name, "not-public", supertypes, location(type));
        }
        List<ExecutableElement> constructors = ElementFilter.constructorsIn(type.getEnclosedElements()).stream()
                .filter(constructor -> constructor.getModifiers().contains(Modifier.PUBLIC))
                .toList();
        if (constructors.isEmpty()) {
            return unusable(name, "no-public-constructor", supertypes, location(type));
        }
        if (constructors.size() > 1) {
            return unusable(name, "multiple-public-constructors " + constructors.size(), supertypes, location(type));
        }
        ExecutableElement constructor = constructors.get(0);
        List<? extends VariableElement> parameters = constructor.getParameters();
        List<String> parameterTypes = parameters.stream().map(parameter -> forName(parameter.asType())).toList();
        Optional<String> unusable = Optional.empty();
        for (int index = 0; index < parameters.size(); index++) {
            TypeMirror parameterType = parameters.get(index).asType();
            if (parameterType.getKind() == TypeKind.DECLARED
                    && !((DeclaredType) parameterType).getTypeArguments().isEmpty()) {
                unusable = Optional.of("parameterized-dependency " + index);
                break;
            }
        }
        Optional<SourceLocation> source = elements.getOrigin(constructor) == Elements.Origin.EXPLICIT
                ? location(constructor)
                : location(type);
        return new ComponentRecord(name, Optional.of(parameterTypes), unusable, supertypes, source);
    }

    private static ComponentRecord unusable(String name, String reason, List<String> supertypes,
            Optional<SourceLocation> source) {
        return new ComponentRecord(name, Optional.empty(), Optional.of(reason), supertypes, source);
    }

    private List<String> supertypes(TypeElement type) {
        Set<String> names = new TreeSet<>();
        Deque<TypeMirror> pending = new ArrayDeque<>(types.directSupertypes(type.asType()));
        while (!pending.isEmpty()) {
            TypeMirror supertype = types.erasure(pending.pop());
            if (supertype.getKind() != TypeKind.DECLARED) {
                continue;
            }
            String name = elements.getBinaryName((TypeElement) types.asElement(supertype)).toString();
            if (!name.equals("java.lang.Object") && names.add(name)) {
                pending.addAll(types.directSupertypes(supertype));
            }
        }
        return List.copyOf(names);
    }

    /**
     * Returns the type in {@code Class.forName} format: binary name, primitive keyword or array
     * descriptor.
     */
    private String forName(TypeMirror type) {
        TypeMirror erased = types.erasure(type);
        if (erased.getKind().isPrimitive()) {
            return erased.toString();
        }
        if (erased.getKind() == TypeKind.ARRAY) {
            return "[" + descriptor(((ArrayType) erased).getComponentType());
        }
        return elements.getBinaryName((TypeElement) types.asElement(erased)).toString();
    }

    private String descriptor(TypeMirror type) {
        TypeMirror erased = types.erasure(type);
        return switch (erased.getKind()) {
            case BOOLEAN -> "Z";
            case BYTE -> "B";
            case CHAR -> "C";
            case SHORT -> "S";
            case INT -> "I";
            case LONG -> "J";
            case FLOAT -> "F";
            case DOUBLE -> "D";
            case ARRAY -> "[" + descriptor(((ArrayType) erased).getComponentType());
            default -> "L" + elements.getBinaryName((TypeElement) types.asElement(erased)) + ";";
        };
    }

    /**
     * Returns the portable location of an element: path relative to the source root and line.
     */
    private Optional<SourceLocation> location(Element element) {
        if (trees == null) {
            return Optional.empty();
        }
        TreePath path = trees.getPath(element);
        if (path == null) {
            return Optional.empty();
        }
        CompilationUnitTree unit = path.getCompilationUnit();
        long position = trees.getSourcePositions().getStartPosition(unit, path.getLeaf());
        if (position < 0) {
            return Optional.empty();
        }
        String fileName = Path.of(unit.getSourceFile().getName()).getFileName().toString();
        String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
        String relative = packageName.isEmpty() ? fileName : packageName.replace('.', '/') + "/" + fileName;
        return Optional.of(new SourceLocation(relative, unit.getLineMap().getLineNumber(position)));
    }
}
