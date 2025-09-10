/*
 * Copyright 2005-2010 Ignis Software Tools Ltd. All rights reserved.
 */
package jsystem.runner.agent.mediators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.Notification;

import jsystem.framework.report.InteractiveReporter;
import jsystem.runner.agent.notifications.NotificationLevel;
import jsystem.runner.agent.notifications.ShowMessageDialogNotification;
import jsystem.runner.agent.server.RunnerAgent;

public class InteractiveReporterMediator extends BaseMediator implements InteractiveReporter{
	private static Logger log = LoggerFactory.getLogger(InteractiveReporterMediator.class);
	
	public InteractiveReporterMediator(RunnerAgent agent){
		super(agent);	
	}
	
	@Override
	public int showConfirmDialog(String title, String message, int optionType, int messageType) {
		if (NotificationLevel.getCurrentNotificationLevel().compareTo(NotificationLevel.ALL) > 0){
			return 0;
		}				
		log.trace("showConfirmDialog(String title, String message, int optionType, int messageType) - sent");
		Notification n = new ShowMessageDialogNotification(runnerAgent().getClass().getName(),title,message,optionType,messageType);
		sendNotification(n);
		try {
			return runnerAgent().waitForConfirmDialogResults(n.getSequenceNumber());
		}catch (Exception e){
			log.warn("Error when waiting for confirm dialog",e);
		}
		return 0;
	}
}
