/*
* Copyright 2012-2013 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package amplify.integration.java;

import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.testtools.TestVerticle;

import static org.vertx.testtools.VertxAssert.assertNotNull;
import static org.vertx.testtools.VertxAssert.assertTrue;
import static org.vertx.testtools.VertxAssert.testComplete;

/**
 * @author <a href="mailto:atarno@gmail.com">Asher Tarnopolski</a>
 *         <p/>
 */
public class GCMTestClient extends TestVerticle {
    String address;

    @Override
    public void start() {
        initialize();

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
                container.logger().info("GCM client is up");
                assertTrue(stringAsyncResult.succeeded());
                assertNotNull("deploymentID should not be null", stringAsyncResult.result());
                startTests();
            }
        });
    }

    @Test
    public void testSomethingElse() {
        // Whatever
        testComplete();
    }

    @Test
    public void testValidNotification() {
        JsonObject notif = new JsonObject();
        notif.putString( "api_key",  "AIzaSyAFRTb3omOobZnqPi-XR5JE_USjuG20Mrk" );

        JsonObject data = new JsonObject();
        data.putString( "action", "TEXT" );
        data.putString( "sender", "vertx-gcm" );
        data.putString( "message_title", "Test * Test * Test" );
        data.putString( "message_text", "Hello world" );

        JsonObject n = new JsonObject();
        n.putString( "collapse_key", "key" );
        n.putNumber( "time_to_live", 60 * 10 );
        n.putBoolean( "delay_while_idle", false );
        //n.putBoolean( "dry_run", true );
        //n.putString("restricted_package_name", "");
        n.putObject( "data", data );
        n.putArray( "registration_ids", new JsonArray(
                new String[]{ "token0",
                        "token1",
                        "token2" } ) );

        notif.putObject( "notification", n );
        Handler<Message<JsonObject>> replyHandler = new Handler<Message<JsonObject>>() {
            public void handle( Message<JsonObject> message ) {
                System.out.println( "received: \n" + message.body().encode() );
                testComplete();
            }
        };
        vertx.eventBus().send( address, notif, replyHandler );
    }
}
