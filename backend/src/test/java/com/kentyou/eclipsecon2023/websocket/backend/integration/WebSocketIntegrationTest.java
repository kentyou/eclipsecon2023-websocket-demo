/*********************************************************************
* Copyright (c) 2023 Kentyou.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*   Thomas Calmant (Kentyou) - initial implementation
**********************************************************************/
package com.kentyou.eclipsecon2023.websocket.backend.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.util.BlockingArrayQueue;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.test.common.annotation.Property;
import org.osgi.test.common.annotation.config.WithConfiguration;
import org.osgi.test.junit5.cm.ConfigurationExtension;

@ExtendWith(ConfigurationExtension.class)
@WithConfiguration(pid = "org.apache.felix.http", location = "?", properties = @Property(key = "org.osgi.service.http.port", value = "14001"))
public class WebSocketIntegrationTest {

    /**
     * Utility class to auto-close a web socket client
     */
    class WSClient implements AutoCloseable {
        WebSocketClient ws;

        public WSClient() throws Exception {
            ws = new WebSocketClient();
            ws.start();
        }

        @Override
        public void close() throws Exception {
            ws.stop();
            ws.destroy();
        }
    }

    @Test
    void testEcho() throws Exception {
        final BlockingArrayQueue<String> queue = new BlockingArrayQueue<>(16);
        final AtomicReference<Session> session = new AtomicReference<>();
        final CountDownLatch barrier = new CountDownLatch(1);

        final WSHandler handler = new WSHandler();
        handler.onConnect = (s) -> {
            session.set(s);
            barrier.countDown();
        };
        handler.onError = (s, e) -> fail(e);
        handler.onMessage = (s, m) -> queue.offer(m);

        try (WSClient wsClient = new WSClient()) {
            WebSocketClient ws = wsClient.ws;
            ws.connect(handler, new URI("ws://localhost:14001/ws/test"));
            barrier.await(5, TimeUnit.SECONDS);

            final String text = "Hello, World!";
            session.get().getRemote().sendString(text);
            final String result = queue.poll(5, TimeUnit.SECONDS);
            assertNotNull(result);
            assertEquals("Echo: " + text, result);
        }
    }
}
