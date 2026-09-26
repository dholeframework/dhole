package org.dhole.internal.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.dhole.validation.Rule;
import org.dhole.validation.Rules;
import org.dhole.validation.Validatable;
import org.dhole.validation.Validation;
import org.dhole.validation.ValidationError;
import org.dhole.validation.ValidationResult;
import org.junit.jupiter.api.Test;

class ValidatorTest {

    private static final String PREFIX = "org.dhole.internal.validation.ValidatorTest$";

    private static final String METADATA = """
            dhole-validation 1

            type %1$sCreateUser
            field name java.lang.String
            field email java.lang.String
            field age java.lang.Integer
            field tags java.util.List<java.lang.String>
            field password java.lang.String

            type %1$sCustomer
            field name java.lang.String

            type %1$sItem
            field quantity int

            type %1$sOrder
            field customer %1$sCustomer
            field items java.util.List<%1$sItem>

            type %1$sRegister
            field password java.lang.String
            field confirmation java.lang.String
            """.formatted(PREFIX);

    private final DefaultValidator validator =
            new DefaultValidator(new ValidationRegistry(ValidationMetadata.parse(METADATA)));

    static final Rule<String> STRONG = value -> value.length() >= 12
            ? Validation.success()
            : Validation.failure("PASSWORD_TOO_WEAK", "Password must contain at least 12 characters.");

    public record CreateUser(String name, String email, Integer age, List<String> tags, String password)
            implements Validatable {

        public static Rules<CreateUser> rules() {
            return Rules.forType(CreateUser.class)
                    .field(CreateUser::name).required().notBlank().minLength(2).maxLength(5)
                    .field(CreateUser::email).required().email()
                    .field(CreateUser::age).min(18).max(130).positive()
                    .field(CreateUser::tags).notEmpty()
                    .field(CreateUser::password).rule(STRONG);
        }
    }

    public record Customer(String name) implements Validatable {

        public static Rules<Customer> rules() {
            return Rules.forType(Customer.class).field(Customer::name).required();
        }
    }

    public record Item(int quantity) implements Validatable {

        public static Rules<Item> rules() {
            return Rules.forType(Item.class).field(Item::quantity).positive();
        }
    }

    public record Order(Customer customer, List<Item> items) implements Validatable {

        public static Rules<Order> rules() {
            return Rules.forType(Order.class)
                    .field(Order::customer).required().nested()
                    .field(Order::items).required().notEmpty().eachNested();
        }
    }

    public record Register(String password, String confirmation) implements Validatable {

        public static Rules<Register> rules() {
            return Rules.forType(Register.class)
                    .field(Register::password).required()
                    .field(Register::confirmation).required()
                    .check(input -> input.password() != null && input.password().equals(input.confirmation())
                            ? Validation.success()
                            : Validation.failure("PASSWORD_MISMATCH", "Passwords do not match."));
        }
    }

    public record Unindexed(String name) implements Validatable {

        public static Rules<Unindexed> rules() {
            return Rules.forType(Unindexed.class).field(Unindexed::name).required();
        }
    }

    public record NoRules(String name) implements Validatable {
    }

    @Test
    void validInputHasNoErrors() {
        ValidationResult result = validator.validate(
                new CreateUser("Ana", "ana@example.com", 30, List.of("a"), "correct horse battery"));

        assertTrue(result.isValid(), result.toString());
    }

    @Test
    void multipleInvalidFieldsAreReportedInDeclarationOrderWithStableCodes() {
        ValidationResult result = validator.validate(new CreateUser("  ", "not-an-email", 7, List.of(), "short"));

        assertEquals(List.of(
                new ValidationError("name", "NOT_BLANK", "Must not be blank."),
                new ValidationError("email", "INVALID_EMAIL", "Must be a valid email address."),
                new ValidationError("age", "MIN", "Must be at least 18."),
                new ValidationError("tags", "NOT_EMPTY", "Must not be empty."),
                new ValidationError("password", "PASSWORD_TOO_WEAK", "Password must contain at least 12 characters.")),
                result.errors());
    }

    @Test
    void oneFieldCanHaveSeveralErrors() {
        ValidationResult result = validator.validate(new CreateUser("x", "a@b.co", -1, null, null));

        assertEquals(List.of("MIN_LENGTH"), codes(result.errors("name")));
        assertEquals(List.of("MIN", "POSITIVE"), codes(result.errors("age")));
    }

    @Test
    void nullFailsOnlyRequiredAndOptionalNullsAreAccepted() {
        ValidationResult result = validator.validate(new CreateUser(null, null, null, null, null));

        assertEquals(List.of(new ValidationError("name", "REQUIRED", "Is required."),
                new ValidationError("email", "REQUIRED", "Is required.")), result.errors());
    }

    @Test
    void nestedAndEachNestedPrefixPaths() {
        ValidationResult result = validator.validate(new Order(new Customer(null), List.of(new Item(1), new Item(0))));

        assertEquals(List.of(new ValidationError("customer.name", "REQUIRED", "Is required."),
                new ValidationError("items[1].quantity", "POSITIVE", "Must be positive.")), result.errors());
        assertEquals(List.of(new ValidationError("items", "NOT_EMPTY", "Must not be empty.")),
                validator.validate(new Order(new Customer("c"), List.of())).errors());
    }

    @Test
    void objectLevelChecksRunAfterFieldsWithAnEmptyPath() {
        ValidationResult result = validator.validate(new Register("secret", "different"));

        assertEquals(List.of(new ValidationError("", "PASSWORD_MISMATCH", "Passwords do not match.")), result.errors());
    }

    @Test
    void missingOrStaleMetadataRequiresARebuild() {
        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> validator.validate(new Unindexed("x")));
        assertTrue(missing.getMessage().endsWith("Rebuild the application."), missing.getMessage());

        DefaultValidator stale = new DefaultValidator(new ValidationRegistry(ValidationMetadata.parse(
                "dhole-validation 1\n\ntype " + PREFIX + "Customer\nfield name java.lang.String\nfield extra int\n")));
        IllegalStateException count = assertThrows(IllegalStateException.class,
                () -> stale.validate(new Customer("x")));
        assertTrue(count.getMessage().contains("rules() declares 1 field(s) but the metadata records 2"), count.getMessage());
    }

    @Test
    void typesWithoutRulesOrNotValidatableAreRejected() {
        assertTrue(assertThrows(IllegalStateException.class, () -> validator.validate(new NoRules("x")))
                .getMessage().contains("declares no public static Rules<T> rules()"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("not validatable"));
    }

    @Test
    void metadataIndexIsStrict() {
        assertTrue(assertThrows(IllegalStateException.class, () -> ValidationMetadata.parse("dhole-validation 2\n"))
                .getMessage().startsWith("Metadata Compatibility Error"));
        for (String text : List.of("validation 1\n", "dhole-validation 1\n\nfield a int\n",
                "dhole-validation 1\n\ntype A\nfield 1a int\n", "dhole-validation 1\n\ntype A\nextra\n",
                "dhole-validation 1\n\ntype A\n\ntype A\n")) {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> ValidationMetadata.parse(text), text);
            assertTrue(failure.getMessage().startsWith("Metadata Error\n\nMalformed META-INF/dhole/validation.idx at line "),
                    failure.getMessage());
        }
    }

    private static List<String> codes(List<ValidationError> errors) {
        return errors.stream().map(ValidationError::code).toList();
    }
}
