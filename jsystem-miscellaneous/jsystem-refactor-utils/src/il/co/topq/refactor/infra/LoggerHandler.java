package il.co.topq.refactor.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class LoggerHandler {
	private static final Logger logger = LoggerFactory.getLogger(LoggerHandler.class);

	public static void initLogger() {
		// Install SLF4J bridge to capture JUL logs from third-party libraries
		if (!SLF4JBridgeHandler.isInstalled()) {
			SLF4JBridgeHandler.removeHandlersForRootLogger();
			SLF4JBridgeHandler.install();
		}
		logger.info("SLF4J logging bridge initialized for refactor utils");
		// Logback configuration is handled by logback.xml in resources
	}

}
