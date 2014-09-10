package amplify;

import org.apache.commons.codec.digest.DigestUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;

public class MetricsVerticle extends Verticle {
    public static final String GAME_KEY = "4ca1ee5d255754fd604500aab90942c8";
    public static final String SECRET_KEY = "417cae80d8054acd1c8619dad0b3dda13b1662f0";
    public static final String GAME_ANALYTICS_URL = "api.gameanalytics.com";
    public static final String API_VERSION = "1";
    private static final String CATEGORY = "design";

    private HttpClient client;

    public void start() {
        client = vertx.createHttpClient().setHost(GAME_ANALYTICS_URL);

        vertx.eventBus().registerHandler("send-metric",
            new Handler<org.vertx.java.core.eventbus.Message<JsonObject>>() {
                @Override
                public void handle(org.vertx.java.core.eventbus.Message<JsonObject> message) {
                    sendMetric(message, message.body());
                }
            });

        /*client.requestHandler(new Handler<HttpServerRequest>() {
            public void handle(HttpServerRequest request) {
                container.logger().info("A request has arrived on the server!");
                request.response().end();
                vertx.eventBus().send("ping-address", "ping!");
            }
        }).listen(8200, "localhost");*/

        container.logger().info("Metrics Verticle started");
    }

    private void sendMetric(final org.vertx.java.core.eventbus.Message<JsonObject> message, JsonObject jsonObject) {
        String uri = "/"+ API_VERSION + "/" + GAME_KEY + "/" + CATEGORY + "/";
        HttpClientRequest request = client.post(uri , new Handler<HttpClientResponse>() {
            @Override
            public void handle(HttpClientResponse event) {
                container.logger().info(event.statusMessage());
                message.reply(event.statusCode());
            }
        });


        String content = jsonObject.encode();
        request.putHeader("Authorization", DigestUtils.md5Hex(content + SECRET_KEY));
        request.setChunked(true).putHeader("Content-Type", "application/json; charset=utf-8").write(content).end();

        container.logger().info("About to make request " + uri);
    }
}
