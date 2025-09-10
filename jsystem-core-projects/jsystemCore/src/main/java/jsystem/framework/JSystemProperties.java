/*
 * Copyright 2005-2010 Ignis Software Tools Ltd. All rights reserved.
 */
package jsystem.framework;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.StringTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import jsystem.framework.common.CommonResources;
import jsystem.framework.scenario.RunningProperties;
import jsystem.utils.FileUtils;
import jsystem.utils.StringUtils;

/**
 * JSystemsProperties takes care for JSystem properties. JSystemProperties is
 * implemented as singleton so to instance you get from
 * JSystremProperties.getInstance()
 */
public class JSystemProperties {

	private static Logger log = LoggerFactory.getLogger(JSystemProperties.class);

	private static JSystemProperties jSystemProperties = null;

	/**
	 * If true this VM is the reporter VM.
	 */
	private boolean isReporterVm = true;

	/**
	 * If true this VM is the JSystem runner VM.
	 */
	private boolean isJsystemRunner = false;

	Properties cachedProperties;

	/**
	 * Return instance of JSystemProperties.
	 * 
	 * @param isReportVm
	 *            set to true if the reporter is in the current VM.
	 * @return The properties instance.
	 */
	public static JSystemProperties getInstance(boolean isReportVm) {
		if (jSystemProperties == null) {
			jSystemProperties = new JSystemProperties(isReportVm);
			log.debug("JSystem instance init");
		}
		return jSystemProperties;
	}

	/**
	 * Return instance of JSystemProperties.
	 */
	public static JSystemProperties getInstance() {
		return getInstance(true);
	}

	private JSystemProperties(boolean isReportVm) {
		getPreferencesFile();
		loadPropertiesToCache();
		setIsReporterVm(isReportVm);
		initSLF4JBridge();
		readPreferences();
	}

	private File settingFile = null;

	private boolean exitOnRunEnd = false;

	/**
	 * This function return the jsystem_properties file
	 * 
	 * @return
	 */
	public synchronized File getPreferencesFile() {
		if (settingFile == null) {
			String home = System.getProperty("user.dir");
			settingFile = new File(home, CommonResources.JSYSTEM_PROPERTIES_FILE_NAME);
			log.debug("Created new JSystem properties file.");
			if (!settingFile.exists()) {
				if (!restoreFromBackup()) {
					createJsystemPropertiesFile();
				}
			}
		}
		return settingFile;
	}

	/**
	 * 1) delete properties file<br>
	 * 2) clear properties from static member<br>
	 * 3) create properties file
	 */
	public synchronized void clearAndResetJsystemPropertiesFile() {
		int retry = 0;
		while (settingFile.exists() && retry < 6) {
			System.gc(); // releasing all input\output stream to that file
			FileUtils.deleteFile(settingFile.getName());
			log.debug("deleted jsystem properties file.");
			System.out.println("retry " + retry);
			retry++;
		}
		if (!settingFile.exists()) {
			createJsystemPropertiesFile();
		}
		loadPropertiesToCache();
	}

	/**
	 * check if there is a base file, copy base file to JSystem properties, if
	 * exists
	 */
	private void createJsystemPropertiesFile() {
		String home = System.getProperty("user.dir");
		File baseFile = new File(home, CommonResources.JSYSTEM_BASE_FILE);
		try {
			settingFile.createNewFile();
		} catch (IOException e1) {
			log.error("Problem creating new properties file!");
		}
		if (baseFile.exists()) {
			try {
				FileUtils.copyFile(baseFile, settingFile);
				log.debug("Base file copied to jsystem properties file.");
			} catch (IOException e) {
				log.warn("Couldn't copy base file!");
			}
		}
	}

	/**
	 * Will reread the properties file It use when editing the properties file
	 * and use refresh button.
	 * 
	 */
	public void rereadPropertiesFile() {
		loadPropertiesToCache();
	}

	private synchronized Properties readPreferences() {
		// int tryNum = 0;
		// Exception exception = null;
		// Properties fPreferences = null;
		// while (tryNum < retries){
		// tryNum++;
		// try {
		// fPreferences =
		// FileUtils.loadPropertiesFromFile(getPreferencesFile().getAbsolutePath());
		// if (fPreferences.size() == 0 ){
		// restoreFromBackup();
		// continue;
		// }
		// } catch (IOException e) {
		//
		// if (e instanceof FileNotFoundException){
		// restoreFromBackup();
		// }else{
		// log.log(Level.FINE,"Problem reading jsystem.properties file, try " +
		// tryNum, e);
		// }
		// exception = e;
		// try {
		// Thread.sleep(100);
		// } catch (InterruptedException e1) {
		// log.fine("Thread sleep was interrupted");
		// }
		// }
		// }
		// if (fPreferences == null){
		// throw new RuntimeException(exception);
		// }
		// return fPreferences;
		if (cachedProperties == null) {
			loadPropertiesToCache();
		}
		return cachedProperties;
	}

	/**
	 * it is recommended use getPreference(FrameworkOptions option)
	 * 
	 * @return value assigned to key
	 */
	public synchronized String getPreference(String key) {
		if (System.getProperty(key) != null) {
			return System.getProperty(key);
		}
		Properties fPreferences = readPreferences();
		String property = fPreferences.getProperty(key);
		if (property != null) {
			return property.trim();
		} else {
			return null;
		}
	}

	/**
	 * Get specific system preferences see FrameworkOptions for possible
	 * framework option.
	 * 
	 * @param option
	 *            the option to get
	 * @return the property value (can be null if not set to the properties
	 *         file)
	 */
	public String getPreference(FrameworkOptions option) {
		return getPreference(option.toString());
	}

	/**
	 */
	public String getPreferenceOrDefault(FrameworkOptions option) {
		String val = getPreference(option);
		if (StringUtils.isEmpty(val)) {
			return option.getDefaultValue().toString();
		}
		return val;
	}

	public long getLongPreference(FrameworkOptions option, long defaultVal) {
		return getLongPreference(option.toString(), defaultVal);
	}

	public long getLongPreference(String key, long defaultVal) {
		String val = JSystemProperties.getInstance().getPreference(key);
		if (val == null) {
			return defaultVal;
		}
		try {
			return Long.parseLong(val);
		} catch (Exception e) {
			return defaultVal;
		}
	}

	/**
	 * This function return a property object that build from the
	 * jsystem_properties file
	 * 
	 * @return
	 */
	public Properties getPreferences() {
		return readPreferences();
	}

	/**
	 * Store JSystem preferences to file
	 */
	private synchronized void savePreferences(Properties fPreferences) {
		try {
			if (fPreferences.size() > 0) { // avoiding clearing of JSystem
											// properties
				FileUtils.saveSortedPropertiesToFile(fPreferences, getPreferencesFile().getAbsolutePath(), false);
				backupProperties();
			}
		} catch (IOException e) {
			log.warn("Unable to save to properties file (could be write protected)");
		}
	}

	/**
	 * Assign key to value and store it to JSystem preferences it is recommended
	 * to use setPreference(FrameworkOptions option, String value)
	 */
	public synchronized void setPreference(String key, String value) {
		Properties fPreferences = readPreferences();
		if (value == null) {
			throw new RuntimeException("Can't set null value to Jsystem Property \"" + key + "\"");
		}
		fPreferences.setProperty(key, value);
		savePreferences(fPreferences);
	}

	/**
	 * Assign key to value and store it to JSystem preferences
	 * 
	 * @param option
	 *            the option
	 * @param value
	 *            the value
	 */
	public void setPreference(FrameworkOptions option, String value) {
		setPreference(option.toString(), value);
	}

	/**
	 * Set preferences to JSystem preferences.
	 */
	public void setPreferences(Properties preferences) {
		savePreferences(preferences);
	}

	private void initSLF4JBridge() {
		String loggerStatus = getPreference(FrameworkOptions.LOGGER_STATUS);
		if (loggerStatus == null) { // the logger was never init
			setPreference("logger", "true");
			// SLF4J configuration is now handled by logback.xml
		}
		
		if (loggerStatus == null || loggerStatus.equals("true") || loggerStatus.equals("enable")) {
			// Install SLF4J bridge to capture JUL logs from third-party libraries
			if (!SLF4JBridgeHandler.isInstalled()) {
				SLF4JBridgeHandler.removeHandlersForRootLogger();
				SLF4JBridgeHandler.install();
			}
			log.info("SLF4J logging bridge initialized");
		} else {
			log.info("Logger was set to off");
			// Note: With SLF4J/Logback, logging levels are controlled via logback.xml
		}
	}

	/**
	 * Returns the tests CLASSES folder<br>
	 * <br>
	 * 
	 * if the path is invalid, Triggers the user to select a project directory
	 * If the user refuses to select a project directory the runner exists.
	 */
	public static String getCurrentTestsPath() {
		String path = JSystemProperties.getInstance().getPreference(FrameworkOptions.TESTS_CLASS_FOLDER);
		if (path != null) {
			File pathFile = new File(path);
			if (pathFile.exists()) {
				try {
					return pathFile.getCanonicalPath();
				} catch (IOException e) {
					return pathFile.getAbsolutePath();
				}
			}
		}

		if (!JSystemProperties.getInstance().isJsystemRunner()) {
			StringTokenizer st = new StringTokenizer(System.getProperty("java.class.path"),
					System.getProperty("path.separator"));
			while (st.hasMoreTokens()) {
				File f = new File(st.nextToken());
				if (f.isDirectory() && f.exists()) {
					path = f.getPath();
					JSystemProperties.getInstance().setPreference(FrameworkOptions.TESTS_CLASS_FOLDER, path);
					break;
				}
			}
		}

		if (path != null && getInstance().getPreference(FrameworkOptions.TESTS_SOURCE_FOLDER) == null) {
			File pathFile = new File(path);
			File testSrc = new File(pathFile.getParent(), "tests");
			if (testSrc.exists()) {
				// Ant project structure
				getInstance().setPreference(FrameworkOptions.TESTS_SOURCE_FOLDER, testSrc.getPath());
				getInstance().setPreference(FrameworkOptions.RESOURCES_SOURCE_FOLDER, testSrc.getPath());
			} else {
				// Maven project structure
				testSrc = new File(pathFile.getParentFile().getParentFile(), "src/main/java");
				getInstance().setPreference(FrameworkOptions.TESTS_SOURCE_FOLDER, testSrc.getPath());
				File resourcesSrc = new File(pathFile.getParentFile().getParentFile(), "src/main/resources");
				getInstance().setPreference(FrameworkOptions.RESOURCES_SOURCE_FOLDER, resourcesSrc.getPath());

			}
		}

		return path;
	}

	/**
	 * Finds the lib folder of the tests project
	 * 
	 * @return return the lib folder of the tests project or null if not exists
	 * @author Itai Agmon
	 */
	public static String getTestsLibFolder() {
		String testPath = getCurrentTestsPath();
		if (testPath == null) {
			return null;
		}
		File libFolder = new File(new File(testPath).getParentFile().getParentFile(), "lib");
		if (!libFolder.exists() || !libFolder.isDirectory()) {
			return null;
		}
		return libFolder.getAbsolutePath().toString();
	}

	public void removePreference(FrameworkOptions option) {
		removePreference(option.toString());
	}

	public synchronized void removePreference(String key) {
		Properties fPreferences = readPreferences();
		fPreferences.remove(key);
		savePreferences(fPreferences);
	}

	/**
	 * Create a backup file of the JSystem properties
	 * 
	 * @throws IOException
	 */
	private synchronized void backupProperties() {
		try {
			FileUtils.copyFile(getPreferencesFile(), getBackupFile());
		} catch (IOException e) {
			log.error("Problem backing up JSystem properties file");
		}
	}

	/**
	 * Restore JSystem properties from backup file
	 * 
	 * @throws IOException
	 */
	public synchronized boolean restoreFromBackup() {
		if (!isBackupValid()) {
			return false;
		}
		log.error("JSystem properties file was empty, restoring from backup.");
		File bu = getBackupFile();
		try {
			FileUtils.copyFile(bu, getPreferencesFile());
			return true;
		} catch (IOException e) {
			log.error("Problem restoring JSystem properties from backup");
			return false;
		}
	}

	/**
	 * @return the file used for back up of the JSystem properties
	 */
	private File getBackupFile() {
		String home = System.getProperty("user.dir");
		return new File(home, CommonResources.JSYSTEM_PROPERTIES_BACKUP_FILE_NAME);
	}

	/**
	 * Check if jsystem properties backup file exists and is not empty
	 * 
	 * @return True if it is valid
	 */
	private boolean isBackupValid() {
		File buFile = getBackupFile();
		if (!buFile.exists()) {
			return false;
		}
		try {
			if (FileUtils.loadPropertiesFromFile(buFile.getAbsolutePath()).size() > 0) {
				return true;
			}
		} catch (IOException e) {
			return false;
		}
		return false;
	}

	public void flushCacheToFile() {
		savePreferences(cachedProperties);
	}

	public void loadPropertiesToCache() {
		try {
			cachedProperties = FileUtils.loadPropertiesFromFile(getPreferencesFile().getAbsolutePath());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @return True - if called from the Reporter VM<br>
	 *         (for JRunner execution - True only if called from the JRunner VM,
	 *         <br>
	 *         for Ant\IDE execution - True always)
	 */
	public boolean isReporterVm() {
		return isReporterVm;
	}

	public void setIsReporterVm(boolean isReporterVm) {
		this.isReporterVm = isReporterVm;
	}

	/**
	 * @return True - only if called from the JRunner VM (from the test VM -
	 *         false)<br>
	 *         (Note: when executing from ANT or from an IDE will always return
	 *         false)
	 */
	public boolean isJsystemRunner() {
		return isJsystemRunner;
	}

	/**
	 * @param isJsystemRunner
	 *            if set to True then the JSystemProperties was init on the
	 *            JRuner JVM
	 */
	public void setJsystemRunner(boolean isJsystemRunner) {
		this.isJsystemRunner = isJsystemRunner;
	}

	public boolean isExitOnRunEnd() {
		return exitOnRunEnd;
	}

	public void setExitOnRunEnd(boolean exitOnRunEnd) {
		this.exitOnRunEnd = exitOnRunEnd;
	}

	/**
	 * Is the tests execution done from an IDE?
	 * 
	 * @return True - if execution is from an IDE.<br>
	 *         False - if it is ran from an ANT or from the JRunner
	 */
	public boolean isExecutedFromIDE() {
		if (!isJsystemRunner()) { // Test JVM
			return System.getProperty(RunningProperties.CURRENT_SCENARIO_NAME) == null;
		}
		return false; // JRunner JVM
	}

}
