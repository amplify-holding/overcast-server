package amplify;

import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.platform.Verticle;

public class MetricsVerticle extends Verticle {
    public void start() {
        vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
            public void handle(HttpServerRequest request) {
                container.logger().info("A request has arrived on the server!");
                request.response().end();
                vertx.eventBus().send("ping-address", "ping!");
            }
        }).listen(8200, "localhost");

        container.logger().info("MetricsVerticle started");
    }
}
