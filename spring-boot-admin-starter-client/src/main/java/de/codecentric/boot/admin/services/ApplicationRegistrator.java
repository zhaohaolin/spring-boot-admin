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
package de.codecentric.boot.admin.services;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import de.codecentric.boot.admin.config.AdminClientProperties;
import de.codecentric.boot.admin.config.AdminProperties;
import de.codecentric.boot.admin.model.Application;

/**
 * Registers the client application at spring-boot-admin-server
 */
public class ApplicationRegistrator {
	
	private static final Logger				LOG				= LoggerFactory
																	.getLogger(ApplicationRegistrator.class);
	
	private static HttpHeaders				HTTP_HEADERS	= createHttpHeaders();
	
	private final AtomicReference<String>	registeredId	= new AtomicReference<String>();
	private AdminClientProperties			clientProps;
	private AdminProperties					props;
	private final RestTemplate				template;
	private ScheduledExecutorService		scheduled;
	
	@Deprecated
	public ApplicationRegistrator(final RestTemplate template,
			final AdminProperties props, final AdminClientProperties clientProps) {
		this.clientProps = clientProps;
		this.props = props;
		this.template = template;
	}
	
	public ApplicationRegistrator(final RestTemplate template,
			final AdminProperties props,
			final AdminClientProperties clientProps,
			final ScheduledExecutorService scheduled) {
		this.clientProps = clientProps;
		this.props = props;
		this.template = template;
		this.scheduled = scheduled;
	}
	
	public void startScheduled() {
		if (null != scheduled) {
			scheduled.scheduleWithFixedDelay(new Runnable() {
				
				@Override
				public void run() {
					try {
						if (clientProps.isServerInitialized()) {
							register();
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}, 5000, props.getPeriod(), TimeUnit.MILLISECONDS);
		}
	}
	
	private static HttpHeaders createHttpHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		return HttpHeaders.readOnlyHttpHeaders(headers);
	}
	
	/**
	 * Registers the client application at spring-boot-admin-server.
	 *
	 * @return true if successful
	 */
	public boolean register() {
		Application self = null;
		String adminUrl = props.getUrl() + '/' + props.getContextPath();
		try {
			self = createApplication();
			
			@SuppressWarnings("rawtypes")
			ResponseEntity<Map> response = template.postForEntity(adminUrl,
					new HttpEntity<Application>(self, HTTP_HEADERS), Map.class);
			
			LOG.info("Application registered self: {}", response.getBody());
			
			if (response.getStatusCode().equals(HttpStatus.CREATED)) {
				if (registeredId.get() == null) {
					if (registeredId.compareAndSet(null, response.getBody()
							.get("id").toString())) {
						
						return true;
					}
				}
				
				LOG.debug("Application refreshed self: {}", response.getBody());
				return true;
			}
			
			// else
			LOG.warn("Application failed to registered self: {}. Response: {}",
					self, response.toString());
		} catch (Exception ex) {
			LOG.warn(
					"Failed to register application: {} at spring-boot-admin ({}): {}",
					self, adminUrl, ex.getMessage());
		}
		
		return false;
	}
	
	public void deregister() {
		String id = registeredId.get();
		if (id != null) {
			String adminUrl = props.getUrl() + '/' + props.getContextPath()
					+ "/" + id;
			
			try {
				template.delete(adminUrl);
				registeredId.set(null);
			} catch (Exception ex) {
				LOG.warn(
						"Failed to deregister application (id={}) at spring-boot-admin ({}): {}",
						id, adminUrl, ex.getMessage());
			}
		}
	}
	
	protected Application createApplication() {
		return Application.create(clientProps.getName())
				.withHealthUrl(clientProps.getHealthUrl())
				.withManagementUrl(clientProps.getManagementUrl())
				.withServiceUrl(clientProps.getServiceUrl()).build();
	}
}
