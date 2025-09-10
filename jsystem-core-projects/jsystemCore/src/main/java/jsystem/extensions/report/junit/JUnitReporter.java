package jsystem.extensions.report.junit;

import java.io.File;
import java.io.IOException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

import jsystem.extensions.report.xml.XmlReporter;
import jsystem.framework.FrameworkOptions;
import jsystem.framework.JSystemProperties;
import jsystem.framework.report.ExtendTestListener;
import jsystem.framework.report.ExtendTestReporter;
import jsystem.framework.report.TestInfo;
import jsystem.framework.scenario.JTestContainer;
import jsystem.framework.scenario.flow_control.AntForLoop;
import jsystem.utils.FileUtils;
import jsystem.utils.StringUtils;
import junit.framework.AssertionFailedError;
import junit.framework.Test;

/**
 * This reporter mimic the JUnit Ant reporter behavior. The output of the
 * reporter is XML file that can be read from any IDE or CI system that
 * recognizes the Junit format. To use the reporter, add it to the
 * reporter.class Jsystem property.
 * 
 * @author Itai_Agmon
 * 
 */
public class JUnitReporter implements ExtendTestReporter, ExtendTestListener {
	static Logger log = LoggerFactory.getLogger(XmlReporter.class);

	private static final double MILL_TO_SEC = 1000;
	private final String logFileName = "TEST-JSystem_JUnit_report.xml";
	private TestSuite testSuite;
	private long testStart;
	private long testSuiteStart;

	public JUnitReporter() {
		init();
	}

	/**
	 * Initialize the model state.
	 */
	@Override
	public void init() {
		testSuite = new TestSuite();
		testSuite.setName("JSystem Reporter");
		testSuite.setTimeStamp(new Date().toString());
		String hostName = "Unknown";
		try {
			hostName = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			log.warn("Failed to get local host name");
		}
		testSuite.setHostName(hostName);
		testSuiteStart = System.currentTimeMillis();

	}

 	@Override
 	public void startTest(TestInfo testInfo) {
 		TestCase testCase = new TestCase();

		String methodName = testInfo.methodName;
	
		// if this field is null read basicName
		// to fix "Mark scenario as Test" bug #184
		if ( methodName == null ) 
			methodName = testInfo.basicName;

		testCase.setName(methodName);		
 		testCase.setClassName(testInfo.className);
 		testStart = System.currentTimeMillis();
 		testSuite.addTestCase(testCase);
 	}

	@Override
	public void addError(Test arg0, Throwable arg1) {
		testSuite.addError();
		TestCase testCase = testSuite.getLastTestCase();
		testCase.setError(buildFailureOrError(arg1));
		testCase.setTime(getTimeDeltaInSec(testStart));

	}

	@Override
	public void addFailure(Test arg0, AssertionFailedError arg1) {
		testSuite.addFailures();
		TestCase testCase = testSuite.getLastTestCase();
		testCase.setFailure(buildFailureOrError(arg1));
		testCase.setTime(getTimeDeltaInSec(testStart));

	}

	@Override
	public void endTest(Test arg0) {
		TestCase testCase = testSuite.getLastTestCase();
		if (null != testCase) {
			testCase.setTime(getTimeDeltaInSec(testStart));
		}
		toXml();
	}

	/**
	 * Parses throwable to failureOrError object.
	 * 
	 * @param throwable
	 *            The throwable to parse
	 * @return failureOrError
	 */
	private static FailureOrError buildFailureOrError(Throwable throwable) {
		FailureOrError failureOrError = new FailureOrError();
		if (throwable.getMessage() != null) {
			failureOrError.setMessage(throwable.getMessage());
		}
		failureOrError.setType(throwable.getClass().toString());
		failureOrError.setValue(StringUtils.getStackTrace(throwable));
		return failureOrError;
	}

	/**
	 * Exports the model to XML. The file will be copied to the JSystem root
	 * directory.
	 */
	public void toXml() {
		testSuite.setTime(getTimeDeltaInSec(testSuiteStart));
		try {
			XmlMapper xmlMapper = new XmlMapper();
			String xmlString = xmlMapper.writeValueAsString(testSuite);
			final String currentLogFolder = JSystemProperties.getInstance().getPreference(FrameworkOptions.LOG_FOLDER)
					+ File.separator + "current";
			FileUtils.write(currentLogFolder + File.separator + logFileName, xmlString);
		} catch (Exception e) {
			log.error("Failed to export report to XML", e);
		}
	}

	/**
	 * Get the delta between the given time and the current time in seconds
	 * 
	 * @param startTime
	 *            start time in milli seconds
	 * @return time delta
	 */
	private static float getTimeDeltaInSec(final long startTime) {
		return (float) ((System.currentTimeMillis() - startTime) / MILL_TO_SEC);
	}

	/** The tests model **/

	static class TestCase {

		private String name;

		private String className;

		private float time;

		private FailureOrError failure;

		private FailureOrError error;

		@JacksonXmlProperty(isAttribute = true)
		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		@JacksonXmlProperty(isAttribute = true, localName = "classname")
		public String getClassName() {
			return className;
		}

		public void setClassName(String className) {
			this.className = className;
		}

		@JacksonXmlProperty(isAttribute = true)
		public float getTime() {
			return time;
		}

		public void setTime(float time) {
			this.time = time;
		}

		@JsonProperty
		public FailureOrError getFailure() {
			return failure;
		}

		public void setFailure(FailureOrError failure) {
			this.failure = failure;
		}

		@JsonProperty
		public FailureOrError getError() {
			return error;
		}

		public void setError(FailureOrError error) {
			this.error = error;
		}

	}

	@JacksonXmlRootElement(localName = "testsuite")
	static class TestSuite {

		private int errors;

		private int failures;

		private String hostName;

		private String name;

		private float time;

		private String timeStamp;

		@JacksonXmlElementWrapper(useWrapping = false)
		@JsonProperty("testcase")
		protected List<TestCase> testCaseList = new ArrayList<TestCase>();

		public TestCase getLastTestCase() {
			if (testCaseList.size() == 0) {
				return null;
			}
			return testCaseList.get(testCaseList.size() - 1);

		}

		public void addFailures() {
			failures++;

		}

		public void addTestCase(TestCase testCase) {
			testCaseList.add(testCase);
		}

		@JacksonXmlProperty(isAttribute = true)
		public int getErrors() {
			return errors;
		}

		public void addError() {
			errors++;
		}

		@JacksonXmlProperty(isAttribute = true)
		public int getFailures() {
			return failures;
		}

		public void setFailures(int failures) {
			this.failures = failures;
		}

		@JacksonXmlProperty(isAttribute = true)
		public String getHostName() {
			return hostName;
		}

		public void setHostName(String hostName) {
			this.hostName = hostName;
		}

		@JacksonXmlProperty(isAttribute = true)
		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		@JacksonXmlProperty(isAttribute = true)
		public String getTimeStamp() {
			return timeStamp;
		}

		@JacksonXmlProperty(isAttribute = true)
		public float getTime() {
			return time;
		}

		public void setTime(float time) {
			this.time = time;
		}

		public void setTimeStamp(String timeStamp) {
			this.timeStamp = timeStamp;
		}

		@JacksonXmlProperty(isAttribute = true)
		public int getTests() {
			return testCaseList.size();
		}

	}

	static class FailureOrError {
		private String value;
		private String message;
		private String type;

		@JacksonXmlProperty(isAttribute = true)
		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		@JacksonXmlProperty(isAttribute = true)
		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		@JacksonXmlText
		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}

	}

	/**
	 * Warnings are not supported by JUnit, so we have nothing to do.
	 */
	@Override
	public void addWarning(Test test) {
	}

	/**
	 * Exports the model to XML and init the model.
	 */
	@Override
	public void endRun() {
		toXml();
		init();

	}

	/* Unused methods */

	/**
	 * This is the Junit start test, we are using the JSystem start test from
	 * the extend test listener.
	 */
	@Override
	public void startTest(Test test) {
	}

	@Override
	public void report(String title, String message, boolean isPass, boolean bold) {

	}

	@Override
	public void report(String title, String message, int status, boolean bold) {

	}

	@Override
	public void startLoop(AntForLoop loop, int count) {

	}

	@Override
	public void endLoop(AntForLoop loop, int count) {

	}

	@Override
	public void startContainer(JTestContainer container) {

	}

	@Override
	public void endContainer(JTestContainer container) {

	}

	@Override
	public void saveFile(String fileName, byte[] content) {

	}

	@Override
	public void report(String title, String message, int status, boolean bold, boolean html, boolean link) {

	}

	@Override
	public void startSection() {

	}

	@Override
	public void endSection() {

	}

	@Override
	public void setData(String data) {

	}

	@Override
	public void addProperty(String key, String value) {

	}

	@Override
	public void setContainerProperties(int ancestorLevel, String key, String value) {

	}

	@Override
	public void flush() throws Exception {
	}

	@Override
	public void initReporterManager() throws IOException {
	}

	@Override
	public boolean asUI() {
		return false;
	}

	@Override
	public String getName() {
		return null;
	}

}
