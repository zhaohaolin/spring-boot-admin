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

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.ManagementServerProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.boot.context.embedded.EmbeddedWebApplicationContext;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "spring.boot.admin.client", ignoreUnknownFields = false)
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class AdminClientProperties implements
		ApplicationListener<ApplicationEvent> {
	/**
	 * Client-management-URL to register with. Inferred at runtime, can be
	 * overriden in case the reachable URL is different (e.g. Docker).
	 */
	private String						managementUrl;
	
	/**
	 * Client-service-URL register with. Inferred at runtime, can be overriden
	 * in case the reachable URL is different (e.g. Docker).
	 */
	private String						serviceUrl;
	
	/**
	 * Client-health-URL to register with. Inferred at runtime, can be overriden
	 * in case the reachable URL is different (e.g. Docker). Must be unique in
	 * registry.
	 */
	private String						healthUrl;
	
	/**
	 * Name to register with. Defaults to ${spring.application.name}
	 */
	@Value("${spring.application.name:spring-boot-application}")
	private String						name;
	
	@Value("${endpoints.health.id:health}")
	private String						healthEndpointId;
	
	/**
	 * Should the registered urls be built with server.address or with hostname.
	 */
	private boolean						preferIp			= false;
	
	@Autowired
	private ManagementServerProperties	management;
	
	@Autowired
	private ServerProperties			server;
	
	private int							serverPort			= -1;
	
	private int							managementPort		= -1;
	
	private boolean						serverInitialized	= false;
	
	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof EmbeddedServletContainerInitializedEvent) {
			EmbeddedServletContainerInitializedEvent initEvent = (EmbeddedServletContainerInitializedEvent) event;
			serverInitialized = true;
			if ("management".equals(initEvent.getApplicationContext()
					.getNamespace())) {
				managementPort = initEvent.getEmbeddedServletContainer()
						.getPort();
			} else {
				serverPort = initEvent.getEmbeddedServletContainer().getPort();
			}
		} else if (startedDeployedWar(event)) {
			serverInitialized = true;
			if (!StringUtils.hasText(serviceUrl)) {
				throw new RuntimeException(
						"spring.boot.admin.client.serviceUrl must be set for deployed war files!");
			}
		}
	}
	
	private boolean startedDeployedWar(ApplicationEvent event) {
		if (event instanceof ContextRefreshedEvent) {
			ApplicationContextEvent contextEvent = (ApplicationContextEvent) event;
			if (contextEvent.getApplicationContext() instanceof EmbeddedWebApplicationContext) {
				EmbeddedWebApplicationContext context = (EmbeddedWebApplicationContext) contextEvent
						.getApplicationContext();
				return context.getEmbeddedServletContainer() == null;
			}
		}
		return false;
	}
	
	public String getManagementUrl() {
		if (managementUrl != null) {
			return managementUrl;
		}
		if (managementPort == -1) {
			return append(getServiceUrl(), management.getContextPath());
		}
		
		if (preferIp) {
			Assert.notNull(management.getAddress(),
					"management.address must be set when using preferIp");
			return append(
					createLocalUri(management.getAddress().getHostAddress(),
							managementPort), management.getContextPath());
			
		}
		return append(createLocalUri(getHostname(), managementPort),
				management.getContextPath());
	}
	
	public void setManagementUrl(String managementUrl) {
		this.managementUrl = managementUrl;
	}
	
	public String getHealthUrl() {
		if (healthUrl != null) {
			return healthUrl;
		}
		return append(getManagementUrl(), healthEndpointId);
	}
	
	public void setHealthUrl(String healthUrl) {
		this.healthUrl = healthUrl;
	}
	
	public String getServiceUrl() {
		if (serviceUrl != null) {
			return serviceUrl;
		}
		
		if (serverPort == -1) {
			throw new IllegalStateException(
					"EmbeddedServletContainer has not been initialized yet!");
		}
		
		if (preferIp) {
			Assert.notNull(server.getAddress(),
					"server.address must be set when using preferIp");
			return append(
					createLocalUri(server.getAddress().getHostAddress(),
							serverPort), server.getContextPath());
			
		}
		return append(createLocalUri(getHostname(), serverPort),
				server.getContextPath());
	}
	
	public void setServiceUrl(String serviceUrl) {
		this.serviceUrl = serviceUrl;
	}
	
	public boolean isServerInitialized() {
		return serverInitialized;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public void setPreferIp(boolean preferIp) {
		this.preferIp = preferIp;
	}
	
	public boolean isPreferIp() {
		return preferIp;
	}
	
	private String createLocalUri(String host, int port) {
		String scheme = server.getSsl() != null && server.getSsl().isEnabled() ? "https"
				: "http";
		return scheme + "://" + host + ":" + port;
	}
	
	private String append(String uri, String path) {
		String baseUri = uri.replaceFirst("/+$", "");
		if (StringUtils.isEmpty(path)) {
			return baseUri;
		}
		
		String normPath = path.replaceFirst("^/+", "").replaceFirst("/+$", "");
		return baseUri + "/" + normPath;
	}
	
	private String getHostname() {
		try {
			return InetAddress.getLocalHost().getCanonicalHostName();
		} catch (UnknownHostException ex) {
			throw new IllegalArgumentException(ex.getMessage(), ex);
		}
	}
	
}
