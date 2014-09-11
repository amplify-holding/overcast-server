package amplify.integration.java;

import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.testtools.TestVerticle;

import static org.vertx.testtools.VertxAssert.assertTrue;
import static org.vertx.testtools.VertxAssert.testComplete;

public class XmppTest extends TestVerticle {

    @Override
    public void start() {
        initialize();

        vertx.eventBus().registerHandler("authenticated", new Handler<Message>() {
            @Override
            public void handle(Message event) {
                startTests();
            }
        });

        container.deployVerticle("amplify.XmppVerticle", new AsyncResultHandler<String>() {
            @Override
            public void handle(AsyncResult<String> event) {
                JsonObject startParams = new JsonObject();
                startParams.putNumber("senderId", 906288492058L);
                startParams.putString("apiKey", "AIzaSyDDGDRptJWLROo7XFhYVinwH4fQ1r0o5Qw");

                vertx.eventBus().publish("start-gcm-services", startParams);
            }
        });
    }

    @Test
    public void testXmppWithAmplifyTablet() {

        container.logger().info("sending a message!");

        JsonObject payload = new JsonObject();
        JsonObject messageParams = new JsonObject();
        payload.putString("Hello", "World");
        payload.putString("CCS", "Dummy Message");

        messageParams.putObject("payload", payload);
        messageParams.putString("collapseKey", "sample");
        messageParams.putString("toRegId", "APA91bGmNQVWs0apw3ioVOGAqlbBw2lLxvw3jTBcBfgP_6Mr0u-900Fg9UOnXrsWO1woniZ_JwmMiZlXRHZ4BeFLGq89qp2PWpaif7br9F0l4Q612LFXGjIuUkNLC6UkHSozLYZiwvSNFX8ju9FAdYY0oTcEByloeA");
        messageParams.putNumber("timeToLive", 10000L);
        messageParams.putBoolean("delayWhileIdle", true);

        vertx.eventBus().send("send-message", messageParams, new Handler<Message>() {
            @Override
            public void handle(Message event) {
                testComplete();
            }
        });
    }
}
