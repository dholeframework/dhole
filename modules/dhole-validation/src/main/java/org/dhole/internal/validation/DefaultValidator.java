package org.dhole.internal.validation;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.dhole.validation.Rule;
import org.dhole.validation.Validatable;
import org.dhole.validation.ValidationError;
import org.dhole.validation.ValidationResult;
import org.dhole.validation.Validator;

/**
 * Runs declared rules. Fields in declaration order, then object-level checks; a {@code null} field
 * value fails only {@code required()} and skips the field's other rules; every other rule of a field
 * runs, in order. Nested values are validated with their own type's rules under a prefixed path.
 *
 * <p>Public only for the internal web runtime; not application API.
 */
public final class DefaultValidator implements Validator {

    private static final String REQUIRED = "REQUIRED";

    private final ValidationRegistry registry;

    public DefaultValidator(ValidationRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public ValidationResult validate(Object value) {
        Objects.requireNonNull(value, "value");
        if (!(value instanceof Validatable)) {
            throw new IllegalArgumentException(value.getClass().getName() + " is not Validatable");
        }
        List<ValidationError> errors = new ArrayList<>();
        validate(value, "", errors);
        return ValidationResult.of(errors);
    }

    @SuppressWarnings("unchecked")
    private void validate(Object value, String prefix, List<ValidationError> errors) {
        ValidationRegistry.Compiled compiled = registry.compiled(value.getClass());
        List<RuleSet.FieldDefinition> fields = compiled.definition().fields;
        for (int index = 0; index < fields.size(); index++) {
            RuleSet.FieldDefinition field = fields.get(index);
            String path = prefix + compiled.names().get(index);
            Object fieldValue = field.accessor.apply(value);
            if (fieldValue == null) {
                if (field.required) {
                    errors.add(new ValidationError(path, REQUIRED, "Is required."));
                }
                continue;
            }
            for (Check check : field.checks) {
                apply(check, fieldValue, path, errors);
            }
        }
        String objectPath = prefix.endsWith(".") ? prefix.substring(0, prefix.length() - 1) : prefix;
        for (Rule<?> check : compiled.definition().checks) {
            add(((Rule<Object>) check).validate(value), objectPath, errors);
        }
    }

    private void apply(Check check, Object value, String path, List<ValidationError> errors) {
        switch (check) {
            case Check.Builtin builtin -> {
                if (!builtin.passes().test(value)) {
                    errors.add(new ValidationError(path, builtin.code(), builtin.message()));
                }
            }
            case Check.Custom custom -> add(custom.rule().validate(value), path, errors);
            case Check.Nested nested -> validate(requireValidatable(value, path), path + ".", errors);
            case Check.EachNested each -> {
                List<Object> elements = elements(value, path);
                for (int index = 0; index < elements.size(); index++) {
                    Object element = elements.get(index);
                    if (element != null) {
                        validate(requireValidatable(element, path), path + "[" + index + "].", errors);
                    }
                }
            }
        }
    }

    private static void add(ValidationResult result, String path, List<ValidationError> errors) {
        Objects.requireNonNull(result, "A validation rule returned no result");
        for (ValidationError error : result.errors()) {
            String field = error.field().isEmpty() ? path : path.isEmpty() ? error.field() : path + "." + error.field();
            errors.add(new ValidationError(field, error.code(), error.message()));
        }
    }

    private static Object requireValidatable(Object value, String path) {
        if (!(value instanceof Validatable)) {
            throw new IllegalStateException("Validation rule nested() at '" + path + "' does not apply to "
                    + value.getClass().getSimpleName() + " values; the type is not Validatable");
        }
        return value;
    }

    private static List<Object> elements(Object value, String path) {
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value.getClass().isArray()) {
            List<Object> elements = new ArrayList<>();
            for (int index = 0; index < Array.getLength(value); index++) {
                elements.add(Array.get(value, index));
            }
            return elements;
        }
        throw new IllegalStateException("Validation rule eachNested() at '" + path + "' needs a collection or array");
    }
}
