/*
 * Copyright 2012 Jeanfrancois Arcand
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.cpr;


import org.apache.catalina.CometEvent;
import org.apache.catalina.CometProcessor;
import org.atmosphere.container.JBossWebCometSupport;
import org.atmosphere.container.Tomcat7CometSupport;
import org.atmosphere.container.TomcatCometSupport;
import org.jboss.servlet.http.HttpEvent;
import org.jboss.servlet.http.HttpEventServlet;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Native Comet Support for Tomcat 6/7 and JBoss CometProcessor API.
 */
public class AtmosphereNativeServlet extends AtmosphereServlet implements CometProcessor, HttpEventServlet, org.apache.catalina.comet.CometProcessor {

    /**
     * Hack to support Tomcat AIO like other WebServer. This method is invoked
     * by Tomcat when it detect a {@link Servlet} implements the interface
     * {@link CometProcessor} without invoking {@link Servlet#service}
     *
     * @param cometEvent the {@link CometEvent}
     * @throws java.io.IOException
     * @throws javax.servlet.ServletException
     */
    public void event(CometEvent cometEvent) throws IOException, ServletException {
        HttpServletRequest req = cometEvent.getHttpServletRequest();
        HttpServletResponse res = cometEvent.getHttpServletResponse();
        req.setAttribute(TomcatCometSupport.COMET_EVENT, cometEvent);

        if (!framework.getAsyncSupport().supportWebSocket()) {
            if (!framework.isCometSupportSpecified && !framework.isCometSupportConfigured.getAndSet(true)) {
                synchronized (framework.getAsyncSupport()) {
                    if (!framework.getAsyncSupport().getClass().equals(TomcatCometSupport.class)) {
                        AsyncSupport current = framework.getAsyncSupport();
                        logger.warn("TomcatCometSupport is enabled, switching to it");
                        framework.setAsyncSupport(new TomcatCometSupport(framework.getAtmosphereConfig()));
                        if(current instanceof AsynchronousProcessor) {
                            ((AsynchronousProcessor)current).shutdown();
                        }
                        framework.getAsyncSupport().init(framework.getAtmosphereConfig().getServletConfig());
                    }
                }
            }
        }

        framework.doCometSupport(AtmosphereRequest.wrap(req), AtmosphereResponse.wrap(res));

        String transport = cometEvent.getHttpServletRequest().getParameter(HeaderConfig.X_ATMOSPHERE_TRANSPORT);
        if (transport != null && transport.equalsIgnoreCase(HeaderConfig.WEBSOCKET_TRANSPORT)) {
            cometEvent.close();
        }
    }

    /**
     * Hack to support Tomcat 7 AIO
     */
    public void event(org.apache.catalina.comet.CometEvent cometEvent) throws IOException, ServletException {
        HttpServletRequest req = cometEvent.getHttpServletRequest();
        HttpServletResponse res = cometEvent.getHttpServletResponse();
        req.setAttribute(Tomcat7CometSupport.COMET_EVENT, cometEvent);

        if (!framework.getAsyncSupport().supportWebSocket()) {
            if (!framework.isCometSupportSpecified && !framework.isCometSupportConfigured.getAndSet(true)) {
                synchronized (framework.getAsyncSupport()) {
                    if (!framework.getAsyncSupport().getClass().equals(Tomcat7CometSupport.class)) {
                        AsyncSupport current = framework.getAsyncSupport();
                        logger.warn("TomcatCometSupport7 is enabled, switching to it");
                        framework.setAsyncSupport(new Tomcat7CometSupport(framework.getAtmosphereConfig()));
                        if(current instanceof AsynchronousProcessor) {
                            ((AsynchronousProcessor)current).shutdown();
                        }
                        framework.getAsyncSupport().init(framework.getAtmosphereConfig().getServletConfig());
                    }
                }
            }
        }

        framework.doCometSupport(AtmosphereRequest.wrap(req), AtmosphereResponse.wrap(res));

        String transport = cometEvent.getHttpServletRequest().getParameter(HeaderConfig.X_ATMOSPHERE_TRANSPORT);
        if (transport != null && transport.equalsIgnoreCase(HeaderConfig.WEBSOCKET_TRANSPORT)) {
            cometEvent.close();
        }
    }

    /**
     * Hack to support JBossWeb AIO like other WebServer. This method is invoked
     * by Tomcat when it detect a {@link Servlet} implements the interface
     * {@link HttpEventServlet} without invoking {@link Servlet#service}
     *
     * @param httpEvent the {@link CometEvent}
     * @throws java.io.IOException
     * @throws javax.servlet.ServletException
     */
    public void event(HttpEvent httpEvent) throws IOException, ServletException {
        HttpServletRequest req = httpEvent.getHttpServletRequest();
        HttpServletResponse res = httpEvent.getHttpServletResponse();
        req.setAttribute(JBossWebCometSupport.HTTP_EVENT, httpEvent);

        if (!framework.isCometSupportSpecified && !framework.isCometSupportConfigured.getAndSet(true)) {
            synchronized (framework.getAsyncSupport()) {
                if (!framework.getAsyncSupport().getClass().equals(JBossWebCometSupport.class)) {
                    AsyncSupport current = framework.getAsyncSupport();
                    logger.warn("JBossWebCometSupport is enabled, switching to it");
                    framework.setAsyncSupport(new JBossWebCometSupport(framework.getAtmosphereConfig()));
                    if(current instanceof AsynchronousProcessor) {
                        ((AsynchronousProcessor)current).shutdown();
                    }
                    framework.getAsyncSupport().init(framework.getAtmosphereConfig().getServletConfig());
                }
            }
        }
        framework.doCometSupport(AtmosphereRequest.wrap(req), AtmosphereResponse.wrap(res));
    }

}