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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.glassfish.tyrus.core.TyrusServerEndpointConfigurator;
import org.osgi.service.component.AnyService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.service.http.whiteboard.annotations.RequireHttpWhiteboard;
import org.osgi.service.http.whiteboard.propertytypes.HttpWhiteboardFilterAsyncSupported;
import org.osgi.service.http.whiteboard.propertytypes.HttpWhiteboardFilterPattern;
import org.osgi.service.http.whiteboard.propertytypes.HttpWhiteboardServletPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpoint;

@Component(service = { Filter.class, Servlet.class }, scope = ServiceScope.PROTOTYPE)
@RequireHttpWhiteboard
@HttpWhiteboardServletPattern("/ws/*")
@HttpWhiteboardFilterPattern("/ws/*")
@HttpWhiteboardFilterAsyncSupported
public class WebSocketRegistrar extends HttpServlet implements Filter {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(WebSocketRegistrar.class);

    private WSServerContainer serverContainer;

    private List<Class<?>> webSocketEndpoints = new ArrayList<>();

    @Activate
    void activate() {
        // TODO Read configuration
    }

    @Deactivate
    void stop() {
        // TODO Close all web sockets
        webSocketEndpoints.clear();

        if (serverContainer != null) {
            serverContainer.stop();
            serverContainer = null;
        }
    }

    /**
     * New websocket service registered
     */
    @Reference(service = AnyService.class, target = "(websocket.server=*)", cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY)
    void addServerEndpoint(final Object endpoint, final Map<String, Object> properties) {
        if (endpoint.getClass().getAnnotation(ServerEndpoint.class) != null) {
            final Class<?> clazz = endpoint.getClass();
            if (!webSocketEndpoints.contains(clazz)) {
                logger.debug("Adding server endpoint - {}", endpoint.getClass().getName());
                webSocketEndpoints.add(clazz);
            }
        } else {
            logger.warn("Found a websocket service that isn't annotated. Ignoring it.");
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Filter.super.init(filterConfig);

        final ServletContext context = filterConfig.getServletContext();
        serverContainer = new WSServerContainer(context.getContextPath());

        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(TyrusServerEndpointConfigurator.class.getClassLoader());
            for (final Class<?> clazz : webSocketEndpoints) {
                try {
                    serverContainer.register(clazz);
                } catch (DeploymentException e) {
                    logger.error("Error register WebSocket server endpoint class {}: {}", clazz.getName(),
                            e.getMessage(), e);
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }

        try {
            serverContainer.start(context.getContextPath(), 0);
        } catch (Exception e) {
            e.printStackTrace();
            throw new ServletException(e);
        }

        context.setAttribute(ServerContainer.class.getName(), serverContainer);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        final HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        final HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        try {
            boolean success = serverContainer.getServletUpgrade().upgrade(httpServletRequest, httpServletResponse);

            if (!success && chain != null) {
                chain.doFilter(request, response);
            }
        } catch (Throwable e) {
            httpServletResponse.setStatus(500);
            logger.error("Error upgrading WebSocket", e);
            if (chain != null) {
                chain.doFilter(request, response);
            }
        }
    }
}
