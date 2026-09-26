package example.hello.users;

import org.dhole.routing.Router;
import org.dhole.web.Controller;

public final class UserController extends Controller {

    @Override
    public void routes(Router routes) {
        routes.get("/users/{id}").to(this::find);
        routes.post("/users").to(this::create);
    }

    User find(long id) {
        return new User(id, "User " + id, "user" + id + "@example.com");
    }

    User create(CreateUser input) {
        return new User(1, input.name(), input.email());
    }
}
