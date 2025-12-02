package jsystem.extensions.report.simpleHtmlReporter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jsystem.extensions.report.html.ExtendLevelTestReporter;
import jsystem.extensions.report.simpleHtmlReporter.dto.ReportElementDto;
import jsystem.extensions.report.simpleHtmlReporter.dto.Status;
import jsystem.framework.FrameworkOptions;
import jsystem.framework.JSystemProperties;
import jsystem.framework.report.ExtendTestListener;
import jsystem.framework.report.TestInfo;
import jsystem.framework.scenario.JTestContainer;
import jsystem.framework.scenario.flow_control.AntForLoop;
import jsystem.utils.BrowserLauncher;
import junit.framework.AssertionFailedError;
import junit.framework.Test;

/**
 * 
 * @author Udi Kalifon
 * 
 * This replaces HtmlReporter (by Itai Agmon), which is a difido reporter.
 * It writes the course of the scenario into a single file, to allow for continuous
 * reading of the report.
 * It also supports running multiple instances of the runner, synchronizing the access to
 * the execution file.
 * 
 * This is still a work in progress.
 * 
 */
public class SimpleHtmlReporter implements ExtendLevelTestReporter, ExtendTestListener {

	private static final Logger log = LoggerFactory.getLogger(SimpleHtmlReporter.class);

	private static final String LOCK_FILE_NAME = ".reporter.lock";
	private static final long DEFAULT_LOCK_TIMEOUT_MS = 2 * 60 * 1000; // 2 minutes
	private static final long LOCK_RETRY_INTERVAL_MS = 200; // 100ms between retries

	private String reportDir;
	private File logDirectory;
	private boolean firstTest = true;  //TODO: replace this with a startScenario event
	private Deque<String> logLevels = new ArrayDeque<>();
	private ReportElementDto currentStep = null;	// FIXME: currentStep should be a property of the current level (container)
	private Deque<JTestContainer> containerLevels = new ArrayDeque<>();	// TODO: replace this with a specialized class for containers
	
	private FileChannel lockChannel;
	private FileLock fileLock;

	@Override
	public void initReporterManager() throws IOException {
		BrowserLauncher.openURL(getIndexFile().getAbsolutePath());
	}

	private File getIndexFile() {
		return new File(getLogDirectory(), "index.html");
	}

	public SimpleHtmlReporter() {
		init();
	}

	@Override
	public void init() {
		if (!lockMutex(false)) {	// blocking = false
			// Lock is in use - reporter is already initialized
			return;
		}

		try {
			Path logDirPath = ensureLogDirectoryExists();
			if (logDirPath == null) {
				return;
			}

			// Copy index.html from template if it doesn't exist
			Path indexPath = logDirPath.resolve("index.html");
			if (!Files.exists(indexPath)) {
				copyTemplateFile("index.html", indexPath);
			} else {
				// if it exists - we were already initialized
				return;
			}

			// Copy table.html from template if it doesn't exist
			Path tablePath = logDirPath.resolve("table.html");
			if (!Files.exists(tablePath)) {
				copyTemplateFile("table.html", tablePath);
			}

			// Copy resource directories if they don't exist
			copyTemplateDirectoryIfNotExists("controllers", logDirPath, CONTROLLER_FILES);
			copyTemplateDirectoryIfNotExists("css", logDirPath, CSS_FILES);
			copyTemplateDirectoryIfNotExists("js", logDirPath, JS_FILES);
		} finally {
			unlockMutex();
		}
	}

	private static final String TEMPLATE_RESOURCE_PATH = "/jsystem/extensions/report/simpleHtmlReporter/template/";

	private static final String[] CONTROLLER_FILES = {
		"scenarioController.js",
		"statusBarsController.js",
		"sumBarChartController.js",
		"sumTableController.js",
		"tableController.js",
		"executionPropertiesTableController.js",
		"controllerUtils.js"
	};

	private static final String[] CSS_FILES = {
		"dashboard.css",
		"general_page.css",
		"status_colors.css",
		"test_page.css",
		"table.css",
		"bootstrap.min.css",
		"jquery.dataTables.min.css"
	};

	private static final String[] JS_FILES = {
		"bootstrap.min.css",
		"bootstrap.min.js",
		"dataTables.bootstrap.min.js",
		"docs.min.js",
		"jquery-ui.min.js",
		"jquery.dataTables.js",
		"jquery.min.js",
		"lightbox-2.6.min.js"
	};

	/**
	 * Copies a template file from the classpath resources to the specified destination.
	 * 
	 * @param templateFileName the name of the template file (e.g., "index.html")
	 * @param destination the destination path to copy the file to
	 */
	private void copyTemplateFile(String templateFileName, Path destination) {
		String resourcePath = TEMPLATE_RESOURCE_PATH + templateFileName;
		try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
			if (is == null) {
				log.error("Template resource not found: " + resourcePath);
				return;
			}
			Files.copy(is, destination);
		} catch (IOException e) {
			log.error("Failed to copy template file: " + resourcePath + " to " + destination, e);
		}
	}

	/**
	 * Copies a template directory from the classpath resources to the specified destination
	 * if it doesn't already exist.
	 * 
	 * @param dirName the name of the directory (e.g., "controllers", "css")
	 * @param logDirPath the base log directory path
	 * @param files the list of files to copy from the directory
	 */
	private void copyTemplateDirectoryIfNotExists(String dirName, Path logDirPath, String[] files) {
		Path destDir = logDirPath.resolve(dirName);
		if (Files.exists(destDir)) {
			return;
		}

		try {
			Files.createDirectories(destDir);
		} catch (IOException e) {
			log.error("Failed to create directory: " + destDir, e);
			return;
		}

		for (String fileName : files) {
			String resourcePath = TEMPLATE_RESOURCE_PATH + dirName + "/" + fileName;
			Path destFile = destDir.resolve(fileName);
			try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
				if (is == null) {
					log.error("Template resource not found: " + resourcePath);
					continue;
				}
				Files.copy(is, destFile);
			} catch (IOException e) {
				log.error("Failed to copy template file: " + resourcePath + " to " + destFile, e);
			}
		}
	}

	/**
	 * Acquires an exclusive lock on the log directory.
	 * This method blocks and retries for up to 2 minutes (default timeout).
	 * 
	 * @return true if the lock was acquired, false if the lock could not be acquired within the timeout
	 */
	private boolean lockMutex() {
		return lockMutex(true, DEFAULT_LOCK_TIMEOUT_MS);
	}
	
	/**
	 * Attempts to acquire an exclusive lock on the log directory.
	 * 
	 * @param blocking if true, waits and retries up to 2 minutes; if false, returns immediately on failure
	 * @return true if the lock was acquired, false otherwise
	 */
	private boolean lockMutex(boolean blocking) {
		return lockMutex(blocking, DEFAULT_LOCK_TIMEOUT_MS);
	}
	
	/**
	 * Attempts to acquire an exclusive lock on the log directory.
	 * 
	 * @param blocking if true, waits and retries up to the specified timeout; if false, returns immediately on failure
	 * @param timeoutMs the maximum time to wait for the lock in milliseconds (only used when blocking is true)
	 * @return true if the lock was acquired, false otherwise
	 */
	private boolean lockMutex(boolean blocking, long timeoutMs) {
		try {
			// Ensure the log directory exists (handles race condition with atomic createDirectories)
			Path logDirPath = ensureLogDirectoryExists();
			if (logDirPath == null) {
				log.error("Failed to create or access log directory: " + logDirPath);
				return false;
			}
			
			Path lockFilePath = logDirPath.resolve(LOCK_FILE_NAME);
			
			// Open or create the lock file
			lockChannel = FileChannel.open(lockFilePath, 
					StandardOpenOption.CREATE, 
					StandardOpenOption.WRITE);
			
			if (blocking) {
				// Blocking mode: retry until timeout
				long startTime = System.currentTimeMillis();
				while (System.currentTimeMillis() - startTime < timeoutMs) {
					try {
						fileLock = lockChannel.tryLock();
						if (fileLock != null) {
							log.debug("Lock acquired on: " + lockFilePath);
							return true;
						}
					} catch (OverlappingFileLockException e) {
						// Lock is held by another thread in this JVM
						log.debug("Lock is held by another thread, retrying...");
					}
					
					// Wait before retrying
					try {
						Thread.sleep(LOCK_RETRY_INTERVAL_MS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						log.warn("Lock acquisition interrupted");
						closeLockChannel();
						return false;
					}
				}
				
				// Timeout reached
				log.warn("Failed to acquire lock within " + timeoutMs + "ms timeout");
				closeLockChannel();
				return false;
				
			} else {
				// Non-blocking mode: try once and return immediately
				try {
					fileLock = lockChannel.tryLock();
					if (fileLock != null) {
						log.debug("Lock acquired on: " + lockFilePath);
						return true;
					} else {
						log.debug("Lock not available (non-blocking mode)");
						closeLockChannel();
						return false;
					}
				} catch (OverlappingFileLockException e) {
					log.debug("Lock is held by another thread (non-blocking mode)");
					closeLockChannel();
					return false;
				}
			}
			
		} catch (IOException e) {
			log.error("Error acquiring lock: " + e.getMessage(), e);
			closeLockChannel();
			return false;
		}
	}
	
	/**
	 * Ensures the log directory exists, creating it if necessary.
	 * Uses atomic directory creation to handle race conditions when multiple
	 * instances try to create the directory simultaneously.
	 * 
	 * @return the Path to the log directory, or null if creation failed
	 */
	private Path ensureLogDirectoryExists() {
		String logDir = getLogDir();
		Path logDirPath = new File(logDir).toPath().toAbsolutePath();
		
		try {
			// Files.createDirectories is atomic and handles the case where
			// the directory is created by another process between the check and creation
			Files.createDirectories(logDirPath);
			return logDirPath;
		} catch (IOException e) {
			// Check if the directory exists (may have been created by another process)
			if (Files.isDirectory(logDirPath)) {
				return logDirPath;
			}
			log.error("Failed to create log directory: " + logDirPath, e);
			return null;
		}
	}
	
	/**
	 * Releases the exclusive lock on the log directory.
	 */
	private void unlockMutex() {
		try {
			if (fileLock != null && fileLock.isValid()) {
				fileLock.release();
				log.debug("Lock released");
			}
		} catch (IOException e) {
			log.error("Error releasing lock: " + e.getMessage(), e);
		} finally {
			fileLock = null;
			closeLockChannel();
		}
	}
	
	/**
	 * Closes the lock file channel.
	 */
	private void closeLockChannel() {
		if (lockChannel != null) {
			try {
				lockChannel.close();
			} catch (IOException e) {
				log.debug("Error closing lock channel: " + e.getMessage());
			}
			lockChannel = null;
		}
	}

	protected String getLogDir() {
		if (reportDir == null) {
			reportDir = JSystemProperties.getInstance().getPreference(FrameworkOptions.LOG_FOLDER);
			if (reportDir == null) {
				reportDir = "log";
			}
		}
		return reportDir;
	}

	@Override
	public void startTest(TestInfo testInfo) {
		if (firstTest) {
			//TODO: replace this with a startScenario event, or implement it here
			firstTest = false;
		}
		log.info("### Recieved start test event -> " + testInfo.toString());
		currentStep = ReportElementDto.newStep(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME), 
			testInfo.meaningfulName, null);
		currentStep.addProperty("Class", testInfo.className + "." + testInfo.methodName);

		if (testInfo.parameters != null && !testInfo.parameters.trim().isEmpty()) {
			log.info("Adding parameters " + testInfo.parameters);
			try (Scanner scanner = new Scanner(testInfo.parameters)) {
				while (scanner.hasNextLine()) {
					final String parameter = scanner.nextLine();
					if (!parameter.contains("=")) {
						log.warn("There is an illegal parameter '" + parameter + "' in test " + currentStep.getTitle());
						continue;
					}
					int equalsIndex = parameter.indexOf("=");
					String key = parameter.substring(0, equalsIndex);
					String value = parameter.substring(equalsIndex + 1);
					currentStep.addProperty(key, value);
				}
			}
		}

		if (testInfo.userDoc != null && !testInfo.userDoc.trim().isEmpty()) {
			currentStep.setUserDoc(testInfo.userDoc);
		}

		// TODO: understand what testInfo.comment is
		
		// TODO: currentScenario.addChild(currentStep);

		// TODO: write the current step to disk
	}

	@Override
	public void startTest(Test test) {
		// not used
	}

	@Override
	public void endTest(Test test) {
		log.info("### Recieved end test event -> " + test.toString());
		currentStep = null;
	}

	@Override
	public void endRun() {
		// ignore for now (assuming that everything is written to disk)
		// TODO: we need to catch the end of a scenario run, and update the execution file
	}

	// TODO: implement the rest of the methods to log lines, levels, etc...

	public void setLogDirectory(File logDirectory) {
		// TODO: make sure this is used consistently (there is also getLogDir())
		this.logDirectory = logDirectory;
	}

	public File getLogDirectory() {
		// TODO: make sure this is used consistently (there is also getLogDir())
		return logDirectory;
	}

	@Override
	public String getName() {
		return "SimpleHtmlReporter";
	}

	@Override
	public void startContainer(JTestContainer container) {
		log.info(("Received start container event: " + container.toString()));

		// TODO: properly update the container stack
		containerLevels.push(container);

		// TODO: This should start a container.
		// TODO: Containers should be hierarchical so that when stopping a container, all its children are stopped too.
	}

	@Override
	public void endContainer(JTestContainer container) {
		log.info(("Received end container event: " + container.toString()));

		closeAllLevels();
		containerLevels.pop();	// TODO: update the status (succes, failre...) of the parent
	}

	@Override
	public void startLevel(String level, jsystem.framework.report.Reporter.EnumReportLevel place) throws IOException {
		// FIXME: levels should have a log line and not just a level name
		log.info("Starting level: " + level + " at place: " + place);
		logLevels.push(level);
		// TODO: create the level node in the report structure
	}

	@Override
	public void startLevel(String levelName, int place) throws IOException {
		log.info("Starting level: " + levelName + " at place: " + place);
		logLevels.push(levelName);
		ReportElementDto levelStart = ReportElementDto.newLogLevelStart(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME), 
			levelName);		// FIXME: we want to report a line, not the level name
		// TODO: create the level node in the report structure and write to disk
	}

	@Override
	public void stopLevel() {
		if (!logLevels.isEmpty()) {
			String level = logLevels.pop();
			log.info("Stopping level: " + level);
			ReportElementDto levelStop = ReportElementDto.newLogLevelStop(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
			// TODO: finalize the level node in the report structure and write to disk
			// TODO: only a log level that was started by startLevel() should be stopped by stopLevel(), and if it was started by a container - don't stop it
		} else {
			log.warn("Attempted to stop level but logLevels stack is empty");
		}
	}

	@Override
	public void closeAllLevels() {
		log.info("Closing all levels. Current depth: " + logLevels.size());
		// TODO: only stop until the container level is reached
		while (!logLevels.isEmpty()) {
			stopLevel();
		}
	}

	@Override
	public void closeLevelsUpTo(String levelName, boolean includeLevel) {
		log.info("Closing levels up to: " + levelName + " (include: " + includeLevel + ")");
		
		// Pop levels until we find the specified level or the stack is empty, or until the container level is reached
		while (!logLevels.isEmpty()) {
			String currentLevel = logLevels.peek();
			
			if (currentLevel.equals(levelName)) {
				if (includeLevel) {
					stopLevel();
				}
				return;
			}
			
			stopLevel();
		}
		
		// If we reach here, the level was not found
		log.warn("Level '" + levelName + "' not found in stack. All levels have been closed.");
	}

	@Override
	public void startLoop(AntForLoop loop, int count) {
		// TODO: ignore for now
		// TODO: This should start a container.
		// TODO: Containers should be hierarchical so that when stopping a container, all its children are stopped too.
	}

	@Override
	public void endLoop(AntForLoop loop, int count) {
		// TODO: ignore for now
		// TODO: This should start a container.
		// TODO: Containers should be hierarchical so that when stopping a container, all its children are stopped too.
	}

	@Override
	public void report(String title, final String message, boolean isPass, boolean bold) {
		// FIXME: try to get rid of this overloaded method
		report(title, message, isPass ? 0 : 1, bold, false, false);
	}

	@Override
	public void report(String title, final String message, int statusCode, boolean bold) {
		// FIXME: try to get rid of this overloaded method
		report(title, message, statusCode, bold, false, false);
	}

	@Override
	public void report(String title, final String message, int statusCode, boolean bold, boolean html, boolean link) {
		Status status = switch (statusCode) {
			case 0 -> Status.SUCCESS;
			case 1 -> Status.FAILURE;
			case 2 -> Status.WARNING;
			default -> Status.SUCCESS;
		};

		// TODO: support bold (used to be a step), html and link

		if (status != Status.SUCCESS && currentStep != null) {
			currentStep.setStatus(Status.FAILURE);
		}
		ReportElementDto reportEntry = ReportElementDto.newReportEntry(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME), message, status);
		// TODO: add the error report to the current step + container, and write to disk
	}

	@Override
	public void addWarning(Test test) {
		if (currentStep != null) {
			currentStep.setStatus(Status.WARNING);
			// TODO: This step should receive a message to log in the report
			// TODO: write the scenario to disk
		}
	}

	@Override
	public void addFailure(Test test, AssertionFailedError error) {
		if (currentStep != null) {
			currentStep.setStatus(Status.FAILURE);
			ReportElementDto errorReport = ReportElementDto.newFailureReport(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME), error.getMessage());
			// TODO: make sure the stack is reported also
			// TODO: add the error report to the current step and write to disk
		}
	}

	@Override
	public void addError(Test test, Throwable error) {
		if (currentStep != null) {
			currentStep.setStatus(Status.ERROR);
			ReportElementDto errorReport = ReportElementDto.newErrorReport(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME), error.getMessage());
			// TODO: make sure the stack is reported also
			// TODO: add the error report to the current step and write to disk
		}
	}

	@Override
	public boolean asUI() {
		return true;		// TODO: what is the meaning of this? Preferrably this should be removed from the code
	}

	@Override
	public void saveFile(String fileName, byte[] content) {
		// TODO: save a binary file (like a screenshot, etc...)
		// TODO: this should include a log line in the report, which should be passed as a parameter
	}

	@Override
	public void setContainerProperties(final int ancestorLevel, String key, String property) {
		// TODO: find the parent containse (ignore ancestorLevel) and set the property
		// scenario.addScenarioProperty(key, property);
	}

	@Override
	public void flush() throws Exception {
		// used in stdout loggers... ignore
	}

	@Override
	public void startSection() {
		// TODO: not clear what it's for... need to investigate
	}

	@Override
	public void endSection() {
		// TODO: not clear what it's for... need to investigate
	}

	@Override
	public void addProperty(String key, String value) {
		// FIXME: make sure that this is not called from anywhere, and remove this from the interface
		currentStep.addProperty(key, value);
	}

	@Override
	public void setData(String data) {
		// TODO: not clear what it's for... need to investigate (and remove from the interface if it's not used)
	}
}
