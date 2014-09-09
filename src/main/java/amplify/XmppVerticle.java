package amplify;

import org.jivesoftware.smack.SmackException;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;

public class XmppVerticle extends Verticle {
    private static XmppGcmClient gcmClient;
    private int holdValue = 4;

    public void start() {
        gcmClient = new XmppGcmClient();
        vertx.eventBus().registerHandler("start-gcm-services", new Handler<org.vertx.java.core.eventbus.Message<JsonObject>>() {
            @Override
            public void handle(org.vertx.java.core.eventbus.Message<JsonObject> message) {
                container.logger().info("Starting XMPP service.");
                container.logger().info("Hello number " + holdValue++);

                try {
                    JsonObject messageDetails = message.body();
                    gcmClient.connect(messageDetails.getLong("senderId"), messageDetails.getString("apiKey"));

                    message.reply();
                } catch (Exception e) {
                    message.fail(0, e.toString());
                    container.logger().error("Connecting to the xmpp server failed: " + e.toString());
                }

                container.logger().info("Sent back pong after a " + message.body());
                vertx.eventBus().send("authenticated", new JsonObject());
            }
        });

        vertx.eventBus().registerHandler("send-message", new Handler<org.vertx.java.core.eventbus.Message<JsonObject>>() {
            @Override
            public void handle(org.vertx.java.core.eventbus.Message<JsonObject> message) {
                container.logger().info("sending message through XMPP service.");
                container.logger().info("Hello number " + holdValue++);

                JsonObject messageDetails = message.body();
                String encodedMessage = XmppGcmClient.transformSendMessage(messageDetails, XmppGcmClient.nextMessageId());
                try {
                    gcmClient.sendDownstreamMessage(encodedMessage);
                    message.reply();
                } catch (SmackException.NotConnectedException e) {
                    message.fail(0, e.toString());
                }
                container.logger().info("Sent back pong after a " + message.body());
            }
        });

        container.logger().info("Starting XMPP Verticle.");
    }
}
