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

import java.util.Set;

import org.glassfish.tyrus.core.TyrusWebSocketEngine;
import org.glassfish.tyrus.server.TyrusServerContainer;
import org.glassfish.tyrus.spi.WebSocketEngine;

import jakarta.websocket.DeploymentException;
import jakarta.websocket.server.ServerEndpointConfig;

public class WSServerContainer extends TyrusServerContainer {

    private final TyrusWebSocketEngine engine;
    private final String contextPath;
    private final TyrusServletUpgrade tyrusServletUpgrade;

    public WSServerContainer(final String contextPath) {
        super(Set.of());

        this.contextPath = contextPath;
        this.engine = TyrusWebSocketEngine.builder(this).build();
        this.tyrusServletUpgrade = new TyrusServletUpgrade(engine);
    }

    @Override
    public WebSocketEngine getWebSocketEngine() {
        return engine;
    }

    TyrusServletUpgrade getServletUpgrade() {
        return tyrusServletUpgrade;
    }

    @Override
    public void stop() {
        tyrusServletUpgrade.stop();
        super.stop();
    }

    @Override
    public void register(Class<?> endpointClass) throws DeploymentException {
        engine.register(endpointClass, contextPath);
    }

    @Override
    public void register(ServerEndpointConfig serverEndpointConfig) throws DeploymentException {
        engine.register(serverEndpointConfig, contextPath);
    }
}
