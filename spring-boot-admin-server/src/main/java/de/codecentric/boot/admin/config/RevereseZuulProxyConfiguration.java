/*
 * Copyright 2014 the original author or authors.
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
package de.codecentric.boot.admin.config;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.boot.actuate.trace.TraceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.netflix.zuul.RoutesEndpoint;
import org.springframework.cloud.netflix.zuul.ZuulFilterInitializer;
import org.springframework.cloud.netflix.zuul.filters.ProxyRequestHelper;
import org.springframework.cloud.netflix.zuul.filters.ProxyRouteLocator;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties;
import org.springframework.cloud.netflix.zuul.filters.post.SendErrorFilter;
import org.springframework.cloud.netflix.zuul.filters.post.SendResponseFilter;
import org.springframework.cloud.netflix.zuul.filters.pre.DebugFilter;
import org.springframework.cloud.netflix.zuul.filters.pre.FormBodyWrapperFilter;
import org.springframework.cloud.netflix.zuul.filters.pre.PreDecorationFilter;
import org.springframework.cloud.netflix.zuul.filters.pre.Servlet30WrapperFilter;
import org.springframework.cloud.netflix.zuul.filters.route.SimpleHostRoutingFilter;
import org.springframework.cloud.netflix.zuul.web.ZuulController;
import org.springframework.cloud.netflix.zuul.web.ZuulHandlerMapping;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.zuul.ZuulFilter;

import de.codecentric.boot.admin.controller.RegistryController;
import de.codecentric.boot.admin.registry.ApplicationRegistry;
import de.codecentric.boot.admin.zuul.ApplicationRouteLocator;
import de.codecentric.boot.admin.zuul.ApplicationRouteRefreshListener;

@Configuration
@EnableConfigurationProperties(ZuulProperties.class)
public class RevereseZuulProxyConfiguration {
	
	@Autowired(required = false)
	private TraceRepository		traces;
	
	@Autowired
	private ZuulProperties		zuulProperties;
	
	@Autowired
	private ServerProperties	server;
	
	@Autowired
	private ApplicationRegistry	registry;
	
	@Bean
	public ApplicationRouteLocator routeLocator() {
		return new ApplicationRouteLocator(this.server.getServletPrefix(),
				registry, this.zuulProperties, RegistryController.PATH);
	}
	
	@Bean
	public PreDecorationFilter preDecorationFilter() {
		return new PreDecorationFilter(routeLocator(),
				this.zuulProperties.isAddProxyHeaders());
	}
	
	@Bean
	public SimpleHostRoutingFilter simpleHostRoutingFilter() {
		ProxyRequestHelper helper = new ProxyRequestHelper();
		if (this.traces != null) {
			helper.setTraces(this.traces);
		}
		return new SimpleHostRoutingFilter(helper);
	}
	
	// @Bean
	// public ZuulController zuulController() {
	// return new ZuulController();
	// }
	
	@Bean
	public ZuulHandlerMapping zuulHandlerMapping(RouteLocator routes,
			ZuulController zuulController) {
		return new ZuulHandlerMapping(routes, zuulController);
	}
	
	// pre filters
	
	@Bean
	public FormBodyWrapperFilter formBodyWrapperFilter() {
		return new FormBodyWrapperFilter();
	}
	
	@Bean
	public DebugFilter debugFilter() {
		return new DebugFilter();
	}
	
	@Bean
	public Servlet30WrapperFilter servlet30WrapperFilter() {
		return new Servlet30WrapperFilter();
	}
	
	// post filters
	
	@Bean
	public SendResponseFilter sendResponseFilter() {
		return new SendResponseFilter();
	}
	
	@Bean
	public SendErrorFilter sendErrorFilter() {
		return new SendErrorFilter();
	}
	
	@Configuration
	protected static class ZuulFilterConfiguration {
		
		@Autowired
		private Map<String, ZuulFilter>	filters;
		
		@Bean
		public ZuulFilterInitializer zuulFilterInitializer() {
			return new ZuulFilterInitializer(this.filters);
		}
		
	}
	
	@Bean
	public ApplicationRouteRefreshListener applicationRouteRefreshListener(
			ZuulController zuulController) {
		return new ApplicationRouteRefreshListener(routeLocator(),
				zuulHandlerMapping(routeLocator(), zuulController));
	}
	
	@Configuration
	@ConditionalOnClass(Endpoint.class)
	protected static class RoutesEndpointConfiguration {
		
		@Autowired
		private ProxyRouteLocator	routeLocator;
		
		@Bean
		public RoutesEndpoint zuulEndpoint() {
			return new RoutesEndpoint(this.routeLocator);
		}
		
	}
	
}
