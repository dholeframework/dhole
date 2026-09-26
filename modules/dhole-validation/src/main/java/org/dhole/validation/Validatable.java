package org.dhole.validation;

/**
 * Marks an input type that declares validation rules with a static method:
 *
 * <pre>{@code
 * public record CreateUser(String name, String email) implements Validatable {
 *
 *     public static Rules<CreateUser> rules() {
 *         return Rules.forType(CreateUser.class)
 *             .field(CreateUser::name).required().minLength(2)
 *             .field(CreateUser::email).required().email();
 *     }
 * }
 * }</pre>
 *
 * The field names come from build-time metadata, so {@code rules()} must be a single fluent chain.
 */
public interface Validatable {
}
