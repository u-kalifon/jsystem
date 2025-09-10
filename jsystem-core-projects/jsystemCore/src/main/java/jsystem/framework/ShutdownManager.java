/*
 * Created on 17/05/2005
 *
 * Copyright 2005-2010 Ignis Software Tools Ltd. All rights reserved.
 */
package jsystem.framework;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author guy.arieli
 * 
 */
public class ShutdownManager {
	private static Logger log = LoggerFactory.getLogger(ShutdownManager.class);

	private static boolean wasInit = false;

	public static void init() {
		if (wasInit) {
			return;
		}
		if (JSystemProperties.getInstance().isReporterVm() && JSystemProperties.getInstance().isJsystemRunner()) {
			wasInit = true;
			return;
		}

		String threadClassName = JSystemProperties.getInstance().getPreference(FrameworkOptions.SHUTDOWN_THREADS);
		if (threadClassName != null) {
			String[] threads = threadClassName.split(";");
			for (int i = 0; i < threads.length; i++) {
				Class<?> c;
				Thread t;
				try {
					c = Class.forName(threads[i]);
					t = (Thread) c.newInstance();
					Runtime.getRuntime().addShutdownHook(t);
				} catch (Throwable e) {
					log.warn("Fail to load thread", e);
				}

			}
		} else {
			Runtime.getRuntime().addShutdownHook(new DefaultShutdownHook());
		}
		wasInit = true;
	}

}
