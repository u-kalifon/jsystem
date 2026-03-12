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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jsystem.extensions.report.html.ExtendLevelTestReporter;
import jsystem.extensions.report.simpleHtmlReporter.ContainerStack.Container;
import jsystem.extensions.report.simpleHtmlReporter.dto.Execution;
import jsystem.extensions.report.simpleHtmlReporter.dto.ReportElementDto;
import jsystem.extensions.report.simpleHtmlReporter.dto.Status;
import jsystem.extensions.report.simpleHtmlReporter.dto.TestReportDto;
import jsystem.framework.FrameworkOptions;
import jsystem.framework.JSystemProperties;
import jsystem.framework.report.ExtendTestListener;
import jsystem.framework.report.TestInfo;
import jsystem.framework.scenario.JTestContainer;
import jsystem.framework.scenario.flow_control.AntForLoop;
import jsystem.framework.sut.SutFactory;
import jsystem.utils.BrowserLauncher;
import junit.framework.AssertionFailedError;
import junit.framework.Test;

/**
 * 
 * @author Udi Kalifon
 * 
 * This replaces HtmlReporter (a difido reporter, written by Itai Agmon).
 * It writes the course of the scenario into a single file, to allow for continuous
 * reading of the report (as opposed to writing each step to a separate file).
 * 
 * It also supports running multiple instances of the runner, synchronizing the access to
 * the execution file.
 * 
 * This is still a (big) work in progress.
 * 
 */
public class SimpleHtmlReporter implements ExtendLevelTestReporter, ExtendTestListener {

	private static final Logger log = LoggerFactory.getLogger(SimpleHtmlReporter.class);

	private static final String LOCK_FILE_NAME = ".reporter.lock";
	private static final long DEFAULT_LOCK_TIMEOUT_MS = 2 * 60 * 1000; // 2 minutes
	private static final long LOCK_RETRY_INTERVAL_MS = 200; // 100ms between retries
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

	private String reportDir;
	private File logDirectory = null;
	private ReportElementDto currentStep = null;
	private ContainerStack containerStack = new ContainerStack();
	
	private FileChannel lockChannel;
	private FileLock fileLock;

	// this represents the scenario report (will be writen to scenario.js in the end of the run)
	private TestReportDto testReportDto = null;

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

			// Copy properties.js from template if it doesn't exist
			Path propertiesPath = logDirPath.resolve("properties.js");
			if (!Files.exists(propertiesPath)) {
				copyTemplateFile("properties.js", propertiesPath);
			}

			// Copy resource directories if they don't exist
			copyTemplateDirectoryIfNotExists("controllers", logDirPath, CONTROLLER_FILES);
			copyTemplateDirectoryIfNotExists("css", logDirPath, CSS_FILES);
			copyTemplateDirectoryIfNotExists("js", logDirPath, JS_FILES);

			// Create the execution DTO, with the local machine and an empty scenario array
			Execution execution = new Execution();
			execution.setMachines(new ArrayList<Execution.Machine>());
			execution.getMachines().add(new Execution.Machine("local", new ArrayList<>()));

			// Write the execution DTO to the execution.js file
			File executionJsonFile = getExecutionJsonFile();
			try {
				String json = JsonReportSerializer.toJson(execution);
				String jsContent = "var execution = " + json + ";";
				Files.writeString(executionJsonFile.toPath(), jsContent);
			} catch (IOException e) {
				log.error("Failed to write execution to: " + executionJsonFile, e);
				throw new RuntimeException("Failed to write execution to: " + executionJsonFile, e);
			}
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
	
	private File getExecutionJsonFile() {
		String logDir = getLogDir();
		return new File(logDir + "/execution.js");
	}

	private Path getScenarioDir(String scenarioNameAndUid) {
		String logDir = getLogDir();
		return new File(logDir + "/scenarios/" + scenarioNameAndUid).toPath().toAbsolutePath();
	}

	private File getScenarioJsFile(String scenarioNameAndUid) {
		Path scenarioDir = getScenarioDir(testReportDto.getNameAndUid());
		return new File(scenarioDir.resolve("scenario.js").toString());
	}

	private void createScenarioDirectory(String scenarioNameAndUid) {
		Path logDirPath = getScenarioDir(scenarioNameAndUid);
		
		try {
			Files.createDirectories(logDirPath);
		} catch (IOException e) {
			// Check if the directory exists (may have been created by another process)
			if (!Files.isDirectory(logDirPath)) {
				log.error("Failed to create scenario directory: " + logDirPath, e);
				throw new RuntimeException("Failed to create scenario directory: " + logDirPath, e);
			}
		}

		// copy the scenario.html file to the newly created scenario directory
		String resourcePath = TEMPLATE_RESOURCE_PATH + "scenario.html";
		try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
			if (is == null) {
				log.error("Template resource not found: " + resourcePath);
				throw new RuntimeException("Template resource not found: " + resourcePath);
			}
			Files.copy(is, logDirPath.resolve("scenario.html"));
		} catch (IOException e) {
			log.error("Failed to copy template file: " + resourcePath + " to " + logDirPath, e);
			throw new RuntimeException("Failed to copy template file: " + resourcePath + " to " + logDirPath, e);
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
		log.debug("### Recieved start test event -> " + testInfo.toString());
		String stepName = sanitizeClassName(testInfo.className) + "." + testInfo.methodName;
		currentStep = ReportElementDto.newStep(LocalDateTime.now().format(DATE_TIME_FORMATTER), 
			String.join(" ", stepName, testInfo.meaningfulName), null);
		currentStep.addProperty("Class", testInfo.className);
		currentStep.addProperty("Method", testInfo.methodName);
		currentStep.setUserDoc(testInfo.userDoc);

		if (testInfo.parameters != null && !testInfo.parameters.trim().isEmpty()) {
			log.debug("Adding parameters " + testInfo.parameters);
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

		// add the step to the scenario report (adds an item to the log) and write to disk
		testReportDto.getReportElements().add(currentStep);
		appendReportElementToScenarioJs(currentStep);

		// TODO: understand what testInfo.comment is
		
	}

	/**
	 * Appends a ReportElementDto to the scenario.js file as JavaScript code.
	 * The element is serialized to JSON and formatted as a JavaScript statement
	 * that creates a variable and pushes it to test.reportElements.
	 * 
	 * This is just to support the case where the scenario hangs (god forbid!) and we
	 * want to see the report so far. If the test exits gracefully, the report will be
	 * written again to disk, this time as one big json object.
	 * 
	 * Another consideration for this implementation is that we don't want to write to
	 * disk too often, so we append only the new element, and not the whole report.
	 *
	 * @param element the ReportElementDto to append
	 */
	private void appendReportElementToScenarioJs(ReportElementDto element) {
		if (containerStack.isEmpty()) {
			log.error("Cannot append report element: no container is active");
			return;
		}

		File scenarioJsFile = getScenarioJsFile(testReportDto.getNameAndUid());

		try {
			String json = JsonReportSerializer.toJson(element);
			
			StringBuilder jsCode = new StringBuilder();
			jsCode.append("\n");  // Blank line for readability
			jsCode.append("newReportElement = ");
			jsCode.append(json);
			jsCode.append(";\n");
			jsCode.append("test.reportElements.push(newReportElement);\n");

			Files.writeString(scenarioJsFile.toPath(), jsCode.toString(), 
					StandardOpenOption.APPEND, StandardOpenOption.CREATE);
			
			log.debug("Appended report element to: " + scenarioJsFile);
		} catch (IOException e) {
			log.error("Failed to append report element to: " + scenarioJsFile, e);
		}
	}

	@Override
	public void startTest(Test test) {
		// not used
	}

	@Override
	public void endTest(Test test) {
		log.debug("### Recieved end test event -> " + test.toString());
		currentStep.setStatus(Status.SUCCESS);	// if it's already more severe (WARNING, ERROR...) there won't be an update
		currentStep = null;
	}

	@Override
	public void endRun() {
		// ignore for now (assuming that everything is written to disk)
		log.debug("### Recieved end run event");
	}

	// TODO: implement the rest of the methods to log lines, levels, etc...

	public File getLogDirectory() {
		if (logDirectory == null) {
			logDirectory = new File(getLogDir());
		}
		return logDirectory;
	}

	@Override
	public String getName() {
		return "SimpleHtmlReporter";
	}

	/**
	 * Sanitizes a scenario name by:
	 * 1. Removing the "scenarios/" prefix if present
	 * 2. Replacing all remaining slashes with dots
	 *
	 * @param name the scenario name to sanitize
	 * @return the sanitized scenario name
	 */
	private String sanitizeScenarioName(String name) {
		if (name == null) {
			return null;
		}
		if (name.startsWith("scenarios/")) {
			name = name.substring("scenarios/".length());
		}
		return name.replace("/", ".");
	}

	/**
	 * Sanitizes a sut file name by removing the "sut\" prefix if present
	 *
	 * @param name the sut file name to sanitize
	 * @return the sanitized sut file name
	 */
	private String sanitizeSutFileName(String name) {
		if (name == null) {
			return null;
		}
		if (name.startsWith("sut\\")) {
			name = name.substring("sut\\".length());
		}
		return name;
	}

	/**
	 * Sanitizes a class name by removing the fqdn and returning only the last token which is the class name.
	 * Tokens are separated by ".".
	 *
	 * @param className the fully qualified class name
	 * @return the last token which is the class name
	 */
	private String sanitizeClassName(String className) {
		if (className == null) {
			return null;
		}
		String[] tokens = className.split("\\.");
		return tokens[tokens.length - 1];
	}

	@Override
	public void startContainer(JTestContainer container) {
		log.debug(("Received start container event: " + container.toString()));
		currentStep = null;
		String scenarioName = sanitizeScenarioName(container.getName());

		// if this is the first container, start the scenario
		if (containerStack.isEmpty()) {
			initTestReportDto(scenarioName, container.getDocumentation());

			// Create the directory for the scenario and write the testReportDto to it
			createScenarioDirectory(testReportDto.getNameAndUid());
			File scenarioJsFile = getScenarioJsFile(testReportDto.getNameAndUid());
			try {
				dumpAsJsToFile(testReportDto, scenarioJsFile);
				log.debug("Wrote initial test report to: " + scenarioJsFile);
			} catch (IOException e) {
				log.error("Failed to write initial test report to: " + scenarioJsFile, e);
				throw new RuntimeException("Failed to write initial test report to: " + scenarioJsFile, e);
			}

			// Get the SUT file path relative to the classes directory
			Path sutFilePath = SutFactory.getInstance().getSutFile().toPath().toAbsolutePath();
			String classesPath = JSystemProperties.getInstance().getPreference(FrameworkOptions.TESTS_CLASS_FOLDER);
			Path classesDir = new File(classesPath).toPath().toAbsolutePath();
			String relativeSutFile = classesDir.relativize(sutFilePath).toString();
			String sanitizedSutFile = sanitizeSutFileName(relativeSutFile);
			testReportDto.addProperty("sutFile", sanitizedSutFile);
			testReportDto.addProperty("Timestamp", testReportDto.getTimestamp());

			// Add the scenario to the execution file
			addScenarioToExecutionFile(scenarioName, testReportDto.getTimestamp(), sanitizedSutFile, testReportDto.getUid());
		} else {
			ReportElementDto childScenarioStart = ReportElementDto.newChildScenarioStart(LocalDateTime.now().format(DATE_TIME_FORMATTER), container.getName(), container.getDocumentation());
			testReportDto.getReportElements().add(childScenarioStart);
			appendReportElementToScenarioJs(childScenarioStart);
		}

		containerStack.push(scenarioName);
	}

	@Override
	public void endContainer(JTestContainer container) {
		// this is the end of a scenario (or a child scenario)
		log.debug(("Received end container event: " + container.toString()));
		currentStep = null;

		closeAllLevels();
		Container oldContainer = containerStack.pop();		// will update the status of the containing container and log level

		// when all containers are finished, re-serialize the scenario report and write it to disk
		if (containerStack.isEmpty()) {
			testReportDto.setStatus(oldContainer.getStatus());	// overall scenario status
			File scenarioJsFile = getScenarioJsFile(testReportDto.getNameAndUid());
			try {
				dumpAsJsToFile(testReportDto, scenarioJsFile);
				log.debug("Wrote final test report to: " + scenarioJsFile);
			} catch (IOException e) {
				log.error("Failed to write final test report to: " + scenarioJsFile, e);
				throw new RuntimeException("Failed to write final test report to: " + scenarioJsFile, e);
			}

			String duration = composeDuration(testReportDto.getTimestamp());
			updateScenarioInExecutionFile(testReportDto.getUid(), testReportDto.getStatus(), duration);
			testReportDto = null;
		} else {
			ReportElementDto childScenarioEnd = ReportElementDto.newChildScenarioEnd(LocalDateTime.now().format(DATE_TIME_FORMATTER));
			testReportDto.getReportElements().add(childScenarioEnd);
			appendReportElementToScenarioJs(childScenarioEnd);
		}
	}

	/**
	 * Initializes the testReportDto with the given name.
	 * 
	 * @param scenarioName the name to set on the TestReportDto
	 * @throws IllegalStateException if testReportDto is already initialized
	 */
	private void initTestReportDto(String scenarioName, String scenarioDescription) {
		if (testReportDto != null) {
			throw new IllegalStateException("testReportDto is already initialized");
		}
		testReportDto = new TestReportDto();
		testReportDto.setUid(UUID.randomUUID().toString());
		testReportDto.setName(scenarioName);
		testReportDto.setDescription(scenarioDescription);
		testReportDto.setTimestamp(LocalDateTime.now().format(DATE_TIME_FORMATTER));
		testReportDto.setStatus(Status.RUNNING);
		testReportDto.setReportElements(new ArrayList<>());
	}

	private String composeDuration(String initialTimestamp) {
		LocalDateTime initialDateTime = LocalDateTime.parse(initialTimestamp, DATE_TIME_FORMATTER);
		LocalDateTime now = LocalDateTime.now();
		Duration duration = Duration.between(initialDateTime, now);
		long hours = duration.toHours();
		long minutes = duration.toMinutesPart();
		long seconds = duration.toSecondsPart();
		return String.format("%dh%dm%ds", hours, minutes, seconds);
	}

	/**
	 * Serializes a TestReportDto to JavaScript format and writes it to a file.
	 * The output format is: var test = {...};
	 *
	 * @param dto  the TestReportDto to serialize
	 * @param file the file to write to
	 * @throws IOException if serialization or file writing fails
	 */
	public void dumpAsJsToFile(TestReportDto dto, File file) throws IOException {
		String json = JsonReportSerializer.toJson(dto);
		String jsContent = "var test = " + json + ";";
		Files.writeString(file.toPath(), jsContent);
	}

	/**
	 * Adds a new scenario to the execution file.
	 * This method locks the mutex, reads the execution file, adds the scenario,
	 * writes the updated execution file, and unlocks the mutex.
	 *
	 * @param scenarioName the name of the scenario to add
	 * @param timestamp the timestamp when the scenario started
	 */
	private void addScenarioToExecutionFile(String scenarioName, String timestamp, String sutFile, String uid) {
		if (!lockMutex()) {
			log.error("Failed to acquire lock for adding scenario to execution file");
			return;
		}
		
		try {
			File executionJsonFile = getExecutionJsonFile();
			String content = Files.readString(executionJsonFile.toPath());
			
			// Parse the execution file (format: "var execution = {...};")
			String jsonContent = content
				.substring("var execution = ".length())
				.replaceFirst(";\\s*$", "");
			
			Execution execution = JsonReportSerializer.executionFromJson(jsonContent);
			
			// Create a new ExecutionScenario and add it to the first machine
			Execution.ExecutionScenario newScenario = new Execution.ExecutionScenario(scenarioName, sutFile, timestamp, uid);
			if (!execution.getMachines().isEmpty()) {
				execution.getMachines().get(0).getChildren().add(newScenario);
			} else {
				log.error("No machines found in execution file");
				return;
			}
			
			// Write the updated execution back to the file
			String json = JsonReportSerializer.toJson(execution);
			String jsContent = "var execution = " + json + ";";
			Files.writeString(executionJsonFile.toPath(), jsContent);
			
			log.debug("Added scenario to execution file: " + scenarioName);
		} catch (IOException e) {
			log.error("Failed to add scenario to execution file", e);
		} finally {
			unlockMutex();
		}
	}

	private void updateScenarioInExecutionFile(String uid, Status status, String duration) {
		if (!lockMutex()) {
			log.error("Failed to acquire lock for adding scenario to execution file");
			return;
		}
		
		try {
			File executionJsonFile = getExecutionJsonFile();
			String content = Files.readString(executionJsonFile.toPath());
			
			// Parse the execution file (format: "var execution = {...};")
			String jsonContent = content
				.substring("var execution = ".length())
				.replaceFirst(";\\s*$", "");
			
			Execution execution = JsonReportSerializer.executionFromJson(jsonContent);
			Execution.ExecutionScenario scenario = execution.getScenarioByUid(uid);
			scenario.setStatus(status.name());
			scenario.setDuration(duration);

			// Write the updated execution back to the file
			String json = JsonReportSerializer.toJson(execution);
			String jsContent = "var execution = " + json + ";";
			Files.writeString(executionJsonFile.toPath(), jsContent);
			
			log.debug("Updated scenario in execution file: " + uid);
		} catch (IOException e) {
			log.error("Failed to update scenario in execution file", e);
		} finally {
			unlockMutex();
		}
	}

	@Override
	public void startLevel(String levelName, jsystem.framework.report.Reporter.EnumReportLevel place) throws IOException {

		// FIXME: levels should have a log line and not just a level name

		// FIXME 2: avoid code duplication with the overloaded derivative with place = 0 (CurrentPlace) and place = 1 (MainFrame)

		log.debug("Starting level: " + levelName + " at place: " + place.name());	// place = CurrentPlace or MainFrame
		if (containerStack.isEmpty()) {
			log.error("Cannot start level: no container is active");
			return;
		}
		containerStack.peek().startLevel(levelName);
		ReportElementDto levelStart = ReportElementDto.newLogLevelStart(LocalDateTime.now().format(DATE_TIME_FORMATTER), 
			levelName);		// FIXME: we want to report a line, not the level name
		testReportDto.getReportElements().add(levelStart);
		appendReportElementToScenarioJs(levelStart);
	}

	@Override
	public void startLevel(String levelName, int place) throws IOException {
		log.debug("Starting level: " + levelName + " at place: " + place);
		if (containerStack.isEmpty()) {
			log.error("Cannot start level: no container is active");
			return;
		}
		containerStack.peek().startLevel(levelName);
		ReportElementDto levelStart = ReportElementDto.newLogLevelStart(LocalDateTime.now().format(DATE_TIME_FORMATTER), 
			levelName);		// FIXME: we want to report a line, not the level name
		testReportDto.getReportElements().add(levelStart);
		appendReportElementToScenarioJs(levelStart);
	}

	@Override
	public void stopLevel() {
		if (containerStack.isEmpty()) {
			log.error("Attempted to stop level but no container is active");
			return;
		}
		String currentLevelName = containerStack.peek().getCurrentLevelName();
		if (currentLevelName != null) {
			log.debug("Stopping level: " + currentLevelName);
			ReportElementDto levelStop = ReportElementDto.newLogLevelStop(LocalDateTime.now().format(DATE_TIME_FORMATTER));
			testReportDto.getReportElements().add(levelStop);
			appendReportElementToScenarioJs(levelStop);
			containerStack.peek().endLevel();
		} else {
			log.warn("Attempted to stop level but logLevels stack is empty");
		}
	}

	@Override
	public void closeAllLevels() {
		if (containerStack.isEmpty()) {
			log.error("Cannot close levels: no container is active");
			return;
		}
		String currentLevelName = containerStack.peek().getCurrentLevelName();
		while (currentLevelName != null) {
			log.debug("Closing all levels. Current level name: " + currentLevelName);
			stopLevel();
			currentLevelName = containerStack.peek().getCurrentLevelName();
		}
	}

	@Override
	public void closeLevelsUpTo(String levelName, boolean includeLevel) {
		log.debug("Closing levels up to: " + levelName + " (include: " + includeLevel + ")");
		
		if (containerStack.isEmpty()) {
			log.error("Cannot close levels: no container is active");
			return;
		}
		
		// Pop levels until we find the specified level or the stack is empty, or until the container level is reached
		String currentLevelName = containerStack.peek().getCurrentLevelName();
		while (currentLevelName != null && !currentLevelName.equals(levelName)) {
			log.debug("Closing all levels up to: " + levelName + ". Current level name: " + currentLevelName);
			stopLevel();
			currentLevelName = containerStack.peek().getCurrentLevelName();
		}

		if (currentLevelName != null && currentLevelName.equals(levelName)) {
			if (includeLevel) {
				log.debug("Closing all levels up to and including: " + levelName + ". Current level name: " + currentLevelName);
				stopLevel();
			}
			return;
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
		// TODO: This should stop a container.
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
			// FIXME: handle the ERROR status
		};

		// TODO: support bold (used to be a step), html and link

		ReportElementDto reportEntry = ReportElementDto.newReportEntry(
			LocalDateTime.now().format(DATE_TIME_FORMATTER), title, message, status);
		testReportDto.getReportElements().add(reportEntry);
		appendReportElementToScenarioJs(reportEntry);

		// update the statuses of the current step, the current log level, and the current container
		currentStep.setStatus(status);	// will only update if it's more severe
		containerStack.peek().setStatus(status);	// will only update if it's more severe
	}

	@Override
	public void addWarning(Test test) {
		// FIXME: get rid of this method
		report("", "WARNING added by call to addWarning()", 2, false, false, false);
	}

	@Override
	public void addFailure(Test test, AssertionFailedError error) {
		// FIXME: understand how AssertionFailedError is used in the original JSystem, and get rid of this method
		if (currentStep != null) {
			currentStep.setStatus(Status.FAILURE);
			containerStack.peek().setStatus(Status.FAILURE);
			ReportElementDto errorReport = ReportElementDto.newFailureReport(
				LocalDateTime.now().format(DATE_TIME_FORMATTER), error.getMessage(), "");
			// TODO: make sure the stack is reported also

			testReportDto.getReportElements().add(errorReport);
			appendReportElementToScenarioJs(errorReport);
			}
	}

	@Override
	public void addError(Test test, Throwable error) {
		// FIXME: understand the differences between addFailure() and this, and get rid of this method
		if (currentStep != null) {
			currentStep.setStatus(Status.ERROR);
			containerStack.peek().setStatus(Status.ERROR);
			ReportElementDto errorReport = ReportElementDto.newErrorReport(
				LocalDateTime.now().format(DATE_TIME_FORMATTER), error.getMessage(), "");
			// TODO: make sure the stack is reported also

			testReportDto.getReportElements().add(errorReport);
			appendReportElementToScenarioJs(errorReport);
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
		// FIXME: we want to get rid of scenario properties
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
