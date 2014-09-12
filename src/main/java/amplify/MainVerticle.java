package amplify;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;

import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class MainVerticle extends Verticle {
    private String address;
    private Set<String> registrationIds;
    private int acksPending = 0;
    private long totalBulkDuration = 0;

    public void start() {
        registrationIds = new HashSet<>();
        try {
            NTPClient.syncServerTime("time-d.nist.gov");
        } catch (IOException e) {
            container.logger().error(e.toString(), e);
            vertx.eventBus().send("send-metric", MetricsVerticle.getErrorMetricJson(e.toString(), GASeverity.ERROR));
        }

        container.deployWorkerVerticle("amplify.MetricsVerticle", new JsonObject(), 1, true, new Handler<AsyncResult<String>>() {
            @Override
            public void handle(AsyncResult<String> event) {
                setupXmpp();
                setupHttp();
            }
        });

        vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
            public void handle(HttpServerRequest request) {
                String path = request.path();

                container.logger().info("Server request received: " + path);

                if (path.contains("http/performance/metrics")) {
                    final MultiMap params = request.params();

                    final String startTimeEncoded = params.get("sentTime");
                    final String endTimeEncoded = params.get("receivedTime");

                    request.bodyHandler(new Handler<Buffer>() {
                        @Override
                        public void handle(Buffer event) {
                            if (params.contains("perf-type")) {
                                sendDurationMetric(startTimeEncoded, endTimeEncoded, event.toString(), "http", params.get("perf-type"));

                                if (--acksPending == 0) {
                                    totalBulkDuration = Long.parseLong(endTimeEncoded) - totalBulkDuration;
                                    sendBulkDurationMetric(totalBulkDuration, "http");
                                }
                            } else {
                                sendDurationMetric(startTimeEncoded, endTimeEncoded, event.toString(), "http", "");
                            }
                        }
                    });
                } else if (path.contains("xmpp/performance/metrics")) {
                    final MultiMap params = request.params();

                    final String startTimeEncoded = params.get("sentTime");
                    final String endTimeEncoded = params.get("receivedTime");

                    request.bodyHandler(new Handler<Buffer>() {
                        @Override
                        public void handle(Buffer event) {
                            if (params.contains("perf-type")) {
                                sendDurationMetric(startTimeEncoded, endTimeEncoded, event.toString(), "xmpp", params.get("perf-type"));

                                if (--acksPending == 0) {
                                    totalBulkDuration = Long.parseLong(endTimeEncoded) - totalBulkDuration;
                                    sendBulkDurationMetric(totalBulkDuration, "xmpp");
                                }
                            } else {
                                sendDurationMetric(startTimeEncoded, endTimeEncoded, event.toString(), "xmpp", "");
                            }
                        }
                    });
                } else if (path.contains("xmpp/performance/bulk_send")) {
                    MultiMap params = request.params();
                    final String sizeEncoded = params.get("size");
                    acksPending = Integer.decode(sizeEncoded);
                    totalBulkDuration = NTPClient.getSyncedTime();

                    callXmppBulk(acksPending, false);
                } else if (path.contains("http/performance/bulk_send")) {
                    MultiMap params = request.params();
                    final String sizeEncoded = params.get("size");
                    acksPending = Integer.decode(sizeEncoded);
                    totalBulkDuration = NTPClient.getSyncedTime();

                    callHttpBulk(acksPending, false);
                } else if (path.contains("http/performance/multicast")) {

                } else if (path.contains("xmpp/performance")) {
                    callXmpp(true, getStandardPayload("xmpp"), 10000L);
                } else if (path.contains("http/performance")) {
                    callHttp(true, getStandardPayload("http"), 10000L);
                } else if (path.contains("registration")) {
                    request.bodyHandler(new Handler<Buffer>() {
                        @Override
                        public void handle(Buffer event) {
                            container.logger().info("Adding registration id: " + event.toString());
                            registrationIds.add(event.toString());
                        }
                    });
                }

                request.response().end();
            }
        }).listen(8080, "192.168.1.9");
    }

    private long sendDurationMetric(String startTimeEncoded, String endTimeEncoded, String regId, String protocol, String durationType) {
        long startTime = Long.parseLong(startTimeEncoded);
        long endTime = Long.parseLong(endTimeEncoded);
        long diff = endTime - startTime;

        double timeInSeconds = diff / 1000.0;

        container.logger().info("Transmission duration: " + diff + ", for reg id:" + regId);
        vertx.eventBus().send("send-metric", MetricsVerticle.getDesignMetricJson("performance:+"+protocol+":travelTime:" + durationType, regId, timeInSeconds));

        return diff;
    }

    private void sendBulkDurationMetric(long diff, String protocol) {
        double timeInSeconds = diff / 1000.0;

        container.logger().info("Bulk transmission duration(s): " + timeInSeconds);
        vertx.eventBus().send("send-metric", MetricsVerticle.getDesignMetricJson("performance:+"+protocol+":bulkTravelTime", "bulk-user", timeInSeconds));
    }

    private void setupXmpp() {
        vertx.eventBus().registerHandler("authenticated", new Handler<Message>() {
            @Override
            public void handle(Message event) {
                container.logger().info("Xmpp Service is up!");
            }
        });

        container.deployWorkerVerticle("amplify.XmppVerticle", new JsonObject(), 1, true, new AsyncResultHandler<String>() {
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

        container.deployWorkerVerticle("net.atarno.vertx.gcm.server.GCMServer", config, 1, true, new Handler<AsyncResult<String>>() {
            @Override
            public void handle(AsyncResult<String> stringAsyncResult) {
                container.logger().info("Http GCM Server is up");
            }
        });
    }

    private void callXmppBulk(int size, boolean withCollapseKey) {
        JsonObject payload = getStandardPayload("xmpp");

        payload.putString("perf-type", "bulk");
        for(int i = 0; i < size; ++i) {
            callXmpp(withCollapseKey, payload, 0);
        }
    }

    private void callHttpBulk(int size, boolean withCollapseKey) {
        JsonObject payload = getStandardPayload("http");

        payload.putString("perf-type", "bulk");
        for(int i = 0; i < size; ++i) {
            callHttp(withCollapseKey, payload, 0);
        }
    }

    private void callXmpp(boolean withCollapseKey, JsonObject payload, long timeToLive) {
        JsonObject messageParams = new JsonObject();

        messageParams.putObject("payload", payload);
        if(withCollapseKey)
            messageParams.putString("collapseKey", "sample");
        messageParams.putString("toRegId", getRandomRegId());
        messageParams.putNumber("timeToLive", timeToLive);
        messageParams.putBoolean("delayWhileIdle", true);

        vertx.eventBus().send("send-message", messageParams);
    }

    private void callHttp(boolean withCollapseKey, JsonObject payload, long timeToLive) {
        JsonObject notif = new JsonObject();
        notif.putString( "api_key",  "AIzaSyDDGDRptJWLROo7XFhYVinwH4fQ1r0o5Qw" );

        JsonObject n = new JsonObject();
        if(withCollapseKey)
            n.putString( "collapse_key", "key" );
        n.putNumber( "time_to_live", timeToLive);
        n.putBoolean( "delay_while_idle", false );
        n.putObject( "data", payload );
        n.putArray( "registration_ids", new JsonArray(new String[]{ getRandomRegId() } ) );

        notif.putObject( "notification", n );
        Handler<Message<JsonObject>> replyHandler = new Handler<Message<JsonObject>>() {
            public void handle( Message<JsonObject> message ) {
                container.logger().debug("Http received: \n" + message.body().encode());
            }
        };
        vertx.eventBus().send( address, notif, replyHandler );
    }

    private JsonObject getStandardPayload(String protocol) {
        JsonObject data = new JsonObject();
        data.putString( "action", "TEXT" );
        data.putString( "sender", "vertx-gcm" );
        data.putString( "message_title", "Test * Test * Test" );
        data.putString( "message_text", "Hello world" );
        data.putString( "sent-time", NTPClient.getSyncedTime().toString());
        data.putString( "protocol-used", protocol);

        return data;
    }

    private String getRandomRegId() {
        String foundId;

        try {
            int picked = new Random().nextInt(registrationIds.size());
            String[] ids = new String[registrationIds.size()];
            registrationIds.toArray(ids);
            foundId = ids[picked];
        }catch(RuntimeException e) {
            foundId = "fakeID";
        }

        return foundId;
    }
}
