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
package com.kentyou.eclipsecon2023.websocket.backend;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/ws/test-annotation")
@Component(service = WebSocketTestAnnotation.class, immediate = true, property = { "websocket.server=true" })
public class WebSocketTestAnnotation {

    @Activate
    void activate() {
        System.out.println("WebSocket test started");
    }

    @OnOpen
    public void onConnect() {
        System.out.println("ECHO endpoint connection");
    }

    @OnMessage
    public void onMessage(String message, Session session) throws Exception {
        System.out.println("Echoing " + message);
        session.getBasicRemote().sendText("Echo: " + message);
    }
}
