package example.hello.controllers;

import org.dhole.routing.Router;
import org.dhole.web.Controller;

public final class HelloController extends Controller {

    @Override
    public void routes(Router routes) {
        routes.get("/hello", request -> "Hello World");
    }
}
