package example.hello.users;

import org.dhole.validation.Rules;
import org.dhole.validation.Validatable;

public record CreateUser(String name, String email) implements Validatable {

    public static Rules<CreateUser> rules() {
        return Rules.forType(CreateUser.class)
                .field(CreateUser::name).required().notBlank().maxLength(80)
                .field(CreateUser::email).required().email();
    }
}
