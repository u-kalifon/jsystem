/*
 * Copyright 2005-2010 Ignis Software Tools Ltd. All rights reserved.
 */
package jsystem.framework.report;

import java.io.Externalizable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jsystem.extensions.report.difido.HtmlReporter;
import jsystem.extensions.report.html.ExtendLevelTestReporter;
import jsystem.extensions.report.html.RepeatTestIndex;
import jsystem.extensions.report.junit.JUnitReporter;
import jsystem.extensions.report.xml.XmlReporter;
import jsystem.framework.FrameworkOptions;
import jsystem.framework.JSystemProperties;
import jsystem.framework.RunnerStatePersistencyManager;
import jsystem.framework.analyzer.AnalyzerException;
import jsystem.framework.common.CommonResources;
import jsystem.framework.fixture.Fixture;
import jsystem.framework.fixture.FixtureListener;
import jsystem.framework.scenario.JTestContainer;
import jsystem.framework.scenario.Parameter;
import jsystem.framework.scenario.RunnerTest;
import jsystem.framework.scenario.RunningProperties;
import jsystem.framework.scenario.Scenario;
import jsystem.framework.scenario.ScenarioChangeType;
import jsystem.framework.scenario.ScenarioHelpers;
import jsystem.framework.scenario.ScenarioListener;
import jsystem.framework.scenario.ScenariosManager;
import jsystem.framework.scenario.UpgradeAndBackwardCompatibility;
import jsystem.framework.scenario.flow_control.AntForLoop;
import jsystem.framework.sut.SutListener;
import jsystem.framework.system.SystemObjectImpl;
import jsystem.framework.system.TName;
import jsystem.framework.system.TestNameServer;
import jsystem.runner.ErrorLevel;
import jsystem.runner.loader.LoadersManager;
import jsystem.utils.StackTraceUtil;
import jsystem.utils.StringUtils;
import junit.framework.AssertionFailedError;
import junit.framework.NamedTest;
import junit.framework.SystemTest;
import junit.framework.SystemTestCase;
import junit.framework.Test;
import junit.framework.TestListener;

/**
 * The RunnerListenersManager is the reporter implementation on the Reporter VM.<br>
 * When running from the JRunner, it is on the JRunner VM.<br>
 * When running from Ant\IDE, it is on the Test VM.
 * 
 */
public class RunnerListenersManager extends DefaultReporterImpl implements JSystemListeners {

	public static final String PARAMETERS_END = "Parameters end.";

	public static final String PARAMETERS_START = "Parameters:";

	private static final String RUNNER_REPORTERS_STATE_BIN = "runnerReportersState.bin";

	private static Logger log = LoggerFactory.getLogger(RunnerListenersManager.class);

	private static RunnerListenersManager manager = null;

	ArrayList<Object> listeners = new ArrayList<Object>();

	boolean silent = false;

	long startTime = 0;

	long endTime = 0;

	public static boolean lastTestFail = false;

	// This two flags are will be queried in system exit and will influence the
	// runner error level.
	
	//Will be set to true if in some point some test failed. 
	public static boolean hadFailure = false;
	
	//Will be set to true if in some point one test had warning.
	public static boolean hadWarning = false;

	long testsCount = 0;

	public Vector<Test> runningTests;

	private RepeatTestIndex testIndex;

	private String testClassName = null;

	boolean inTest = false;

	boolean blockReporters = false;

	private EventParser parser;

	private long lastGC = System.currentTimeMillis();

	private boolean testMarkedAsKnownIssue = false;

	private boolean testMarkedNegativeTest = false;

	private long lastFlashReportTime = 0;

	/**
	 * Flag for signaling an error occurred, added to support NegativeTest
	 * issue, without affecting Reporter signatures
	 */
	private boolean isError = false;

	public void addListener(Object listener) {
		removeListener(listener);
		listeners.add(listener);
	}

	public void removeListener(Object listener) {
		listeners.remove(listener);
	}

	public static JSystemListeners getInstance() {
		if (manager == null) {
			manager = new RunnerListenersManager();
			deSerializeRunnerListenersManager();
		}
		return manager;
	}

	/**
	 * Loads reporters state if signaled by the user.
	 * 
	 * @see RunnerStatePersistencyManager
	 */
	private static void deSerializeRunnerListenersManager() {
		if (!RunnerStatePersistencyManager.getInstance().getLoadReporters()) {
			return;
		}
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(RUNNER_REPORTERS_STATE_BIN);
			manager.deSerializeReporters(fis);
		} catch (Exception e) {
			throw new RuntimeException("Failed deserializing reporters. " + e.getMessage());
		} finally {
			RunnerStatePersistencyManager.getInstance().setLoadReporters(false);
			try {
				fis.close();
			} catch (Exception e) {
			}
			;
		}
	}

	public static boolean isInit() {
		return (manager != null);
	}

	private RunnerListenersManager() {
		testIndex = new RepeatTestIndex();
		String reporters = JSystemProperties.getInstance().getPreference(FrameworkOptions.REPORTERS_CLASSES);
		if (reporters == null) {
			reporters = HtmlReporter.class.getName() + ";" + SystemOutTestReporter.class.getName() + ";"
					+ XmlReporter.class.getName() + ";" + JUnitReporter.class.getName();
			JSystemProperties.getInstance().setPreference(FrameworkOptions.REPORTERS_CLASSES, reporters);
		}
		StringTokenizer st = new StringTokenizer(reporters, ";");

		while (st.hasMoreTokens()) {
			String reporterClassName = st.nextToken();
			try {
				loadReporter(reporterClassName);
			} catch (Exception e) {
				log.warn("fail to load reporter: " + reporterClassName, e);
				report("load reporter exception", StringUtils.getStackTrace(e), true);
			}
		}
		runningTests = new Vector<Test>();
		parser = new EventParser(this);
	}

	/**
	 * Saves reporters state
	 * 
	 * @see RunnerStatePersistencyManager
	 */
	@Override
	public void saveState(Test test) throws Exception {
		endTest(test);
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(RUNNER_REPORTERS_STATE_BIN);
			serializeReporters(fos);
			RunnerStatePersistencyManager.getInstance().setRunOnStart(true);
			RunnerStatePersistencyManager.getInstance().setLoadReporters(true);
			step("Agent is about to restart");
		} catch (Exception e) {
			log.warn("Failed saving reporters state" + e.getMessage());
		} finally {
			try {
				fos.close();
			} catch (Exception e) {
			}
			;
		}
	}

	/**
	 * Saves reporters state
	 * 
	 * @see RunnerStatePersistencyManager
	 */
	private void serializeReporters(OutputStream stream) throws Exception {
		ObjectOutputStream objectStream = new ObjectOutputStream(stream);
		objectStream.writeObject(UpgradeAndBackwardCompatibility.currentVersion());
		objectStream.writeObject(isSilent());
		objectStream.writeObject(startTime);
		objectStream.writeObject(lastTestFail);
		objectStream.writeObject(testIndex);
		objectStream.writeObject(testClassName);
		objectStream.writeObject(blockReporters);
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof Externalizable) {
				objectStream.writeObject(currentObject);
			}
		}
		objectStream.flush();
	}

	/**
	 * Loads reporters state
	 * 
	 * @see RunnerStatePersistencyManager
	 */
	private void deSerializeReporters(InputStream stream) throws Exception {
		ObjectInputStream objectStream = new ObjectInputStream(stream);
		String version = (String) objectStream.readObject();
		if (!UpgradeAndBackwardCompatibility.currentVersion().equals(version)) {
			throw new Exception("Incompatible reporters serialization. Current runner version "
					+ UpgradeAndBackwardCompatibility.currentVersion() + " deserialized reporters version: " + version);
		}
		setSilent((Boolean) objectStream.readObject());
		startTime = (Long) objectStream.readObject();
		lastTestFail = (Boolean) objectStream.readObject();
		testIndex = (RepeatTestIndex) objectStream.readObject();
		testClassName = (String) objectStream.readObject();
		inTest = false;
		blockReporters = (Boolean) objectStream.readObject();
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof Externalizable) {
				listeners.remove(i);
				listeners.add(i, objectStream.readObject());
			}
		}
	}

	/**
	 * 
	 */
	private void loadReporter(String className) throws ClassNotFoundException, IllegalAccessException,
			InstantiationException {
		Class<?> reporterClass = LoadersManager.getInstance().getLoader().loadClass(className);
		Object currentObject = reporterClass.newInstance();
		addListener(currentObject);
	}

	public synchronized void addError(Test test, Throwable t) {
		setLastTestFail(true);
		setFailToPass(false);
		setFailToWarning(false);
		try {
			isError = true;
			if (!inKnownIssueState()) {
				report("Fail: " + t.getMessage(), t);
				for (int i = 0; i < listeners.size(); i++) {
					Object currentObject = listeners.get(i);
					if (currentObject instanceof TestListener) {
						TestListener tl = (TestListener) currentObject;
						try {
							tl.addError(test, t);
						} catch (Throwable ex) {
							log.error("Fail to add error to testlistener", ex);
						}
					}
				}
			} else {
				report("Fail: " + t.getMessage(), StringUtils.getStackTrace(t), Reporter.WARNING);
			}
			reportOnStack(StringUtils.getStackTrace(t), "error");
		} finally {
			isError = false;
		}
	}

	public synchronized void addError(Test test, String message, String stack) {
		setLastTestFail(true);
		setFailToPass(false);
		setFailToWarning(false);
		try {
			isError = true;
			if (!inKnownIssueState()) {
				report("Fail: " + message, stack, !getLastTestFailed());
				for (int i = 0; i < listeners.size(); i++) {
					Object currentObject = listeners.get(i);
					if (currentObject instanceof TestListener) {
						TestListener tl = (TestListener) currentObject;
						try {
							tl.addError(test, new Exception(message));
						} catch (Throwable ex) {
							log.error("Fail to add error to testlistener", ex);
						}
					}
				}
			} else {
				report("Fail: " + message, stack, Reporter.WARNING);
			}
			reportOnStack(stack, "error");
		} finally {
			isError = false;
		}
	}

	/**
	 * Take a stack trace of the exception error and extract the test line error
	 * and the system object line error. When used from eclipse will enable link
	 * to the code of the test and system object.
	 * 
	 * @param stack
	 *            a string contain the stack trace
	 * @param type
	 *            the type of the exception: fail/error
	 */
	private void reportOnStack(String stack, String type) {
		String testLine = StackTraceUtil.findTheFirstOfType(stack, SystemTestCase.class);
		if (testLine != null) {
			System.err.println("Test " + type + " line: " + testLine);
		}
		String soLine = StackTraceUtil.findTheFirstOfType(stack, SystemObjectImpl.class);
		if (soLine != null) {
			System.err.println("SystemObject " + type + " line: " + soLine);
		}

	}

	public synchronized void addFailure(Test test, AssertionFailedError t) {
		boolean negativeState = testMarkedNegativeTest;
		setLastTestFail(!inKnownIssueState() && !negativeState);
		setFailToPass(false);
		setFailToWarning(false);
		if (!(t instanceof AnalyzerException)) {
			// bug fix thanks to Jack Kuan from wuerth-phoenix
			String title = t.getMessage();
			if (title == null) {
				title = t.getClass().getName();
			}
			if (negativeState) {
				report(title, StringUtils.getStackTrace(t), Reporter.PASS);
			} else if (inKnownIssueState()) {
				report(title, StringUtils.getStackTrace(t), Reporter.WARNING);
			} else {
				report(title, t);
			}
		}
		if (!inKnownIssueState() && !negativeState) {
			for (int i = 0; i < listeners.size(); i++) {
				Object currentObject = listeners.get(i);
				if (currentObject instanceof TestListener) {
					TestListener tl = (TestListener) currentObject;
					try {
						tl.addFailure(test, t);
					} catch (Throwable ex) {
						log.error("Fail to add failure to testlistener", ex);
					}

				}
			}
		}
		reportOnStack(StringUtils.getStackTrace(t), "failure");

	}

	public synchronized void addFailure(Test test, String message, String stack, boolean analyzerException) {
		boolean negativeState = testMarkedNegativeTest;
		setLastTestFail(!inKnownIssueState() && !negativeState);
		setFailToPass(false);
		setFailToWarning(false);
		if (!analyzerException) {
			// bug fix thanks to Jack Kuan from wuerth-phoenix
			String title = message;
			if (title == null) {
				title = "Failure";
			}
			if (negativeState) {
				report(title, stack, Reporter.PASS);
			} else if (inKnownIssueState()) {
				report(title, stack, Reporter.WARNING);
			} else {
				report(title, stack, false);
			}
		}
		if (!inKnownIssueState() && !negativeState) {
			for (int i = 0; i < listeners.size(); i++) {
				Object currentObject = listeners.get(i);
				if (currentObject instanceof TestListener) {
					TestListener tl = (TestListener) currentObject;
					try {
						tl.addFailure(test, new AssertionFailedError(message));
					} catch (Throwable ex) {
						log.error("Fail to add failure to testlistener", ex);
					}

				}
			}
		}
		reportOnStack(stack, "failure");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * jsystem.framework.report.ExtendTestListener#addWarning(junit.framework
	 * .Test)
	 */
	public synchronized void addWarning(Test test) {
		hadWarning = true;
		if (isSilent()) {
			return;
		}
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof ExtendTestListener) {
				ExtendTestListener tl = (ExtendTestListener) currentObject;
				try {
					tl.addWarning(test);
				} catch (Throwable ex) {
					log.error("Fail to add warning to testlistener", ex);
				}
			}
		}

	}

	public synchronized void endTest(Test test) {
		if (test == null) {
			test = currentTest;
		}
		runningTests.removeElement(test);
		fireEndTest(test, blockReporters);
		updateCurrentTest(null);
		inTest = false;
		if (System.currentTimeMillis() - lastGC > 60000) {
			System.gc();
			lastGC = System.currentTimeMillis();
		}
		if (!blockReporters) {
			endTime = System.currentTimeMillis();
		}

		if ("true".equals(JSystemProperties.getInstance().getPreferenceOrDefault(
				FrameworkOptions.SAVE_REPORTERS_ON_RUN_END))) {
			int interval = Integer.parseInt(JSystemProperties.getInstance().getPreferenceOrDefault(
					FrameworkOptions.SAVE_REPORTERS_INTERVAL));
			if (interval > -1 && (System.currentTimeMillis() - lastFlashReportTime) > (interval * 1000)) {
				flushReporters();
				lastFlashReportTime = System.currentTimeMillis();
			}
		}

	}

	/**
	 * If test is marked as Negative, update status according to test results
	 * 
	 * @param test
	 */
	private void updateNegativeTest(Test test) {
		if (test == null) {
			return;
		}
		if (testMarkedNegativeTest) {
			// Test is marked as negative test
			RunnerTest runnerTest = ScenarioHelpers.getRunnerTest(test);
			if (runnerTest.isPassAssumingFlags()) {
				// Test Failed as expected - report success
				step("Negative test - Test failed as expected");
				if (!runnerTest.isWarning()) {
					// warning should not change to pass (support for known
					// issue tests)
					runnerTest.setStatus(RunnerTest.STAT_SUCCESS);
				}
			} else if (!runnerTest.isError()) {
				// Test passed but should have failed - report error
				report(CommonResources.NEGATIVE_TEST_STRING, false);
				runnerTest.setStatus(RunnerTest.STAT_FAIL);
			}
		}
	}

	private void fireEndTest(Test test, boolean maskReporters) {
		updateNegativeTest(test);
		// First notify non reporters
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof TestListener && !(currentObject instanceof TestReporter)) {
				TestListener tl = (TestListener) currentObject;
				try {
					tl.endTest(test);
				} catch (Throwable ex) {
					if (test == null) {
						continue;
					}
					log.error("Fail to add endTest", ex);
				}
			}
		}
		if (inTest) {
			// then notify reporters
			for (int i = 0; i < listeners.size(); i++) {
				Object currentObject = listeners.get(i);
				if (currentObject instanceof TestListener && (currentObject instanceof TestReporter) && !maskReporters) {
					TestListener tl = (TestListener) currentObject;
					try {
						tl.endTest(test);
					} catch (Throwable ex) {
						log.error("Fail to add endTest", ex);
					}
				}
			}
		}
	}

	public void startTest(TestInfo testInfo) {
		// not implemented use startTest(Test) ...
	}

	public synchronized void startTest(Test test) {
		failToPass = false;
		failToWarning = false;

		/*
		 * See that no reports are buffered
		 */
		stopBufferingReports();
		clearReportsBuffer();

		if (inTest) {
			endTest(currentTest);
		}
		if (test instanceof SystemTest) {
			((SystemTest) test).clearDocumentation();
			((SystemTest) test).clearFailCause();
			((SystemTest) test).clearSteps();
			((SystemTest) test).initFlags();
		
		}
		
		// if test is ran not from the runner\ANT (eclipse for example), the
		// flags are currently not supported
		if (!JSystemProperties.getInstance().isExecutedFromIDE() && test instanceof NamedTest) {
			Properties properties = ScenarioHelpers.getAllTestPropertiesFromAllScenarios(
					((NamedTest) test).getFullUUID(), false);
			String testMarkedAsKnownIssueStr = properties.getProperty(RunningProperties.MARKED_AS_KNOWN_ISSUE);
			String testMarkedAsNegativeTestStr = properties.getProperty(RunningProperties.MARKED_AS_NEGATIVE_TEST);
			testMarkedAsKnownIssue = testMarkedAsKnownIssueStr != null
					&& Boolean.parseBoolean(testMarkedAsKnownIssueStr);
			testMarkedNegativeTest = testMarkedAsNegativeTestStr != null
					&& Boolean.parseBoolean(testMarkedAsNegativeTestStr);
		}
		runningTests.addElement(test);
		testsCount++;
		setSilent(false);
		setLastTestFail(false);
		TName tname = TestNameServer.getInstance().getTestName(test);
		int count = testIndex.getTestIndex(tname.getFullName());
		updateCurrentTest(test);
		RunnerTest rt = null;
		/*
		 * Init the meaningful name from the RunnerTest will be done only when
		 * executed from the runner\ANT
		 */
		String meaningfulName = null;
		String uuid = "";
		if (!JSystemProperties.getInstance().isExecutedFromIDE()) {
			//We probably run from ANT
			rt = ScenariosManager.getInstance().getCurrentScenario().findRunnerTest(test);
			parser.startTest(rt);
			if (rt != null) {
				meaningfulName = rt.getMeaningfulName();
				uuid = rt.getFullUUID();
			}
		} 
		else if (test instanceof NamedTest) {
			//We run from IDE
			uuid = ((NamedTest) test).getFullUUID();
			if (!StringUtils.isEmpty(uuid)) {
				//I think that we never get to this block because when we run from IDE we never get UUID.
				try {
					rt = (RunnerTest) ScenarioHelpers.getTestById(ScenariosManager.getInstance().getCurrentScenario(),
							uuid);
					if (rt != null) {
						parser.startTest(rt);
						meaningfulName = rt.getMeaningfulName();
					}
				} catch (Exception e) {
					log.debug("Failed getting RunnerTest", e);
				}
			}
		}

		TestInfo ti = new TestInfo();
		ti.className = tname.getClassName();
		ti.methodName = tname.getMethodName();
		ti.meaningfulName = meaningfulName;
		ti.comment = tname.getComment();
		ti.parameters = tname.getParamsString();
		ti.userDoc = tname.getUserDocumentation();
		ti.count = count;
		ti.fullUuid = uuid;
		if (rt != null) {
			ti.isHiddenInHTML = rt.isHiddenInHTML();
		}
		fireTestStart(ti, test, false);
		if (!blockReporters) {
			startTime = System.currentTimeMillis();
		}
		testClassName = test.getClass().getName();

		inTest = true;
	}

	private void fireTestStart(TestInfo testInfo, Test test, boolean maskReporters) {
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);

			if (currentObject instanceof ExtendTestListener) {
				if (!blockReporters) {
					ExtendTestListener tl = (ExtendTestListener) currentObject;
					try {
						tl.startTest(testInfo);
					} catch (Throwable ex) {
						log.error("Fail to startTest", ex);
					}
				}
			} else {
				if (currentObject instanceof TestListener) {
					if (currentObject instanceof TestReporter && maskReporters) {
						continue;
					}
					TestListener tl = (TestListener) currentObject;
					try {
						if (test != null) {
							tl.startTest(test);
						}
					} catch (Throwable ex) {
						log.error("Fail to startTest", ex);
					}
				}
			}
		}
	}

	public synchronized void aboutToChangeTo(Fixture fixture) {
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof FixtureListener) {
				FixtureListener fl = (FixtureListener) currentObject;
				try {
					fl.aboutToChangeTo(fixture);
				} catch (Throwable ex) {
					log.error("Fail to file aboutToChangeTo", ex);
				}
			}
		}
	}

	public synchronized void fixtureChanged(Fixture fixture) {
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof FixtureListener) {
				FixtureListener fl = (FixtureListener) currentObject;
				try {
					fl.fixtureChanged(fixture);
				} catch (Throwable ex) {
					log.error("Fail to file fixtureChanged", ex);
				}
			}
		}
	}

	public synchronized void startFixturring() {
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof FixtureListener) {
				FixtureListener fl = (FixtureListener) currentObject;
				try {
					fl.startFixturring();
				} catch (Throwable ex) {
					log.error("Fail to file startFixturring", ex);
				}
			}
		}
	}

	public synchronized void endFixturring() {
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof FixtureListener) {
				FixtureListener fl = (FixtureListener) currentObject;
				try {
					fl.endFixturring();
				} catch (Throwable ex) {
					log.error("Fail to file endFixturring", ex);
				}
			}
		}
	}

	public ArrayList<TestReporter> getAllReporters() {
		ArrayList<TestReporter> array = new ArrayList<TestReporter>();
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof TestReporter) {
				array.add((TestReporter) currentObject);
			}
		}
		return array;
	}

	@Override
	public void initReporters() {
		log.debug("RunnerListenersManager - initReporters ");
		parser.init();
		testIndex = new RepeatTestIndex();
		ArrayList<TestReporter> array = getAllReporters();
		for (int i = 0; i < array.size(); i++) {
			log.debug("RunnerListenersManager - initiating " + array.get(i));
			array.get(i).init();
		}
		testsCount = 0;
		RunnerStatePersistencyManager.getInstance().setLoadReporters(false);
	}

	public void setSilent(boolean status) {
		this.silent = status;
	}

	public boolean isSilent() {
		return silent;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see jsystem.framework.report.ExtendReporter#reportHtml(java.lang.String,
	 * java.lang.String, boolean)
	 */
	public synchronized void startSection() {
		if (isSilent()) {
			return;
		}
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof ExtendTestReporter) {
				try {
					((ExtendTestReporter) currentObject).startSection();
				} catch (Throwable ex) {
					log.error("Fail to startSection", ex);
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see jsystem.framework.report.ExtendReporter#reportHtml(java.lang.String,
	 * java.lang.String, boolean)
	 */
	public synchronized void endSection() {
		if (isSilent()) {
			return;
		}
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof ExtendTestReporter) {
				try {
					((ExtendTestReporter) currentObject).endSection();
				} catch (Throwable ex) {
					log.error("Fail to endSection", ex);
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see jsystem.framework.report.ExtendReporter#saveFile(java.lang.String,
	 * java.io.InputStream)
	 */
	public synchronized void saveFile(String fileName, byte[] content) {
		if (isSilent()) {
			return;
		}
		try {

			File file = new File(getCurrentTestFolder(), fileName);
			file.getParentFile().mkdirs();
			FileOutputStream out = new FileOutputStream(file);
			out.write(content);
			out.close();
		} catch (IOException e) {
			log.warn("Fail to save file", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see jsystem.framework.report.ExtendReporter#report(java.lang.String,
	 * java.lang.String, int, boolean)
	 */
	public synchronized void setData(String data) {
		if (isSilent()) {
			return;
		}
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof ExtendTestReporter) {
				try {
					((ExtendTestReporter) currentObject).setData(data);
				} catch (Throwable ex) {
					log.error("Fail to setData", ex);
				}
			}
		}
	}

	public synchronized void sutChanged(String sutName) {
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof SutListener) {
				try {
					((SutListener) currentObject).sutChanged(sutName);
				} catch (Throwable ex) {
					log.error("Fail to sutChange", ex);
				}
			}
		}
	}

	public synchronized void scenarioChanged(Scenario current, ScenarioChangeType changeType) {
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof ScenarioListener) {
				try {
					((ScenarioListener) currentObject).scenarioChanged(current, changeType);
				} catch (Throwable ex) {
					log.error("Fail to scenarioChanged", ex);
				}
			}
		}
	}

	public synchronized void scenarioDirectoryChanged(File directory) {
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof ScenarioListener) {
				try {
					((ScenarioListener) currentObject).scenarioDirectoryChanged(directory);
				} catch (Throwable ex) {
					log.error("Fail to scenarioDirectoryChanged", ex);
				}
			}
		}
	}

	public synchronized void startReport(String name, String parameters, String classDoc, String testDoc) {
		if (isSilent()) {
			return;
		}
		if (inTest) {
			endReport();
		}
		SystemTestCase stc = new InternalTest();
		stc.setName(name);
		setLastTestFail(false);
		stc.setPass(true);
		updateCurrentTest(stc);
		testsCount++;
		setSilent(false);

		TName tname = TestNameServer.getInstance().getTestName(stc);
		tname.setClassName(testClassName);
		tname.setParamsString(parameters);
		int count = testIndex.getTestIndex(tname.getFullName());

		TestInfo ti = new TestInfo();
		ti.className = tname.getClassName();
		ti.methodName = name;
		ti.meaningfulName = null;
		ti.comment = tname.getComment();
		ti.parameters = parameters;
		ti.count = count;
		ti.fullUuid = "";
		ti.classDoc = classDoc;
		ti.testDoc = testDoc;

		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if ((currentObject instanceof ExtendTestListener) && (currentObject instanceof TestReporter)) {
				((ExtendTestListener) currentObject).startTest(ti);
			}
		}

		inTest = true;
		startTime = System.currentTimeMillis();

	}

	public synchronized void endReport(String steps, String failCause) {
		if (isSilent()) {
			return;
		}
		if (steps != null) {
			((SystemTest) currentTest).addExecutedSteps(steps);
		}
		if (failCause != null) {
			((SystemTest) currentTest).addFailCause(failCause);
		}
		if (inTest) {
			for (int i = 0; i < listeners.size(); i++) {
				Object currentObject = listeners.get(i);
				if ((currentObject instanceof ExtendTestListener) && (currentObject instanceof TestReporter)) {
					try {
						((ExtendTestListener) currentObject).endTest(currentTest);
					} catch (Throwable ex) {
						log.error("Fail to endReport", ex);
					}
				}
			}
		}
		inTest = false;
	}

	public synchronized void endRun() {
		blockReporters = false;
		parser.closeAllContainers();
		ArrayList<Object> ll = new ArrayList<Object>(listeners);
		for (int i = 0; i < ll.size(); i++) {
			Object currentObject = ll.get(i);
			if (currentObject instanceof ExtendTestListener) {
				try {
					((ExtendTestListener) currentObject).endRun();
				} catch (Throwable ex) {
					log.error("Fail to endRun", ex);
				}
			}
		}
		flushReporters();
		if (JSystemProperties.getInstance().isExitOnRunEnd()) {
			System.exit(0);
		}
	}

	public void startLevel(String level, int place) throws IOException {
		if (isSilent()) {
			return;
		}
		if (buffering) {
			if (reportsBuffer == null) {
				reportsBuffer = new ArrayList<ReportElement>();
			}
			ReportElement re = new ReportElement();
			re.setTitle(level);
			re.setOriginator(Thread.currentThread().getName());
			re.setTime(System.currentTimeMillis());
			re.setStartLevel(true);
			re.setLevelPlace(place);
			reportsBuffer.add(re);
			return;
		}
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof ExtendLevelTestReporter) {
				try {
					((ExtendLevelTestReporter) currentObject).startLevel(level, place);
				} catch (Throwable ex) {
					log.error("Fail to report", ex);
				}
			}
		}

	}

	public void stopLevel() throws IOException {
		if (isSilent()) {
			return;
		}
		if (buffering) {
			if (reportsBuffer == null) {
				reportsBuffer = new ArrayList<ReportElement>();
			}
			ReportElement currentReportElement = new ReportElement();
			currentReportElement.setOriginator(Thread.currentThread().getName());
			currentReportElement.setTime(System.currentTimeMillis());
			currentReportElement.setStopLevel(true);
			reportsBuffer.add(currentReportElement);
			return;
		}
		for (int index = 0; index < listeners.size(); index++) {
			Object currentObject = listeners.get(index);
			if (currentObject instanceof ExtendLevelTestReporter) {
				try {
					((ExtendLevelTestReporter) currentObject).stopLevel();
				} catch (Throwable ex) {
					log.error("Fail to report", ex);
				}
			}
		}
	}

	@Override
	public synchronized void report(String title, String message, int status, boolean bold, boolean html, boolean step,
			boolean link) {
		if (isSilent()) {
			return;
		}

		if (buffering && !step) {
			if (reportsBuffer == null) {
				reportsBuffer = new ArrayList<ReportElement>();
			}
			ReportElement newReportElementInstance = new ReportElement();
			newReportElementInstance.setTitle(title);
			newReportElementInstance.setMessage(message);
			newReportElementInstance.setStatus(status);
			newReportElementInstance.setBold(bold);
			newReportElementInstance.setHtml(html);
			newReportElementInstance.setStep(step);
			newReportElementInstance.setLink(link);
			newReportElementInstance.setOriginator(Thread.currentThread().getName());
			newReportElementInstance.setTime(System.currentTimeMillis());
			reportsBuffer.add(newReportElementInstance);
			if (!printBufferdReportsInRunTime) {
				return;
			}
		}

		if (failToPass) {
			status = Reporter.PASS;
		}
		if (status == Reporter.FAIL && failToWarning) {
			status = Reporter.WARNING;
		}

		if (testMarkedNegativeTest && !title.equals(CommonResources.NEGATIVE_TEST_STRING) && status == Reporter.FAIL) {
			if (currentTest != null) {
				ScenarioHelpers.getRunnerTest(currentTest).setFailureOccurred(true);
			}
			if (!isError) {
				status = Reporter.PASS;
			}
		}

		if (inKnownIssueState() && status == Reporter.FAIL) {
			status = Reporter.WARNING;
		}

		if (status == Reporter.FAIL) {
			setLastTestFail(true);
			if (systemTest != null) {
				systemTest.addFailCause(title);
				systemTest.setPass(false);
			}
		}
		if (message != null && message.equals("null")) {
			message = null;
		}
		if (status == Reporter.WARNING) {
			if (currentTest != null) {
				addWarning(currentTest);
			}
			if (systemTest != null) {
				systemTest.addFailCause(title);
			}
		}
		if (link) {
			for (int i = 0; i < listeners.size(); i++) {
				Object currentObject = listeners.get(i);
				if (currentObject instanceof ExtendTestReporter) {
					try {
						((ExtendTestReporter) currentObject).report(title, message, status, false, false, true);
					} catch (Throwable ex) {
						log.error("Fail to addLink", ex);
					}
				} else if (currentObject instanceof TestReporter) {
					try {
						((TestReporter) currentObject).report(title, message, status, bold);
					} catch (Throwable ex) {
						log.error("Fail to addLink", ex);
					}
				}
			}
		} else if (html) {
			for (int i = 0; i < listeners.size(); i++) {
				Object currentObject = listeners.get(i);
				if (currentObject instanceof ExtendTestReporter) {
					try {
						((ExtendTestReporter) currentObject).report(title, message, status, false, true, false);
					} catch (Throwable ex) {
						log.error("Fail to reportHtml", ex);
					}
				} else if (currentObject instanceof TestReporter) {
					try {
						((TestReporter) currentObject).report(title, message, status, bold);
					} catch (Throwable ex) {
						log.error("Fail to report", ex);
					}
				}
			}
		} else if (step) {
			report(title, null, Reporter.PASS, true, false, false, false);
			if (currentTest != null && currentTest instanceof SystemTest) {
				((SystemTest) currentTest).addExecutedSteps(title);
			}
		} else {
			for (int i = 0; i < listeners.size(); i++) {
				Object currentObject = listeners.get(i);
				if (currentObject instanceof ExtendTestReporter) {
					try {
						((ExtendTestReporter) currentObject).report(title, message, status, bold, false, false);
					} catch (Throwable ex) {
						log.error("Fail to report", ex);
					}
				} else if (currentObject instanceof TestReporter) {
					try {
						((TestReporter) currentObject).report(title, message, status, bold);
					} catch (Throwable ex) {
						log.error("Fail to report", ex);
					}
				}
			}
		}
	}

	public void blockReporters(boolean block) {
		this.blockReporters = block;
	}

	public boolean getLastTestFailed() {
		return lastTestFail;
	}

	public boolean isPause() {
		return false;
	}

	public void addProperty(String key, String value) {
		String property = key + "=" + value;
		String title = "Added Property: " + property;

		if (StringUtils.hasNotAllowedSpecialCharacters(key) || StringUtils.hasNotAllowedSpecialCharacters(value)) {
			String title2 = "Warning: found unallowed characters from \"" + StringUtils.notAllowedCharacters + "\""
					+ " in property: " + property;
			report(title2, false);
			return;
		}
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof ExtendTestReporter) {
				try {
					((ExtendTestReporter) currentObject).addProperty(key, value);
				} catch (Throwable ex) {
					log.error("Fail to report", ex);
				}
			} else if (currentObject instanceof TestReporter) {
				try {
					((TestReporter) currentObject).report(title, null, true, false);
				} catch (Throwable ex) {
					log.error("Fail to report", ex);
				}
			}

		}

	}

	public int showConfirmDialog(String title, String message, int optionType, int messageType) {
		int result = 0;
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof InteractiveReporter) {
				InteractiveReporter fl = (InteractiveReporter) currentObject;
				try {
					result = fl.showConfirmDialog(title, message, optionType, messageType);
				} catch (Throwable ex) {
					log.error("Fail to run executionEnded", ex);
				}
			}
		}
		return result;
	}

	public void startLevel(String level, EnumReportLevel place) throws IOException {
		startLevel(level, place.value());
	}

	public void executionEnded(String scenarioName) {
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof ExecutionListener) {
				ExecutionListener fl = (ExecutionListener) currentObject;
				try {
					fl.executionEnded(scenarioName);
				} catch (Throwable ex) {
					log.error("Fail to run executionEnded", ex);
				}
			}
		}
	}

	public void remoteExit() {
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof ExecutionListener) {
				ExecutionListener fl = (ExecutionListener) currentObject;
				try {
					fl.remoteExit();
				} catch (Throwable ex) {
					log.error("Fail to run remote exit", ex);
				}
			}
		}
	}

	public void errorOccured(String title, String message, ErrorLevel level) {
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof ExecutionListener) {
				ExecutionListener fl = (ExecutionListener) currentObject;
				try {
					fl.errorOccured(title, message, level);
				} catch (Throwable ex) {
					log.error("Fail to run errorOccured", ex);
				}
			}
		}
	}

	public void remotePause() {
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof ExecutionListener) {
				ExecutionListener fl = (ExecutionListener) currentObject;
				try {
					fl.remotePause();
				} catch (Throwable ex) {
					log.error("Fail to run remote pause", ex);
				}
			}
		}
	}

	private void setLastTestFail(boolean status) {
		lastTestFail = status;
		if (status) {
			hadFailure = true;
		}
	}

	private boolean inKnownIssueState() {
		return testMarkedAsKnownIssue;
	}

	@Override
	public void startContainer(JTestContainer container) {
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof ExtendTestListener) {
				ExtendTestListener tl = (ExtendTestListener) currentObject;
				try {
					tl.startContainer(container);
				} catch (Throwable ex) {
					log.error("Fail to send startContainer notification to testlistener", ex);
				}
			}
		}
	}

	@Override
	public void endContainer(JTestContainer container) {
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof ExtendTestListener) {
				ExtendTestListener tl = (ExtendTestListener) currentObject;
				try {
					tl.endContainer(container);
				} catch (Throwable ex) {
					log.error("Fail to send endContainer notification to testlistener", ex);
				}
			}
		}
		setLastTestFail(false);
	}

	@Override
	public void startLoop(AntForLoop loop, int count) {
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof ExtendTestListener) {
				ExtendTestListener tl = (ExtendTestListener) currentObject;
				try {
					tl.startLoop(loop, count);
				} catch (Throwable ex) {
					log.error("Fail to send startLoop notification to testlistener", ex);
				}
			}
		}
	}

	@Override
	public void endLoop(AntForLoop loop, int count) {
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof ExtendTestListener) {
				ExtendTestListener tl = (ExtendTestListener) currentObject;
				try {
					tl.endLoop(loop, count);
				} catch (Throwable ex) {
					log.error("Fail to send endLoop notification to test listener", ex);
				}
			}
		}
	}

	@Override
	public void closeAllLevels() throws IOException {
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof ExtendLevelTestReporter) {
				try {
					((ExtendLevelTestReporter) currentObject).closeAllLevels();
				} catch (Throwable ex) {
					log.error("Fail to close all levels", ex);
				}
			}
		}
	}

	@Override
	public void scenarioDirtyStateChanged(Scenario s, boolean isDirty) {
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof ScenarioListener) {
				try {
					((ScenarioListener) currentObject).scenarioDirtyStateChanged(s, isDirty);
				} catch (Throwable ex) {
					log.error("Fail to scenarioDirtyStateChanged", ex);
				}
			}
		}
	}

	@Override
	public void testParametersChanged(String testIIUUD, Parameter[] oldValues, Parameter[] newValues) {
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof ScenarioListener) {
				try {
					((ScenarioListener) currentObject).testParametersChanged(testIIUUD, oldValues, newValues);
				} catch (Throwable ex) {
					log.error("Fail to testParametersChanged", ex);
				}
			}
		}

	}

	@Override
	public void flushReporters() {
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof ExtendTestReporter) {
				try {
					((ExtendTestReporter) currentObject).flush();
				} catch (Throwable ex) {
					log.error("Fail to flush reporter", ex);
				}
			}
		}
	}

	@Override
	public void setContainerProperties(int ancestorLevel, String key, String value) {
		for (int i = 0; i < listeners.size(); i++) {
			Object currentObject = listeners.get(i);
			if (currentObject instanceof ExtendTestReporter) {
				try {
					((ExtendTestReporter) currentObject).setContainerProperties(ancestorLevel, key, value);
				} catch (Throwable ex) {
					log.error("Fail to report", ex);
				}
			}
		}
	}
}

class InternalTest extends SystemTestCase {
	// used as the base for all internal tests
}
