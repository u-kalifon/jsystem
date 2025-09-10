/*
 * Copyright 2005-2010 Ignis Software Tools Ltd. All rights reserved.
 */
package jsystem.runner.agent.mediators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jsystem.framework.fixture.Fixture;
import jsystem.framework.fixture.FixtureListener;
import jsystem.runner.agent.notifications.AboutToChangeToFixtureNotification;
import jsystem.runner.agent.notifications.EndFixturingNotification;
import jsystem.runner.agent.notifications.FixtureChangedNotification;
import jsystem.runner.agent.notifications.NotificationLevel;
import jsystem.runner.agent.notifications.StartFixturingNotification;
import jsystem.runner.agent.server.RunnerAgent;
/**
 * Listens for fixture navigation events, converts them to JMX notifications
 * and dispatches the notifications.
 * @author goland
 */
public class FixtureListenerMediator extends BaseMediator implements FixtureListener {
	private static Logger log = LoggerFactory.getLogger(FixtureListenerMediator.class);

	public FixtureListenerMediator(RunnerAgent agent){
		super(agent);
	}

	public void aboutToChangeTo(Fixture fixture) {
		if (NotificationLevel.getCurrentNotificationLevel().equals(NotificationLevel.NO_TEST_INDICATION)){
			return;
		}				
		log.trace("aboutToChangeTo(Fixture fixture)");
		sendNotification(new AboutToChangeToFixtureNotification(runnerAgent().getClass().getName(),fixture.getClass().getName()));
	}

	public void endFixturring() {
		if (NotificationLevel.getCurrentNotificationLevel().equals(NotificationLevel.NO_TEST_INDICATION)){
			return;
		}				
		log.trace("endFixturring()");
		sendNotification(new EndFixturingNotification(runnerAgent().getClass().getName()));
	}

	public void fixtureChanged(Fixture fixture) {
		if (NotificationLevel.getCurrentNotificationLevel().equals(NotificationLevel.NO_TEST_INDICATION)){
			return;
		}				
		log.trace("fixtureChanged(Fixture fixture)");
		sendNotification(new FixtureChangedNotification(runnerAgent().getClass().getName(),fixture.getClass().getName()));

	}

	public void startFixturring() {
		if (NotificationLevel.getCurrentNotificationLevel().equals(NotificationLevel.NO_TEST_INDICATION)){
			return;
		}				
		log.trace("startFixturring()");
		sendNotification(new StartFixturingNotification(runnerAgent().getClass().getName()));
	}
}
