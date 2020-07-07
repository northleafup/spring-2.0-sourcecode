/*
 * Copyright 2002-2006 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.context.request;

import org.springframework.ui.ModelMap;

/**
 * Interface for general web request interception. Allows for being applied
 * to Servlet request as well as Portlet request environments, through
 * building on the WebRequest abstraction.
 *
 * <p>This interface assumes MVC-style request processing: A handler gets executed,
 * exposes a set of model objects, then a view gets rendered based on that model.
 * Alternatively, a handler may also process the request completely, with no
 * view to be rendered.
 *
 * <p>This interface is deliberatly minimalistic to keep the dependencies of
 * generic request interceptors as minimal as feasible.
 *
 * <p><b>NOTE:</b> While this interceptor is applied to the entire request processing
 * in a Servlet environment, it is only applied to the <b>render</b> phase in a
 * Portlet environment, which is dealing with preparing and rendering a Portlet view.
 * The Portlet action phase <i>cannot</i> be intercepted with this mechanism;
 * use the Portlet-specific HandlerInterceptor mechanism for such needs.
 *
 * @author Juergen Hoeller
 * @since 2.0
 * @see WebRequest
 * @see ServletWebRequest
 * @see org.springframework.web.servlet.DispatcherServlet
 * @see org.springframework.web.servlet.handler.AbstractHandlerMapping#setInterceptors
 * @see org.springframework.web.servlet.HandlerInterceptor
 * @see org.springframework.web.portlet.context.PortletWebRequest
 * @see org.springframework.web.portlet.DispatcherPortlet
 * @see org.springframework.web.portlet.handler.AbstractHandlerMapping#setInterceptors
 * @see org.springframework.web.portlet.HandlerInterceptor
 */
public interface WebRequestInterceptor {

	/**
	 * Intercept the execution of a request handler <i>before</i> its invocation.
	 * <p>Allows for preparing context resources (such as a Hibernate Session)
	 * and expose them as request attributes or as thread-local objects.
	 * @param request the current web request
	 * @throws Exception in case of errors
	 */
	void preHandle(WebRequest request) throws Exception;

	/**
	 * Intercept the execution of a request handler <i>after</i> its successful
	 * invocation, right before view rendering (if any).
	 * <p>Allows for modifying context resources after successful handler
	 * execution (for example, flushing a Hibernate Session).
	 * @param request the current web request
	 * @param model the map of model objects that will be exposed to the view
	 * (may be <code>null</code>). Can be used to analyze the exposed model
	 * and/or to add further model attributes, if desired.
	 * @throws Exception in case of errors
	 */
	void postHandle(WebRequest request, ModelMap model) throws Exception;

	/**
	 * Callback after completion of request processing, that is, after rendering
	 * the view. Will be called on any outcome of handler execution, thus allows
	 * for proper resource cleanup.
	 * <p>Note: Will only be called if this interceptor's <code>preHandle</code>
	 * method has successfully completed!
	 * @param request the current web request
	 * @param ex exception thrown on handler execution, if any
	 * @throws Exception in case of errors
	 */
	void afterCompletion(WebRequest request, Exception ex) throws Exception;

}
