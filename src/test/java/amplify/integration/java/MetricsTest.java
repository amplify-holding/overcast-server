package amplify.integration.java;

import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.testtools.TestVerticle;

import static org.vertx.testtools.VertxAssert.assertEquals;
import static org.vertx.testtools.VertxAssert.assertTrue;
import static org.vertx.testtools.VertxAssert.testComplete;


public class MetricsTest extends TestVerticle {

    @Override
    public void start() {
        initialize();

        container.deployWorkerVerticle("amplify.MetricsVerticle", new JsonObject(), 1, false, new Handler<AsyncResult<String>>() {
            @Override
            public void handle(AsyncResult<String> stringAsyncResult) {
                container.logger().info("Metrics client is up");
                assertTrue(stringAsyncResult.succeeded());
                startTests();
            }
        });
    }

    @Test
    public void testGameAnalytics() {
        vertx.eventBus().send("send-metric", getContentJson("8f64a3b5-84c9-4932-9715-48e9456654b1", "a7df63e1-6e46-45b1-9b09-4d61ee0a0b8b", "test:callGA", 1.0), new Handler<Message<Integer>>() {
            @Override
            public void handle(Message<Integer> event) {
                assertEquals(200L, (long)event.body());
                testComplete();
            }
        });
    }

    private JsonObject getContentJson(String userId, String sessionId, String eventId, double value){

        JsonObject jsonObject = new JsonObject();
        jsonObject.putString("user_id", userId);
        jsonObject.putString("session_id", sessionId);
        jsonObject.putString("build", "DesignTest");
        jsonObject.putString("event_id", eventId);
        jsonObject.putNumber("value", value);

        return jsonObject;
    }
}
