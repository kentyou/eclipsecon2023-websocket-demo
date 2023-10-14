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

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

@Component(service = Endpoint.class, immediate = true, property = { "websocket.server=true",
        "websocket.path=/ws/test-endpoint" })
public class WebSocketTestEndpoint extends Endpoint {

    @Activate
    void activate() {
        System.out.println("WebSocket test started");
    }

    private void onMessage(String message, Session session) {
        System.out.println("Echoing " + message);
        try {
            session.getBasicRemote().sendText("Echo2: " + message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        System.out.println("ECHO 2 endpoint connection");
        session.addMessageHandler(String.class, (s) -> onMessage(s, session));
    }
}
