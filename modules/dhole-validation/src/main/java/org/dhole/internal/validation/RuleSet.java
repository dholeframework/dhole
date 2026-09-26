package org.dhole.internal.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.dhole.validation.FieldRules;
import org.dhole.validation.Rule;
import org.dhole.validation.Rules;

/**
 * The implementation of {@link Rules} and {@link FieldRules}: a fluent view over a shared
 * {@link Definition}. Field names are not known here; the {@link ValidationRegistry} pairs the
 * declared fields with build-time metadata.
 */
public final class RuleSet<T, V> implements FieldRules<T, V> {

    private final Definition<T> definition;
    private final FieldDefinition field;

    private RuleSet(Definition<T> definition, FieldDefinition field) {
        this.definition = definition;
        this.field = field;
    }

    public static <T> Rules<T> forType(Class<T> type) {
        return new RuleSet<T, Object>(new Definition<>(Objects.requireNonNull(type, "type")), null);
    }

    /**
     * Returns the definition behind a {@link Rules} created by {@link #forType(Class)}.
     */
    static Definition<?> definition(Rules<?> rules) {
        if (!(rules instanceof RuleSet<?, ?> set)) {
            throw new IllegalArgumentException("Rules must be created with Rules.forType(...)");
        }
        return set.definition;
    }

    @Override
    public Class<T> type() {
        return definition.type;
    }

    @Override
    public <W> FieldRules<T, W> field(Function<? super T, ? extends W> accessor) {
        FieldDefinition next = new FieldDefinition(Objects.requireNonNull(accessor, "accessor"));
        definition.fields.add(next);
        return new RuleSet<>(definition, next);
    }

    @Override
    public Rules<T> check(Rule<? super T> rule) {
        definition.checks.add(Objects.requireNonNull(rule, "rule"));
        return this;
    }

    @Override
    public FieldRules<T, V> required() {
        current().required = true;
        return this;
    }

    @Override
    public FieldRules<T, V> notBlank() {
        return add(Check.notBlank());
    }

    @Override
    public FieldRules<T, V> minLength(int length) {
        return add(Check.minLength(length));
    }

    @Override
    public FieldRules<T, V> maxLength(int length) {
        return add(Check.maxLength(length));
    }

    @Override
    public FieldRules<T, V> email() {
        return add(Check.email());
    }

    @Override
    public FieldRules<T, V> min(long minimum) {
        return add(Check.min(minimum));
    }

    @Override
    public FieldRules<T, V> max(long maximum) {
        return add(Check.max(maximum));
    }

    @Override
    public FieldRules<T, V> positive() {
        return add(Check.positive());
    }

    @Override
    public FieldRules<T, V> notEmpty() {
        return add(Check.notEmpty());
    }

    @Override
    public FieldRules<T, V> rule(Rule<? super V> rule) {
        return add(Check.custom(Objects.requireNonNull(rule, "rule")));
    }

    @Override
    public FieldRules<T, V> nested() {
        return add(Check.nested());
    }

    @Override
    public FieldRules<T, V> eachNested() {
        return add(Check.eachNested());
    }

    private RuleSet<T, V> add(Check check) {
        current().checks.add(check);
        return this;
    }

    private FieldDefinition current() {
        if (field == null) {
            throw new IllegalStateException("Declare a field with field(...) before its rules");
        }
        return field;
    }

    /**
     * The declared rules of a type: fields in declaration order and object-level checks.
     */
    static final class Definition<T> {

        final Class<T> type;
        final List<FieldDefinition> fields = new ArrayList<>();
        final List<Rule<? super T>> checks = new ArrayList<>();

        Definition(Class<T> type) {
            this.type = type;
        }
    }

    /**
     * One declared field: its accessor, whether it is required and its checks in order.
     */
    static final class FieldDefinition {

        final Function<Object, Object> accessor;
        boolean required;
        final List<Check> checks = new ArrayList<>();

        @SuppressWarnings("unchecked")
        FieldDefinition(Function<?, ?> accessor) {
            this.accessor = (Function<Object, Object>) accessor;
        }
    }
}
