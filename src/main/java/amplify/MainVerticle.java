package amplify;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;


public class MainVerticle extends Verticle {
    public void start() {

        vertx.eventBus().registerHandler("authenticated", new Handler<Message>() {
            @Override
            public void handle(Message event) {
                container.logger().info("sending a message!");
                JsonObject payload = new JsonObject();
                JsonObject messageParams = new JsonObject();
                payload.putString("Hello", "World");
                payload.putString("CCS", "Dummy Message");

                messageParams.putObject("payload", payload);
                messageParams.putString("collapseKey", "sample");
                messageParams.putString("toRegId", "RegistrationIdOfTheTargetDevice");
                messageParams.putNumber("timeToLive", 10000L);
                messageParams.putBoolean("delayWhileIdle", true);

                vertx.eventBus().send("send-message", messageParams);
            }
        });

        container.deployVerticle("amplify.XmppVerticle", new AsyncResultHandler<String>() {
            @Override
            public void handle(AsyncResult<String> event) {
                JsonObject startParams = new JsonObject();
                startParams.putNumber("senderId", 461924595460L);
                startParams.putString("apiKey", "AIzaSyAFRTb3omOobZnqPi-XR5JE_USjuG20Mrk");

                vertx.eventBus().publish("start-gcm-services", startParams);
            }
        });

        container.deployVerticle("PingVerticle.java");
    }
}
