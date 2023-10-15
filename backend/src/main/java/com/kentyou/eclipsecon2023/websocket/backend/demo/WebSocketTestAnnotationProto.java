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
package com.kentyou.eclipsecon2023.websocket.backend.demo;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.ServiceScope;

import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

@Component(service = WebSocketTestAnnotationProto.class, scope = ServiceScope.PROTOTYPE, property = {
		"websocket.server=true" })
@ServerEndpoint("/ws/test-annotation-proto")
public class WebSocketTestAnnotationProto {

	private static AtomicInteger nextId = new AtomicInteger();

	private String id;

	private String lastMessage;

	@Activate
	void activate() {
		id = "annotation-proto-" + nextId.incrementAndGet();
		System.out.println("WebSocket prototype annotation started - ID=" + id);
	}

	@Deactivate
	void deactivate() {
		System.out.println("WebSocket prototype annotation stopped - ID=" + id);
	}

	@OnMessage
	public void onMessage(String message, Session session) {
		final String toReturn;
		if ("$".equals(message)) {
			toReturn = "Last message you sent to " + id + " was: " + lastMessage;
		} else {
			toReturn = "Echo from " + id + ": " + message;
			lastMessage = message;
		}

		try {
			session.getBasicRemote().sendText(toReturn);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@OnOpen
	public void onConnect(Session session) {
		try {
			session.getBasicRemote().sendText("Hello from " + id);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
