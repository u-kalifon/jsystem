/*
 * Copyright 2005-2010 Ignis Software Tools Ltd. All rights reserved.
 */
package jsystem.runner.agent.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jsystem.framework.fixture.Fixture;
import jsystem.framework.fixture.FixtureManager;
import jsystem.framework.report.JSystemListeners;
import jsystem.runner.loader.LoadersManager;

public class FixtureChangedNotification extends RunnerNotification {
	private static final long serialVersionUID = 7821056231383938522L;
	private static Logger log = LoggerFactory.getLogger(FixtureChangedNotification.class);
	private String fixtureClassName;
	public FixtureChangedNotification(Object source,String fixtureClassName) {
		super(FixtureChangedNotification.class.getName(), source);
		this.fixtureClassName = fixtureClassName;
	}
	public void invokeDispatcher(JSystemListeners mediator){
		ClassLoader loader = LoadersManager.getInstance().getLoader();
		try {
			Class<?> fixtureClass = (Class<?>)loader.loadClass(getFixtureClassName());
			Fixture fixtureInstance = (Fixture)fixtureClass.newInstance();
			FixtureManager.getInstance().setCurrentFixture(getFixtureClassName());
			mediator.fixtureChanged(fixtureInstance);			
		} catch (Exception e){
			log.warn("Failed creating instance of fixture " + getFixtureClassName() + " " + e.getMessage());
		}
	}
	public String getFixtureClassName() {
		return fixtureClassName;
	}
}
