/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 *
 */
package org.atmosphere.container;

import org.atmosphere.cpr.AsynchronousProcessor;
import org.atmosphere.cpr.AtmosphereResourceImpl;
import org.atmosphere.cpr.AtmosphereServlet;
import org.atmosphere.cpr.AtmosphereServlet.Action;
import org.atmosphere.cpr.AtmosphereServlet.AtmosphereConfig;
import org.atmosphere.cpr.CometSupport;
import org.atmosphere.websocket.WebSocketSupport;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Level;

/**
 * This class gets used when the {@link AtmosphereServlet} detect the container
 * detect Servlet 3.0 Asynch API.
 *
 * @author Jeanfrancois Arcand
 */
public class Servlet30Support extends AsynchronousProcessor implements CometSupport<AtmosphereResourceImpl> {

    public Servlet30Support(AtmosphereConfig config) {
        super(config);
    }

    /**
     * Return "javax.servlet".
     *
     * @return "javax.servlet"
     */
    @Override
    public String getContainerName() {
        return super.getContainerName() + " using javax.servlet/3.0";
    }

    /**
     * {@inheritDoc}
     */
    public Action service(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException {

        Action action = suspended(req, res);
        if (req.getAttribute(WebSocketSupport.WEBSOCKET_SUSPEND) == null) {
            if (action.type == Action.TYPE.SUSPEND) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Suspending" + res);
                }
                suspend(action, req, res);
            } else if (action.type == Action.TYPE.RESUME) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Resuming" + res);
                }

                if (supportSession()) {
                    AsyncContext asyncContext = (AsyncContext)
                            req.getSession().getAttribute("org.atmosphere.container.asyncContext");

                    if (asyncContext != null) {
                        asyncContext.complete();
                    }
                }

                Action nextAction = resumed(req, res);
                if (nextAction.type == Action.TYPE.SUSPEND) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("Suspending after Resuming" + res);
                        suspend(action, req, res);
                    }
                }
            }
        } else {
            if (action.type == Action.TYPE.SUSPEND) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Suspending " + res);
                }
            } else if (action.type == Action.TYPE.RESUME) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Resume " + res);
                }
                req.setAttribute(WebSocketSupport.WEBSOCKET_RESUME, "true");
            }
        }
        return action;
    }

    /**
     * Suspend the connection by invoking {@link HttpServletRequest#startAsync()}
     *
     * @param action The {@link AtmosphereServlet.Action}
     * @param req    the {@link HttpServletRequest}
     * @param res    the {@link HttpServletResponse}
     * @throws java.io.IOException
     * @throws javax.servlet.ServletException
     */
    private void suspend(Action action, HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException {

        if (!req.isAsyncStarted()) {

            AsyncContext asyncContext = req.startAsync();
            asyncContext.addListener(new CometListener());
            // Do nothing except setting the times out
            if (action.timeout != -1) {
                asyncContext.setTimeout(action.timeout);
            } else {
                // Jetty 8 does something really weird if you set it to
                // Long.MAX_VALUE, which is to resume automatically.
                asyncContext.setTimeout(Integer.MAX_VALUE);
            }
            req.setAttribute("org.atmosphere.container.asyncContext"
                    , asyncContext);

            if (supportSession()) {
                // Store as well in the session in case the resume operation
                // happens outside the AtmosphereHandler.onStateChange scope.
                req.getSession().setAttribute("org.atmosphere.container.asyncContext"
                        , asyncContext);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void action(AtmosphereResourceImpl actionEvent) {
        if (actionEvent.action().type == Action.TYPE.RESUME) {
            AsyncContext asyncContext = (AsyncContext)
                    actionEvent.getRequest()
                            .getAttribute("org.atmosphere.container.asyncContext");

            // Try to find using the Session
            if (asyncContext == null && supportSession()) {
                asyncContext = (AsyncContext)
                        actionEvent.getRequest().getSession()
                                .getAttribute("org.atmosphere.container.asyncContext");
            }

            if (asyncContext != null && (config.getInitParameter(AtmosphereServlet.RESUME_AND_KEEPALIVE) == null
                    || config.getInitParameter(AtmosphereServlet.RESUME_AND_KEEPALIVE).equalsIgnoreCase("false"))) {
                asyncContext.complete();
            }
        }
    }

    /**
     * Servlet 3.0 async listener support.
     */
    private class CometListener implements AsyncListener {

        public void onComplete(AsyncEvent event) throws IOException {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Resumed (completed)" + event.getAsyncContext().getRequest());
            }
        }

        public void onTimeout(AsyncEvent event) throws IOException {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("onTimeout" + event.getAsyncContext().getRequest());
            }

            try {
                timedout((HttpServletRequest) event.getAsyncContext().getRequest(),
                        (HttpServletResponse) event.getAsyncContext().getResponse());
            } catch (ServletException ex) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "onTimeout" + event.getAsyncContext().getResponse(), ex);
                }
            }
        }

        public void onError(AsyncEvent event) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "onError" + event.getAsyncContext().getResponse());
            }
            try {
                cancelled((HttpServletRequest) event.getAsyncContext().getRequest(),
                        (HttpServletResponse) event.getAsyncContext().getResponse());
            } catch (Throwable ex) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "cancelled" + event.getAsyncContext().getResponse(), ex);
                }
            }
        }

        public void onStartAsync(AsyncEvent event) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "onStartAsync" + event.getAsyncContext().getResponse());
            }
        }
    }

}
