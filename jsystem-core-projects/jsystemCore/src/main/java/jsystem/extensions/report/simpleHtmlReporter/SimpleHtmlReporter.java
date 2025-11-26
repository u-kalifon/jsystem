package jsystem.extensions.report.simpleHtmlReporter;

import il.co.topq.difido.PersistenceUtils;
import il.co.topq.difido.model.execution.Execution;
import il.co.topq.difido.model.test.TestDetails;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jsystem.extensions.report.html.ExtendLevelTestReporter;
import jsystem.extensions.report.simpleHtmlReporter.dto.ReportElementDto;
import jsystem.framework.FrameworkOptions;
import jsystem.framework.JSystemProperties;
import jsystem.framework.common.CommonResources;
import jsystem.framework.report.ExtendTestListener;
import jsystem.framework.report.ListenerstManager;
import jsystem.framework.report.Summary;
import jsystem.framework.report.TestInfo;
import jsystem.framework.scenario.ScenariosManager;
import jsystem.framework.sut.SutFactory;
import jsystem.utils.BrowserLauncher;
import jsystem.utils.DateUtils;

import org.apache.commons.io.FileUtils;

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
 */
public class SimpleHtmlReporter implements ExtendLevelTestReporter, ExtendTestListener {

	private static final Logger log = LoggerFactory.getLogger(SimpleHtmlReporter.class);

	private String reportDir;
	private File logDirectory;
	private boolean firstTest = true;  //TODO: replace this with a startScenario event

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
		ReportElementDto currentStep = ReportElementDto.newStep(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME), 
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
}
