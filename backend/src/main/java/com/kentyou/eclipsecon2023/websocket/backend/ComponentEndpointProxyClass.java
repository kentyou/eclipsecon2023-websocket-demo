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
import java.util.Collection;
import java.util.Map;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

public class ComponentEndpointProxyClass extends Endpoint {

	/**
	 * Provider bundle context
	 */
	private BundleContext context;

	/**
	 * Real handler service reference
	 */
	private ServiceReference<Endpoint> svcRef;

	/**
	 * Real handler
	 */
	private Endpoint handler;

	@Override
	public void onOpen(Session session, EndpointConfig config) {
		// Extract configuration
		final Map<String, Object> userProperties = config.getUserProperties();
		context = (BundleContext) userProperties.get("osgi.ws.bundle.context");
		if (context == null) {
			throw new RuntimeException("No bundle context configured");
		}

		// Find the service underneath
		final Long svcId = (Long) userProperties.get("osgi.ws.svc.id");
		if (svcId == null) {
			throw new RuntimeException("No handler service ID configured");
		}

		final Collection<ServiceReference<Endpoint>> references;
		try {
			references = context.getServiceReferences(Endpoint.class,
					String.format("(%s=%d)", Constants.SERVICE_ID, svcId));
		} catch (InvalidSyntaxException e) {
			throw new RuntimeException("Error looking for configured service", e);
		}

		if (references == null || references.size() != 1) {
			throw new RuntimeException("Couldn't find configured service");
		}

		svcRef = references.iterator().next();
		handler = context.getServiceObjects(svcRef).getService();

		// Handle onOpen on handler side
		handler.onOpen(session, config);
	}

	@Override
	public void onClose(Session session, CloseReason closeReason) {
		try {
			if (handler != null) {
				handler.onClose(session, closeReason);
			}
		} finally {
			handler = null;
			if (svcRef != null) {
				context.ungetService(svcRef);
				svcRef = null;
			}
		}
	}

	@Override
	public void onError(Session session, Throwable thr) {
		thr.printStackTrace();
		if (handler != null) {
			handler.onError(session, thr);
		} else {
			// No handler: close the session
			try {
				session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, thr.getMessage()));
			} catch (IOException e) {
				System.err.println("Error closing session");
				e.printStackTrace();
			}
		}
	}
}
