/*
 * Copyright (c) 2012, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.kentyou.eclipsecon2023.websocket.backend;

import java.io.IOException;
import java.net.URI;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.glassfish.tyrus.core.RequestContext;
import org.glassfish.tyrus.core.TyrusUpgradeResponse;
import org.glassfish.tyrus.core.TyrusWebSocketEngine;
import org.glassfish.tyrus.core.Utils;
import org.glassfish.tyrus.spi.UpgradeResponse;
import org.glassfish.tyrus.spi.WebSocketEngine;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerContainer;

public class TyrusServletUpgrade {
    private static final Logger LOGGER = Logger.getLogger(TyrusServletUpgrade.class.getName());
    private TyrusWebSocketEngine engine;

    // I don't like this map, but it seems like it is necessary. I am forced to
    // handle subscriptions
    // for HttpSessionListener because the listener itself must be registered
    // *before* ServletContext
    // initialization.
    // I could create List of listeners and send a create something like
    // sessionDestroyed(HttpSession s)
    // but that would take more time (statistically higher number of comparisons).
    private final Map<HttpSession, TyrusHttpUpgradeHandler> sessionToHandler = new ConcurrentHashMap<HttpSession, TyrusHttpUpgradeHandler>();

    private org.glassfish.tyrus.server.TyrusServerContainer serverContainer = null;

    TyrusServletUpgrade(TyrusWebSocketEngine engine) {
        this.engine = engine;
    }

    void init(ServletContext servletContext) throws ServletException {

        this.serverContainer = (org.glassfish.tyrus.server.TyrusServerContainer) servletContext
                .getAttribute(ServerContainer.class.getName());

        try {
            // TODO? - port/contextPath .. is it really relevant here?
            serverContainer.start(servletContext.getContextPath(), 0);
        } catch (Exception e) {
            throw new ServletException("Web socket server initialization failed.", e);
        } finally {
            serverContainer.doneDeployment();
        }
    }

    /**
     * provide the HTTP upgrade
     *
     * @param httpServletRequest  servlet request
     * @param httpServletResponse servlet response
     * @return Return true if response is set, i.e. in filter chain the next filter
     *         should not be invoked.
     * @throws IOException
     * @throws ServletException
     */
    boolean upgrade(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
            throws IOException, ServletException {
        // check for mandatory websocket header
        final String header = httpServletRequest.getHeader(HandshakeRequest.SEC_WEBSOCKET_KEY);
        if (header != null) {
            LOGGER.fine("Setting up WebSocket protocol handler");

            final Map<String, String[]> paramMap = httpServletRequest.getParameterMap();

            final RequestContext requestContext = RequestContext.Builder.create()
                    .requestURI(URI.create(httpServletRequest.getRequestURI()))
                    .queryString(httpServletRequest.getQueryString()).httpSession(httpServletRequest.getSession(false))
                    .secure(httpServletRequest.isSecure()).userPrincipal(httpServletRequest.getUserPrincipal())
                    .isUserInRoleDelegate(new RequestContext.Builder.IsUserInRoleDelegate() {
                        @Override
                        public boolean isUserInRole(String role) {
                            return httpServletRequest.isUserInRole(role);
                        }
                    }).parameterMap(paramMap).remoteAddr(httpServletRequest.getRemoteAddr())
                    .serverAddr(httpServletRequest.getLocalName() == null ? httpServletRequest.getLocalAddr()
                            : httpServletRequest.getLocalName())
                    .serverPort(httpServletRequest.getLocalPort())
                    .tyrusProperties(getInitParams(httpServletRequest.getServletContext())).build();

            Enumeration<String> headerNames = httpServletRequest.getHeaderNames();

            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();

                Enumeration<String> headerValues = httpServletRequest.getHeaders(name);

                while (headerValues.hasMoreElements()) {

                    final List<String> values = requestContext.getHeaders().get(name);
                    if (values == null) {
                        requestContext.getHeaders().put(name,
                                Utils.parseHeaderValue(headerValues.nextElement().trim()));
                    } else {
                        values.addAll(Utils.parseHeaderValue(headerValues.nextElement().trim()));
                    }
                }
            }

            final TyrusUpgradeResponse tyrusUpgradeResponse = new TyrusUpgradeResponse();
            final WebSocketEngine.UpgradeInfo upgradeInfo = engine.upgrade(requestContext, tyrusUpgradeResponse);
            switch (upgradeInfo.getStatus()) {
            case HANDSHAKE_FAILED:
                appendTraceHeaders(httpServletResponse, tyrusUpgradeResponse);
                httpServletResponse.sendError(tyrusUpgradeResponse.getStatus());
                break;
            case NOT_APPLICABLE:
                appendTraceHeaders(httpServletResponse, tyrusUpgradeResponse);
                return false;
            case SUCCESS:
                LOGGER.fine("Upgrading Servlet request");
                // Setup status & header for Jetty to accept the upgrade operation
                httpServletResponse.setStatus(tyrusUpgradeResponse.getStatus());
                for (Map.Entry<String, List<String>> entry : tyrusUpgradeResponse.getHeaders().entrySet()) {
                    httpServletResponse.addHeader(entry.getKey(), Utils.getHeaderFromList(entry.getValue()));
                }

                // Let Jetty create the handler instance and call init
                // Here, the init() doesn't upgrade the connection as the instance isn't configured yet
                final TyrusHttpUpgradeHandler handler = httpServletRequest.upgrade(TyrusHttpUpgradeHandler.class);

                // Configure the instance
                handler.setAuthenticated(httpServletRequest.getUserPrincipal() != null);

                final String frameBufferSize = httpServletRequest.getServletContext()
                        .getInitParameter(TyrusHttpUpgradeHandler.FRAME_BUFFER_SIZE);
                if (frameBufferSize != null) {
                    handler.setIncomingBufferSize(Integer.parseInt(frameBufferSize));
                }

                // Upgrade the connection for real
                handler.upgradeConnection(upgradeInfo);

                if (requestContext.getHttpSession() != null) {
                    sessionToHandler.put((HttpSession) requestContext.getHttpSession(), handler);
                }

                httpServletResponse.flushBuffer();
                LOGGER.fine("Handshake Complete");
                break;
            }
            return true;
        }

        return false;
    }

    TyrusHttpUpgradeHandler destroySession(HttpSession session) {
        final TyrusHttpUpgradeHandler upgradeHandler = sessionToHandler.get(session);
        if (upgradeHandler != null) {
            sessionToHandler.remove(session);
            upgradeHandler.sessionDestroyed();
        }
        return upgradeHandler;
    }

    private static void appendTraceHeaders(HttpServletResponse httpServletResponse,
            TyrusUpgradeResponse tyrusUpgradeResponse) {
        for (Map.Entry<String, List<String>> entry : tyrusUpgradeResponse.getHeaders().entrySet()) {
            if (entry.getKey().contains(UpgradeResponse.TRACING_HEADER_PREFIX)) {
                httpServletResponse.addHeader(entry.getKey(), Utils.getHeaderFromList(entry.getValue()));
            }
        }
    }

    private Map<String, Object> getInitParams(ServletContext ctx) {
        Map<String, Object> initParams = new HashMap<>();
        Enumeration<String> enumeration = ctx.getInitParameterNames();
        while (enumeration.hasMoreElements()) {
            String initName = enumeration.nextElement();
            initParams.put(initName, ctx.getInitParameter(initName));
        }
        return initParams;
    }

    public void destroy() {
        sessionToHandler.forEach((session, upgradeHandler) -> upgradeHandler.destroy());
        sessionToHandler.clear();

        serverContainer.stop();
        engine.getApplicationEventListener().onApplicationDestroyed();
        serverContainer = null;
        engine = null;
    }
}
