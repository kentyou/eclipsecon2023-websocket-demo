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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URI;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.util.BlockingArrayQueue;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.Property;
import org.osgi.test.common.annotation.config.WithConfiguration;
import org.osgi.test.junit5.cm.ConfigurationExtension;
import org.osgi.test.junit5.context.BundleContextExtension;

import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnMessage;
import jakarta.websocket.server.ServerEndpoint;

@ExtendWith(ConfigurationExtension.class)
@ExtendWith(BundleContextExtension.class)
@WithConfiguration(pid = "org.apache.felix.http", location = "?", properties = @Property(key = "org.osgi.service.http.port", value = "14001"))
public class WebSocketIntegrationTest {

	@InjectBundleContext
	BundleContext bundleContext;

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
	void testEchoAnnotations() throws Exception {
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
			ws.connect(handler, new URI("ws://localhost:14001/ws/test-annotation"));
			assertTrue(barrier.await(1, TimeUnit.SECONDS));

			// Get welcome message
			final String welcome = queue.poll(1, TimeUnit.SECONDS);
			assertNotNull(welcome);
			assertTrue(welcome.startsWith("Hello"), welcome + " doesn't start with Hello");

			final String text = "Hello, World!";
			session.get().getRemote().sendString(text);
			final String result = queue.poll(1, TimeUnit.SECONDS);
			assertNotNull(result);
			assertTrue(result.contains(text), "Wrong echo");
		}
	}

	@Test
	void testEchoEndpoint() throws Exception {
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
			ws.connect(handler, new URI("ws://localhost:14001/ws/test-endpoint"));
			assertTrue(barrier.await(1, TimeUnit.SECONDS));

			// Get welcome message
			final String welcome = queue.poll(1, TimeUnit.SECONDS);
			assertNotNull(welcome);
			assertTrue(welcome.startsWith("Hello"), welcome + " doesn't start with Hello");

			final String text = "Hello, World!";
			session.get().getRemote().sendString(text);
			final String result = queue.poll(1, TimeUnit.SECONDS);
			assertNotNull(result);
			assertTrue(result.contains(text), "Wrong echo");
		}
	}

	/**
	 * This class needs to be public static to be usable
	 */
	@ServerEndpoint("/ws/answer")
	public static class TestServiceAnnotation {
		@OnMessage
		public void onMessage(String message, jakarta.websocket.Session s) throws Exception {
			s.getBasicRemote().sendText("42");
		}
	}

	@Test
	void testRegistration() throws Exception {

		final String endPoint = "/ws/answer";

		final BlockingArrayQueue<String> queue = new BlockingArrayQueue<>(16);
		final AtomicReference<Session> session = new AtomicReference<>();

		final WSHandler handler = new WSHandler();
		handler.onError = (s, e) -> {
			e.printStackTrace();
			fail(e);
		};
		handler.onMessage = (s, m) -> queue.offer(m);

		// Before service
		final CountDownLatch barrierBefore = new CountDownLatch(1);
		handler.onConnect = (s) -> {
			session.set(s);
			barrierBefore.countDown();
		};
		try (WSClient wsClient = new WSClient()) {
			WebSocketClient ws = wsClient.ws;
			ws.connect(handler, new URI("ws://localhost:14001" + endPoint));
			// Connection should fail
			assertFalse(barrierBefore.await(1, TimeUnit.SECONDS));
		}

		// With service
		final CountDownLatch barrier = new CountDownLatch(1);
		handler.onConnect = (s) -> {
			session.set(s);
			barrier.countDown();
		};
		final ServiceRegistration<TestServiceAnnotation> svcReg = bundleContext.registerService(
				TestServiceAnnotation.class, new TestServiceAnnotation(),
				new Hashtable<>(Map.of("websocket.server", "true")));
		try {
			try (WSClient wsClient = new WSClient()) {
				WebSocketClient ws = wsClient.ws;
				ws.connect(handler, new URI("ws://localhost:14001" + endPoint));
				assertTrue(barrier.await(1, TimeUnit.SECONDS));

				session.get().getRemote().sendString("run!");
				final String result = queue.poll(1, TimeUnit.SECONDS);
				assertNotNull(result);
				assertEquals("42", result);
			}
		} finally {
			svcReg.unregister();
		}

		// After service
		final CountDownLatch barrierAfter = new CountDownLatch(1);
		handler.onConnect = (s) -> {
			session.set(s);
			barrierAfter.countDown();
		};
		try (WSClient wsClient = new WSClient()) {
			WebSocketClient ws = wsClient.ws;
			ws.connect(handler, new URI("ws://localhost:14001" + endPoint));
			// Connection should fail
			assertFalse(barrierAfter.await(1, TimeUnit.SECONDS));
		}
	}

	/**
	 * This class needs to be public static to be usable
	 */
	public static class TestServiceEndpoint extends Endpoint {
		private void onMessage(String message, jakarta.websocket.Session session) {
			try {
				session.getBasicRemote().sendText("foobar");
			} catch (IOException e) {
				e.printStackTrace();
				fail(e);
			}
		}

		@Override
		public void onOpen(jakarta.websocket.Session session, EndpointConfig config) {
			session.addMessageHandler(String.class, (s) -> onMessage(s, session));
		}
	}

	@Test
	void testEndpointRegistration() throws Exception {

		final String endPoint = "/ws/answer-endpoint";

		final BlockingArrayQueue<String> queue = new BlockingArrayQueue<>(16);
		final AtomicReference<Session> session = new AtomicReference<>();

		final WSHandler handler = new WSHandler();
		handler.onError = (s, e) -> {
			e.printStackTrace();
			fail(e);
		};
		handler.onMessage = (s, m) -> queue.offer(m);

		// Before service
		final CountDownLatch barrierBefore = new CountDownLatch(1);
		handler.onConnect = (s) -> {
			session.set(s);
			barrierBefore.countDown();
		};
		try (WSClient wsClient = new WSClient()) {
			WebSocketClient ws = wsClient.ws;
			ws.connect(handler, new URI("ws://localhost:14001" + endPoint));
			// Connection should fail
			assertFalse(barrierBefore.await(1, TimeUnit.SECONDS));
		}

		// With service
		final CountDownLatch barrier = new CountDownLatch(1);
		handler.onConnect = (s) -> {
			session.set(s);
			barrier.countDown();
		};

		final ServiceRegistration<Endpoint> svcReg = bundleContext.registerService(Endpoint.class,
				new TestServiceEndpoint(),
				new Hashtable<String, Object>(Map.of("websocket.server", "true", "websocket.path", endPoint)));
		try {
			try (WSClient wsClient = new WSClient()) {
				WebSocketClient ws = wsClient.ws;
				ws.connect(handler, new URI("ws://localhost:14001" + endPoint));
				assertTrue(barrier.await(1, TimeUnit.SECONDS));

				session.get().getRemote().sendString("run!");
				final String result = queue.poll(1, TimeUnit.SECONDS);
				assertNotNull(result);
				assertEquals("foobar", result);
			}
		} finally {
			svcReg.unregister();
		}

		// After service
		final CountDownLatch barrierAfter = new CountDownLatch(1);
		handler.onConnect = (s) -> {
			session.set(s);
			barrierAfter.countDown();
		};
		try (WSClient wsClient = new WSClient()) {
			WebSocketClient ws = wsClient.ws;
			ws.connect(handler, new URI("ws://localhost:14001" + endPoint));
			// Connection should fail
			assertFalse(barrierAfter.await(1, TimeUnit.SECONDS));
		}
	}
}
