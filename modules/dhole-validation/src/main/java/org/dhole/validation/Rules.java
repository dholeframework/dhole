package org.dhole.validation;

import java.util.function.Function;

import org.dhole.internal.validation.RuleSet;

/**
 * The validation rules of a type, declared with method references:
 *
 * <pre>{@code
 * Rules.forType(CreateUser.class)
 *     .field(CreateUser::email).required().email()
 *     .field(CreateUser::age).min(18)
 *     .check(input -> ...);
 * }</pre>
 *
 * @param <T> the validated type
 */
public interface Rules<T> {

    static <T> Rules<T> forType(Class<T> type) {
        return RuleSet.forType(type);
    }

    Class<T> type();

    /**
     * Starts the rules of one field; its name is known from build-time metadata.
     */
    <V> FieldRules<T, V> field(Function<? super T, ? extends V> accessor);

    /**
     * Adds an object-level rule, applied after the field rules; its failures have an empty field path.
     */
    Rules<T> check(Rule<? super T> rule);
}
