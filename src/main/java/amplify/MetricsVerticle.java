package amplify;

import org.apache.commons.codec.digest.DigestUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;

import java.util.Random;

public class MetricsVerticle extends Verticle {
    public static final String GAME_KEY = "4ca1ee5d255754fd604500aab90942c8";
    public static final String SECRET_KEY = "417cae80d8054acd1c8619dad0b3dda13b1662f0";
    public static final String GAME_ANALYTICS_URL = "api.gameanalytics.com";
    public static final String API_VERSION = "1";
    private static String session_id = String.valueOf(new Random().nextLong());

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
        String uri = "/"+ API_VERSION + "/" + GAME_KEY + "/";

        if(GABuildType.DESIGN.toString().equals(jsonObject.getString("build")))
            uri += "design" + "/";
        else if(GABuildType.QA.toString().equals(jsonObject.getString("build"))) {
            uri += "error" + "/";
        }
        
        HttpClientRequest request = client.post(uri , new Handler<HttpClientResponse>() {
            @Override
            public void handle(HttpClientResponse event) {
                container.logger().info("Metrics request: " + event.statusMessage());
                message.reply(event.statusCode());
            }
        });

        String content = jsonObject.encode();
        request.putHeader("Authorization", DigestUtils.md5Hex(content + SECRET_KEY));
        request.setChunked(true).putHeader("Content-Type", "application/json; charset=utf-8").write(content).end();
    }

    public static JsonObject getErrorMetricJson(String message, GASeverity severity){
        JsonObject jsonObject = new JsonObject();
        jsonObject.putString("user_id", "overcast_server");
        jsonObject.putString("build", GABuildType.QA.toString());
        jsonObject.putString("message", message);
        jsonObject.putString("session_id", session_id);
        jsonObject.putString("severity", severity.toString());

        return jsonObject;
    }

    public static JsonObject getDesignMetricJson(String eventId, double value){

        JsonObject jsonObject = new JsonObject();
        jsonObject.putString("user_id", "overcast_server");
        jsonObject.putString("session_id", session_id);
        jsonObject.putString("build", GABuildType.DESIGN.toString());
        jsonObject.putString("event_id", eventId);
        jsonObject.putNumber("value", value);

        return jsonObject;
    }
}
