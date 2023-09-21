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
package com.kentyou.eclipsecon2023.websocket.backend.integration;

import org.junit.jupiter.api.Test;
import org.osgi.service.http.runtime.HttpServiceRuntime;
import org.osgi.test.common.annotation.InjectService;

public class WebSocketIntegrationTest {

    @InjectService
    HttpServiceRuntime http;

    @Test
    void test() throws Exception {
        System.out.println("SLEEPING...");
        Thread.sleep(2000);
        System.out.println("Done");
    }
}
