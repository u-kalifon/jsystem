/*
 * Copyright 2005-2010 Ignis Software Tools Ltd. All rights reserved.
 */
package jsystem.runner.agent.publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jsystem.framework.FrameworkOptions;
import jsystem.framework.JSystemProperties;
import jsystem.runner.loader.LoadersManager;
import jsystem.utils.StringUtils;

/**
 * This class is used to load the publisher. The publisher class is read from
 * the jsystem.properties file under 'publisher'
 * 
 * @author guy.arieli
 * 
 */
public class PublisherManager {
	private static Logger log = LoggerFactory.getLogger(PublisherManager.class);

	private static PublisherManager manager = null;

	private Publisher publisher;

	/**
	 * Use it to get an instance of the publisher manager.
	 * 
	 * @return PublisherManager
	 */
	public static PublisherManager getInstance() {
		if (manager == null) {
			manager = new PublisherManager();
		}
		return manager;
	}

	private PublisherManager() {

	}

	/**
	 * Loads the publisher from the JSystem properties. If none was specified, load the default publisher.
	 */
	private void loadPublisher() {
		final String publisherClassName = JSystemProperties.getInstance()
				.getPreference(FrameworkOptions.REPORTS_PUBLISHER_CLASS);
		if (StringUtils.isEmpty(publisherClassName)) {
			publisher = null;
			log.warn("No publiher was defined");
			publisher = new EmptyPublisher();
			return;
		}
		try {
			Class<?> publisherClass = LoadersManager.getInstance().getLoader().loadClass(publisherClassName);
			if (publisherClass != null) {
				Object instance = publisherClass.newInstance();
				if (instance instanceof Publisher) {
					log.info("Reports publisher : " + publisherClassName + " Was loaded.");
					publisher = (Publisher) instance;
				}
			}
		} catch (Exception e) {
			log.warn("Fail to init publisher : " + publisherClassName, e);
			publisher = new EmptyPublisher();
		}

	}

	/**
	 * used to return the publisher
	 * 
	 * @return the publisher object
	 */
	public Publisher getPublisher() {
		if (null == publisher){
			loadPublisher();
		}
		return publisher;
	}

}
