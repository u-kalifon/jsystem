/*
 * Copyright 2005-2010 Ignis Software Tools Ltd. All rights reserved.
 */
package jsystem.runner.agent.mediators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jsystem.framework.scenario.ScenariosManager;
import jsystem.runner.agent.notifications.AddErrorNotification;
import jsystem.runner.agent.notifications.AddFailureNotification;
import jsystem.runner.agent.notifications.EndTestNotification;
import jsystem.runner.agent.notifications.NotificationLevel;
import jsystem.runner.agent.notifications.SimpleStartTestNotification;
import jsystem.runner.agent.server.RunnerAgent;
import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestListener;

/**
 * Listens for native JUnit events , converts them to JMX notifications
 * and dispatches the notifications.
 * @author goland
 */
public class TestListenerMediator extends BaseMediator implements TestListener {
	private static Logger log = LoggerFactory.getLogger(TestListenerMediator.class);
	public TestListenerMediator(RunnerAgent agent){
		super(agent);
	}
	public void addError(Test arg0, Throwable arg1) {
		if (NotificationLevel.getCurrentNotificationLevel().compareTo(NotificationLevel.NO_FAIL) >= 0){
			return;
		}				
		log.trace("addError(Test arg0, Throwable arg1) - sent");
		int index =ScenariosManager.getInstance().getCurrentScenario().getGeneralIndex(arg0,false);
		sendNotification(new AddErrorNotification(runnerAgent().getClass().getName(),index,arg1));
	}

	public void addFailure(Test arg0, AssertionFailedError arg1) {
		if (NotificationLevel.getCurrentNotificationLevel().compareTo(NotificationLevel.NO_FAIL) >= 0){
			return;
		}						
		log.trace("addFailure(Test arg0, AssertionFailedError arg1) - sent");
		int index =ScenariosManager.getInstance().getCurrentScenario().getGeneralIndex(arg0,false);
		sendNotification(new AddFailureNotification(runnerAgent().getClass().getName(),index,arg1));
	}

	public void endTest(Test arg0) {
		if (NotificationLevel.getCurrentNotificationLevel().compareTo(NotificationLevel.NO_TEST_INDICATION) >= 0){
			return;
		}				
		log.trace("endTest(Test arg0) - sent");
		int index =ScenariosManager.getInstance().getCurrentScenario().getGeneralIndex(arg0,false);
		sendNotification(new EndTestNotification(runnerAgent().getClass().getName(),index));

	}

	public void startTest(Test arg0) {
		if (NotificationLevel.getCurrentNotificationLevel().compareTo(NotificationLevel.NO_TEST_INDICATION) >= 0){
			return;
		}				
		log.trace("endTest(Test arg0) - sent");
		int index =ScenariosManager.getInstance().getCurrentScenario().getGeneralIndex(arg0,false);
		sendNotification(new SimpleStartTestNotification(runnerAgent().getClass().getName(),index));
	}
}
