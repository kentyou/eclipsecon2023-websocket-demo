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

import org.osgi.service.component.annotations.Component;

import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/test")
@Component(service = WebSocketTest.class, immediate = true)
public class WebSocketTest {

    @OnMessage
    public void onMessage(String message, Session session) throws Exception {
        session.getBasicRemote().sendText("Echo: " + message);
    }
}
