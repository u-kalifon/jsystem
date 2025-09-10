/*
 * Copyright 2005-2010 Ignis Software Tools Ltd. All rights reserved.
 */
package jsystem.treeui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jsystem.runner.agent.server.RunnerEngine;

public class GracefulStopListener implements WaitDialogListener {
	RunnerEngine agent;
	private static Logger log = LoggerFactory.getLogger(GracefulStopListener.class);
	
	public GracefulStopListener(RunnerEngine agent){
		this.agent = agent;
	}
	
	public void cancel() {
		try {
			agent.stop();
		} catch (Exception e) {
			log.error("Failed stopping execution. " + e.getMessage());

		}
	}

}
