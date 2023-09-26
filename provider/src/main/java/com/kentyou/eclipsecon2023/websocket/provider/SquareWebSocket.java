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
package com.kentyou.eclipsecon2023.websocket.provider;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/ws/square")
@Component(service = SquareWebSocket.class, immediate = true, property = { "websocket.server=true" })
public class SquareWebSocket {

    @Activate
    void activate() {
        System.out.println("Square WebSocket provider activated");
    }

    @Deactivate
    void deactivate() {
        System.out.println("Square WebSocket provider deactivated");
    }

    @OnMessage
    public void onMessage(String message, Session session) throws Exception {
        try {
            long value = Long.parseLong(message);
            session.getBasicRemote().sendText(String.valueOf(value * value));
        } catch (NumberFormatException e) {
            session.getBasicRemote().sendText("Error: " + e);
        }
    }
}
