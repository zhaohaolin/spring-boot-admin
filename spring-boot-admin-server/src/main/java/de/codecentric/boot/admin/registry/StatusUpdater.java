/*
 * Copyright 2013-2014 the original author or authors.
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
package de.codecentric.boot.admin.registry;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import de.codecentric.boot.admin.event.ClientApplicationStatusChangedEvent;
import de.codecentric.boot.admin.model.Application;
import de.codecentric.boot.admin.model.StatusInfo;
import de.codecentric.boot.admin.registry.store.ApplicationStore;

/**
 * The StatusUpdater is responsible for updatig the status of all or a single
 * application querying the healthUrl.
 *
 * @author Johannes Stelzer
 *
 */
public class StatusUpdater implements ApplicationEventPublisherAware {
	private static final Logger						LOGGER			= LoggerFactory
																			.getLogger(StatusUpdater.class);
	
	private final static ScheduledExecutorService	SCHEDULED		= Executors
																			.newSingleThreadScheduledExecutor();
	
	private final ApplicationStore					store;
	private final RestTemplate						restTemplate;
	private ApplicationEventPublisher				publisher;
	private final long								monitorPeriod;
	
	/**
	 * Lifetime of status in ms. The status won't be updated as long the last
	 * status isn't expired.
	 */
	private long									statusLifetime	= 30_000L;
	
	public StatusUpdater(RestTemplate restTemplate, ApplicationStore store,
			final long monitorPeriod) {
		this.restTemplate = restTemplate;
		this.store = store;
		this.monitorPeriod = monitorPeriod;
	}
	
	public void updateStatusForAllApplications() {
		
		SCHEDULED.scheduleWithFixedDelay(new Runnable() {
			
			@Override
			public void run() {
				try {
					long now = System.currentTimeMillis();
					for (Application application : store.findAll()) {
						if (now - statusLifetime > application.getStatusInfo()
								.getTimestamp()) {
							updateStatus(application);
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}, monitorPeriod, monitorPeriod, TimeUnit.MILLISECONDS);
		
	}
	
	public void updateStatus(Application application) {
		StatusInfo oldStatus = application.getStatusInfo();
		StatusInfo newStatus = queryStatus(application);
		
		Application newState = Application.create(application)
				.withStatusInfo(newStatus).build();
		store.save(newState);
		
		if (!newStatus.equals(oldStatus)) {
			publisher.publishEvent(new ClientApplicationStatusChangedEvent(
					this, newState, oldStatus, newStatus));
		}
	}
	
	private StatusInfo queryStatus(Application application) {
		LOGGER.trace("Updating status for {}", application);
		
		try {
			@SuppressWarnings("unchecked")
			ResponseEntity<Map<String, String>> response = restTemplate
					.getForEntity(application.getHealthUrl(),
							(Class<Map<String, String>>) (Class<?>) Map.class);
			LOGGER.debug("/health for {} responded with {}", application,
					response);
			
			if (response.hasBody() && response.getBody().get("status") != null) {
				return StatusInfo.valueOf(response.getBody().get("status"));
			} else if (response.getStatusCode().is2xxSuccessful()) {
				return StatusInfo.ofUp();
			} else {
				return StatusInfo.ofDown();
			}
			
		} catch (RestClientException ex) {
			LOGGER.warn("Couldn't retrieve status for {}", application, ex);
			return StatusInfo.ofOffline();
		}
	}
	
	public void setStatusLifetime(long statusLifetime) {
		this.statusLifetime = statusLifetime;
	}
	
	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}
	
}
