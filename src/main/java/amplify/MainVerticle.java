package amplify;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;

import java.io.IOException;


public class MainVerticle extends Verticle {
    private String address;

    public void start() {
        try {
            NTPClient.syncServerTime("time-d.nist.gov");
        } catch (IOException e) {
            container.logger().error(e.toString(), e);
            vertx.eventBus().send("send-metric", MetricsVerticle.getErrorMetricJson(e.toString(), GASeverity.ERROR));
        }

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

                if (request.path().contains("http/performance/metrics")) {
                    MultiMap params = request.params();

                    final String startTimeEncoded = params.get("sentTime");
                    final String endTimeEncoded = params.get("receivedTime");

                    request.bodyHandler(new Handler<Buffer>() {
                        @Override
                        public void handle(Buffer event) {
                            sendDurationMetric(startTimeEncoded, endTimeEncoded, event.toString(), "http");
                        }
                    });
                } else if (request.path().contains("xmpp/performance/metrics")) {
                    MultiMap params = request.params();

                    final String startTimeEncoded = params.get("sentTime");
                    final String endTimeEncoded = params.get("receivedTime");

                    request.bodyHandler(new Handler<Buffer>() {
                        @Override
                        public void handle(Buffer event) {
                            sendDurationMetric(startTimeEncoded, endTimeEncoded, event.toString(), "xmpp");
                        }
                    });
                } else if (request.path().contains("xmpp/performance")) {
                    container.logger().info("sending a xmpp message!");
                    callXmpp(true);
                } else if (request.path().contains("http/performance")) {
                    callHttp(true);
                }

                request.response().end();
            }
        }).listen(8080, "192.168.1.9");
    }

    private void callXmpp(boolean withCollapseKey) {
        JsonObject payload = new JsonObject();
        JsonObject messageParams = new JsonObject();
        payload.putString("Hello", "World");
        payload.putString("CCS", "Dummy Message");

        messageParams.putObject("payload", payload);
        if(withCollapseKey)
            messageParams.putString("collapseKey", "sample");
        messageParams.putString("toRegId", "APA91bHuZ-obPLppJuN_JtS5HWS55k8YcJyQYAzyp1x4jz678rxhRhtYlAcGHryf9z5eS5WxaaCdJJKdRccGJNb4T70OTFrW5FNYje1_U2wcg7X5lwhbZZ-d1pTDodU7_7zvna_Z5_hJIQKQKaENQzIg6fvcLqx-AQ");
        messageParams.putNumber("timeToLive", 10000L);
        messageParams.putBoolean("delayWhileIdle", true);

        vertx.eventBus().send("send-message", messageParams);
    }

    private void sendDurationMetric(String startTimeEncoded, String endTimeEncoded, String regId, String protocol) {
        long startTime = Long.getLong(startTimeEncoded, Long.MAX_VALUE);
        long endTime = Long.getLong(endTimeEncoded, Long.MIN_VALUE);

        double timeInSeconds = (endTime - startTime) / 1000.0;

        container.logger().trace("Transmission duration(s): " + timeInSeconds + ", for reg id:" + regId);
        vertx.eventBus().send("send-metric", MetricsVerticle.getDesignMetricJson("performance:+"+protocol+":travelTime", regId, timeInSeconds));
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

    private void callHttp(boolean withCollapseKey) {
        JsonObject notif = new JsonObject();
        notif.putString( "api_key",  "AIzaSyDDGDRptJWLROo7XFhYVinwH4fQ1r0o5Qw" );

        JsonObject data = new JsonObject();
        data.putString( "action", "TEXT" );
        data.putString( "sender", "vertx-gcm" );
        data.putString( "message_title", "Test * Test * Test" );
        data.putString( "message_text", "Hello world" );
        data.putString( "sent-time", NTPClient.getSyncedTime().toString());
        data.putString( "protocol-used", "http");

        JsonObject n = new JsonObject();
        if(withCollapseKey)
            n.putString( "collapse_key", "key" );
        n.putNumber( "time_to_live", 60 * 10 );
        n.putBoolean( "delay_while_idle", false );
        n.putObject( "data", data );
        n.putArray( "registration_ids", new JsonArray(
                new String[]{"APA91bHuZ-obPLppJuN_JtS5HWS55k8YcJyQYAzyp1x4jz678rxhRhtYlAcGHryf9z5eS5WxaaCdJJKdRccGJNb4T70OTFrW5FNYje1_U2wcg7X5lwhbZZ-d1pTDodU7_7zvna_Z5_hJIQKQKaENQzIg6fvcLqx-AQ"
                            } ) );

        notif.putObject( "notification", n );
        Handler<Message<JsonObject>> replyHandler = new Handler<Message<JsonObject>>() {
            public void handle( Message<JsonObject> message ) {
                container.logger().debug("Http received: \n" + message.body().encode());
            }
        };
        vertx.eventBus().send( address, notif, replyHandler );
    }
}
