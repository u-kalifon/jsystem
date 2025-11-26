package jsystem.extensions.report.simpleHtmlReporter;

import java.io.File;
import java.io.IOException;
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

	private String reportDir;
	private File logDirectory;
	private boolean firstTest = true;  //TODO: replace this with a startScenario event
	private Deque<String> logLevels = new ArrayDeque<>();
	private ReportElementDto currentStep = null;	// FIXME: currentStep should be a property of the current level (container)
	private Deque<JTestContainer> containerLevels = new ArrayDeque<>();	// TODO: replace this with a specialized class for containers

	@Override
	public void initReporterManager() throws IOException {
		BrowserLauncher.openURL(getIndexFile().getAbsolutePath());
	}

	private File getIndexFile() {
		return new File(getLogDirectory(), "current" + File.separator + "index.html");
	}

	@Override
	public void init() {
		lockMutex();	// if it's already locked - it means that the reporter is already initialized

		// TODO: create the log directory, if it doesn't already exist
		
		getLogDir();
		unlockMutex();
	}

	private void lockMutex() {
		// TODO
	}
	
	private void unlockMutex() {
		// TODO
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
