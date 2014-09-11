package amplify.integration.java;

import amplify.MetricsVerticle;
import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.testtools.TestVerticle;

import static org.vertx.testtools.VertxAssert.*;


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
        vertx.eventBus().send("send-metric", MetricsVerticle.getDesignMetricJson("test:callGA", 1.0), new Handler<Message<Integer>>() {
            @Override
            public void handle(Message<Integer> event) {
                assertEquals(200L, (long)event.body());
                testComplete();
            }
        });
    }
}
