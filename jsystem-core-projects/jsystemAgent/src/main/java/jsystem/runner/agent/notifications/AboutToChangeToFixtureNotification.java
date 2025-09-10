/*
 * Copyright 2005-2010 Ignis Software Tools Ltd. All rights reserved.
 */
package jsystem.runner.agent.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jsystem.framework.fixture.Fixture;
import jsystem.framework.report.JSystemListeners;
import jsystem.runner.loader.LoadersManager;

/**
 * 
 * @author goland
 */
public class AboutToChangeToFixtureNotification extends RunnerNotification {
	private static final long serialVersionUID = 468127957018314228L;
	
	private static Logger log = LoggerFactory.getLogger(AboutToChangeToFixtureNotification.class);
	
	private String fixtureClassName;
	
	public AboutToChangeToFixtureNotification(Object source,String fixtureClassName) {
		super(AboutToChangeToFixtureNotification.class.getName(), source);
		this.fixtureClassName = fixtureClassName;
	}
	
	public void invokeDispatcher(JSystemListeners mediator){
		ClassLoader loader = LoadersManager.getInstance().getLoader();
		try {
			Class<?> fixtureClass = (Class<?>)loader.loadClass(getFixtureClassName());
			Fixture fixtureInstance = (Fixture)fixtureClass.newInstance();
			mediator.aboutToChangeTo(fixtureInstance);			
		} catch (Exception e){
			log.warn("Failed creating instance of fixture " + getFixtureClassName() + " " + e.getMessage());
		}
	}
	
	public String getFixtureClassName() {
		return fixtureClassName;
	}
}
