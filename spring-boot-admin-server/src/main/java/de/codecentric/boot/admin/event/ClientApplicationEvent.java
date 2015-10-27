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
package de.codecentric.boot.admin.event;

import org.springframework.context.ApplicationEvent;

import de.codecentric.boot.admin.model.Application;

/**
 * Abstract Event regearding spring boot admin clients
 * 
 * @author Johannes Stelzer
 */
public abstract class ClientApplicationEvent extends ApplicationEvent {
	private static final long	serialVersionUID	= 1L;
	
	private final Application	application;
	
	public ClientApplicationEvent(Object source, Application application) {
		super(source);
		this.application = application;
	}
	
	public Application getApplication() {
		return application;
	}
}
