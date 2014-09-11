package amplify;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;


public class MainVerticle extends Verticle {
    private String address;
    private HttpClient client;

    public void start() {
        container.deployVerticle("amplify.MetricsVerticle", new Handler<AsyncResult<String>>() {
            @Override
            public void handle(AsyncResult<String> event) {
                setupXmpp();
                setupHttp();
            }
        });

        vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
            public void handle(HttpServerRequest request) {
                container.logger().info("A request has arrived on the server!");
                if(request.path().contains("xmpp/performance")) {
                    container.logger().info("sending a xmpp message!");

                    JsonObject payload = new JsonObject();
                    JsonObject messageParams = new JsonObject();
                    payload.putString("Hello", "World");
                    payload.putString("CCS", "Dummy Message");

                    messageParams.putObject("payload", payload);
                    messageParams.putString("collapseKey", "sample");
                    messageParams.putString("toRegId", "APA91bGmNQVWs0apw3ioVOGAqlbBw2lLxvw3jTBcBfgP_6Mr0u-900Fg9UOnXrsWO1woniZ_JwmMiZlXRHZ4BeFLGq89qp2PWpaif7br9F0l4Q612LFXGjIuUkNLC6UkHSozLYZiwvSNFX8ju9FAdYY0oTcEByloeA");
                    messageParams.putNumber("timeToLive", 10000L);
                    messageParams.putBoolean("delayWhileIdle", true);

                    vertx.eventBus().send("send-message", messageParams);
                }
                else if (request.path().contains("http/performance")) {
                    callHttp();
                }

                request.response().end();
            }
        }).listen(8080, "localhost");
    }

    private void setupXmpp() {
        vertx.eventBus().registerHandler("authenticated", new Handler<Message>() {
            @Override
            public void handle(Message event) {
                container.logger().info("Xmpp Service is up!");
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

    private void setupHttp() {
        address = "test.vertx.gcm";

        JsonObject config = new JsonObject();
        config.putString( "address", address );
        config.putNumber( "gcm_registration_ids_limit", 1000 );//gcm default
        config.putNumber( "gcm_max_seconds_to_leave", 2419200 );//gcm default
        config.putNumber( "gcm_backoff_retries", 5 );
        config.putString( "gcm_url", "https://android.googleapis.com/gcm/send" );

        container.deployWorkerVerticle("net.atarno.vertx.gcm.server.GCMServer", config, 1, false, new Handler<AsyncResult<String>>() {
            @Override
            public void handle(AsyncResult<String> stringAsyncResult) {
                container.logger().info("Http GCM Server is up");
            }
        });
    }

    private void callHttp() {
        JsonObject notif = new JsonObject();
        notif.putString( "api_key",  "AIzaSyDDGDRptJWLROo7XFhYVinwH4fQ1r0o5Qw" );

        JsonObject data = new JsonObject();
        data.putString( "action", "TEXT" );
        data.putString( "sender", "vertx-gcm" );
        data.putString( "message_title", "Test * Test * Test" );
        data.putString( "message_text", "Hello world" );

        JsonObject n = new JsonObject();
        n.putString( "collapse_key", "key" );
        n.putNumber( "time_to_live", 60 * 10 );
        n.putBoolean( "delay_while_idle", false );
        n.putObject( "data", data );
        n.putArray( "registration_ids", new JsonArray(
                new String[]{ "token0",
                        "APA91bGmNQVWs0apw3ioVOGAqlbBw2lLxvw3jTBcBfgP_6Mr0u-900Fg9UOnXrsWO1woniZ_JwmMiZlXRHZ4BeFLGq89qp2PWpaif7br9F0l4Q612LFXGjIuUkNLC6UkHSozLYZiwvSNFX8ju9FAdYY0oTcEByloeA"
                            } ) );

        notif.putObject( "notification", n );
        Handler<Message<JsonObject>> replyHandler = new Handler<Message<JsonObject>>() {
            public void handle( Message<JsonObject> message ) {
                System.out.println("Http received: \n" + message.body().encode());
            }
        };
        vertx.eventBus().send( address, notif, replyHandler );
    }
}
