/*
 * Copyright 2005-2010 Ignis Software Tools Ltd. All rights reserved.
 */
package jsystem.runner.agent.mediators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jsystem.framework.report.ExecutionListener;
import jsystem.framework.report.TestInfo;
import jsystem.framework.scenario.JTestContainer;
import jsystem.framework.scenario.RunnerTest;
import jsystem.framework.scenario.flow_control.AntForLoop;
import jsystem.runner.ErrorLevel;
import jsystem.runner.agent.notifications.EndRunNotification;
import jsystem.runner.agent.notifications.ErrorInExecutionNotification;
import jsystem.runner.agent.notifications.ExecutionEndedNotification;
import jsystem.runner.agent.notifications.NotificationLevel;
import jsystem.runner.agent.notifications.RemoteExitNotification;
import jsystem.runner.agent.notifications.RemotePauseNotification;
import jsystem.runner.agent.notifications.StartTestNotification;
import jsystem.runner.agent.server.RunnerAgent;
import junit.framework.AssertionFailedError;
import junit.framework.Test;

/**
 * Listens for general execution events, converts them to JMX notifications
 * and dispatches the notifications.
 * @author goland
 */
public class ExecutionListenerMediator extends BaseMediator implements ExecutionListener {

	private static Logger log = LoggerFactory.getLogger(RunnerTest.class);
	
	public ExecutionListenerMediator(RunnerAgent agent){
		super(agent);
	}
	
	public void errorOccured(String title, String message, ErrorLevel level) {
		log.trace("errorOccured(String title, String message, ErrorLevel level)");
		sendNotification(new ErrorInExecutionNotification(runnerAgent().getClass().getName(),message,title,level));

	}

	public void executionEnded(String scenarioName) {
		log.trace("executionEnded()");
		sendNotification(new ExecutionEndedNotification(runnerAgent().getClass().getName(),scenarioName));
	}

	public void remoteExit() {
		log.trace("remoteExit()");
		sendNotification(new RemoteExitNotification(runnerAgent().getClass().getName()));
	}

	public void remotePause() {
		log.trace("remotePause()");
		sendNotification(new RemotePauseNotification(runnerAgent().getClass().getName()));
	}

	public void addWarning(Test test) {
		// TODO Auto-generated method stub

	}

	public void endRun() {
		log.trace("endRun()");
		sendNotification(new EndRunNotification(runnerAgent().getClass().getName()));

	}

	public void startTest(TestInfo testInfo) {
		if (NotificationLevel.getCurrentNotificationLevel().equals(NotificationLevel.NO_TEST_INDICATION)){
			return;
		}		
		log.trace("startTest(String className, String methodName, String meaningfulName, String comment,String paramString, int count) - sent");
		sendNotification(new StartTestNotification(runnerAgent().getClass().getName(),testInfo));
	}

	public void addError(Test arg0, Throwable arg1) {
	}

	public void addFailure(Test arg0, AssertionFailedError arg1) {
	}

	public void endTest(Test arg0) {
	}

	public void startTest(Test arg0) {

	}

	@Override
	public void endContainer(JTestContainer container) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void endLoop(AntForLoop loop, int count) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startContainer(JTestContainer container) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startLoop(AntForLoop loop, int count) {
		// TODO Auto-generated method stub
		
	}

}
