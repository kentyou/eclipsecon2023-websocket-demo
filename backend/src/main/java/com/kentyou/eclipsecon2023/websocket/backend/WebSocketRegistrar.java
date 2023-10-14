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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.glassfish.tyrus.core.TyrusServerEndpointConfigurator;
import org.osgi.framework.Constants;
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
import jakarta.websocket.Endpoint;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;

@Component(service = { Filter.class, Servlet.class }, scope = ServiceScope.PROTOTYPE)
@RequireHttpWhiteboard
@HttpWhiteboardServletPattern("/ws/*")
@HttpWhiteboardFilterPattern("/ws/*")
@HttpWhiteboardFilterAsyncSupported
public class WebSocketRegistrar extends HttpServlet implements Filter {

	private static final long serialVersionUID = 1L;

	private static final Logger logger = LoggerFactory.getLogger(WebSocketRegistrar.class);

	private WSServerContainer serverContainer;

	/**
	 * Annotated endpoints
	 */
	private List<Class<?>> webSocketEndpoints = new ArrayList<>();

	/**
	 * Endpoints extending {@link Endpoint}
	 */
	private Map<Class<?>, ServerEndpointConfig> webSocketConfigs = new HashMap<>();

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
	@Reference(service = AnyService.class, target = "(&(test=true)(websocket.server=*))", cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY)
	void addServerEndpoint(final Object endpoint, final Map<String, Object> properties) {
		if (endpoint.getClass().getAnnotation(ServerEndpoint.class) != null) {
			// Got an annotated class
			final Class<?> clazz = endpoint.getClass();
			if (!webSocketEndpoints.contains(clazz)) {
				webSocketEndpoints.add(clazz);
			}
		} else if (Endpoint.class.isAssignableFrom(endpoint.getClass()) && properties.get("websocket.path") != null) {
			// Got an endpoint class
			final Class<?> clazz = ComponentEndpointProxyClass.class;
			if (!webSocketConfigs.containsKey(clazz)) {
				webSocketConfigs.put(clazz, makeProxyConfig(clazz, properties.get("websocket.path").toString(),
						(Long) properties.get(Constants.SERVICE_ID)));
			}
		} else {
			logger.warn("Found a websocket service that isn't annotated. Ignoring it.");
		}
	}

	void removeServerEndpoint(final Object endpoint) {
		if (endpoint.getClass().getAnnotation(ServerEndpoint.class) != null) {
			final Class<?> clazz = endpoint.getClass();
			if (webSocketEndpoints.contains(clazz)) {
				webSocketEndpoints.remove(clazz);
			} else if (webSocketConfigs.containsKey(clazz)) {
				webSocketConfigs.remove(clazz);
			}
		}
	}

	ServerEndpointConfig makeConfig(final Class<?> clazz, final String path) {
		return ServerEndpointConfig.Builder.create(clazz, path).configurator(new TyrusServerEndpointConfigurator())
				.build();
	}

	ServerEndpointConfig makeProxyConfig(final Class<?> clazz, final String path, final Long svcId) {
		final ServerEndpointConfig config = makeConfig(clazz, path);
		final Map<String, Object> userProperties = config.getUserProperties();
		userProperties.put("osgi.ws.bundle.context", Activator.getContext());
		userProperties.put("osgi.ws.svc.id", svcId);
		return config;
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		Filter.super.init(filterConfig);

		final ServletContext context = filterConfig.getServletContext();
		serverContainer = new WSServerContainer(context.getContextPath());

		System.out.println("*** Init with annotated endpoint classes: "
				+ webSocketEndpoints.stream().map(c -> c.getSimpleName()).collect(Collectors.joining(", "))
				+ "... and with endpoint classes: "
				+ webSocketConfigs.keySet().stream().map(c -> c.getSimpleName()).collect(Collectors.joining(", ")));
		registerClasses();

		try {
			serverContainer.start(context.getContextPath(), 0);
		} catch (Exception e) {
			e.printStackTrace();
			throw new ServletException(e);
		}

		context.setAttribute(ServerContainer.class.getName(), serverContainer);
	}

	private <T> T runWithClassLoader(Callable<T> r) throws Exception {
		final ClassLoader old = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(TyrusServerEndpointConfigurator.class.getClassLoader());
			return r.call();
		} finally {
			Thread.currentThread().setContextClassLoader(old);
		}
	}

	private void registerClasses() {
		try {
			runWithClassLoader(() -> {
				for (final Class<?> clazz : webSocketEndpoints) {
					try {
						serverContainer.register(clazz);
					} catch (DeploymentException e) {
						logger.error("Error registering WebSocket server annotated class {}: {}", clazz.getName(),
								e.getMessage(), e);
					}
				}

				for (final ServerEndpointConfig config : webSocketConfigs.values()) {
					try {
						serverContainer.register(config);
					} catch (DeploymentException e) {
						logger.error("Error registering WebSocket server endpoint class {}: {}",
								config.getClass().getName(), e.getMessage(), e);
					}
				}
				return null;
			});
		} catch (Exception e) {
			logger.error("Error registering classes", e);
			e.printStackTrace();
		}
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		final HttpServletRequest httpServletRequest = (HttpServletRequest) request;
		final HttpServletResponse httpServletResponse = (HttpServletResponse) response;

		try {
			System.out.println("DO FILTER on " + httpServletRequest.getServletPath() + " / ctxt="
					+ httpServletRequest.getContextPath() + " / " + httpServletRequest.getPathInfo());
			boolean success = serverContainer.getServletUpgrade().upgrade(httpServletRequest, httpServletResponse);
			System.out.println(" => " + success);

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
