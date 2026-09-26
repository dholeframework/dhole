package org.dhole.internal.validation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.dhole.validation.Rules;
import org.dhole.validation.Validatable;

/**
 * The declared rules of each {@link Validatable} type, paired with their field names from
 * build-time metadata. Rules come from the type's {@code public static Rules<T> rules()} (one exact
 * method lookup on a known type, no scanning); the i-th declared field gets the i-th recorded name,
 * and the counts must match. Compiled once per type.
 *
 * <p>Public only for the internal web runtime; not application API.
 */
public final class ValidationRegistry {

    private final ValidationMetadata metadata;
    private final Map<Class<?>, Compiled> compiled = new ConcurrentHashMap<>();

    public ValidationRegistry(ValidationMetadata metadata) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
    }

    /**
     * Prepares the rules of a validatable type, failing early when they or their metadata are unusable.
     *
     * @throws IllegalStateException if the type has no usable {@code rules()} or its metadata is
     *         missing or stale ("Rebuild the application")
     */
    public void prepare(Class<?> type) {
        compiled(type);
    }

    Compiled compiled(Class<?> type) {
        return compiled.computeIfAbsent(type, this::compile);
    }

    private Compiled compile(Class<?> type) {
        if (!Validatable.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException(type.getName() + " is not Validatable");
        }
        RuleSet.Definition<?> definition = RuleSet.definition(rules(type));
        if (definition.type != type) {
            throw invalid(type, "rules() declares rules for " + definition.type.getName());
        }
        List<String> names = metadata.fields(type.getName())
                .orElseThrow(() -> stale(type, "it has no build-time validation metadata"));
        if (names.size() != definition.fields.size()) {
            throw stale(type, "rules() declares " + definition.fields.size() + " field(s) but the metadata records "
                    + names.size());
        }
        return new Compiled(definition, names);
    }

    private static Rules<?> rules(Class<?> type) {
        Method method;
        try {
            method = type.getMethod("rules");
        } catch (NoSuchMethodException e) {
            throw invalid(type, "it declares no public static Rules<T> rules()");
        }
        if (!Modifier.isStatic(method.getModifiers()) || !Rules.class.isAssignableFrom(method.getReturnType())) {
            throw invalid(type, "rules() must be public static and return Rules<T>");
        }
        try {
            return Objects.requireNonNull((Rules<?>) method.invoke(null), "rules() returned null");
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Validation Error\n\n" + type.getName() + ".rules() failed.", e.getCause());
        } catch (IllegalAccessException e) {
            throw invalid(type, "rules() is not accessible");
        }
    }

    private static IllegalStateException invalid(Class<?> type, String problem) {
        return new IllegalStateException("Validation Error\n\n" + type.getName() + " cannot be validated: " + problem + ".");
    }

    private static IllegalStateException stale(Class<?> type, String problem) {
        return new IllegalStateException("Metadata Error\n\n" + type.getName() + " cannot be validated: " + problem
                + ".\n\nRebuild the application.");
    }

    /**
     * Rules of one type with their field names.
     */
    record Compiled(RuleSet.Definition<?> definition, List<String> names) {
    }
}
