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

import java.io.IOException;

import org.eclipse.jetty.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.websocket.server.JettyWebSocketServletFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.http.whiteboard.annotations.RequireHttpWhiteboard;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

@Component(service = { Servlet.class, Filter.class })
@RequireHttpWhiteboard
public class WebSocketRegistrar extends JettyWebSocketServlet implements Filter {

    private static final long serialVersionUID = 1L;

    @Activate
    void activate() {
        // TODO Read configuration
    }

    @Deactivate
    void stop() {
        // TODO Close all web sockets
    }

    @Override
    public void init() throws ServletException {
        // Block the default initialization
    }

    @Override
    protected void configure(JettyWebSocketServletFactory factory) {
        // TODO: set the WebSocket session pool using with factory.setCreator
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        // TODO Auto-generated method stub

    }
}
