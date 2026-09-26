package org.dhole.internal.web.activation;

import org.dhole.routing.Router;
import org.dhole.web.Controller;

public final class PingController extends Controller {

    @Override
    public void routes(Router routes) {
        routes.get("/ping", request -> "pong");
    }
}
