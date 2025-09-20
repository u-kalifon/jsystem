/*
 * Copyright 2005-2010 Ignis Software Tools Ltd. All rights reserved.
 */
package jsystem.extensions.report.html;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jsystem.framework.FrameworkOptions;
import jsystem.framework.JSystemProperties;
import jsystem.runner.loader.LoadersManager;
import jsystem.utils.FileUtils;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.JavadocComment;
import java.util.Optional;

/**
 * This class give general code base services.
 * To get the service use the static method: <code>HtmlCodeWriter.getInstance()</code>. Use <code>init</code> to reset.<p>
 * Following code services:<br>
 * 1. getClassJavaDoc - return the class documentation.<br>
 * 2. getMethodJavaDoc - return the method documentation.<br>
 * 3. getMethodAnnotation - return the method doclet tag.<br>
 * 4. getCode - take tests sources and convert it to html and present it as
 * part of the report. the attribute 'tests.src' is used to find whare is the
 * tests sources located.<br>
 * 
 * @author guy.arieli
 * 
 */
public class HtmlCodeWriter {
	private static Logger log = LoggerFactory.getLogger(HtmlCodeWriter.class);

	private static HtmlCodeWriter writer;

	public static HtmlCodeWriter getInstance() {
		if (writer == null) {
			writer = new HtmlCodeWriter();
		}
		return writer;
	}

	public static void init() {
		if(writer != null){
			writer.close();
		}
		writer = null;
	}
	/**
	 * Contain all the sources to parse.
	 */
	JavaParser javaParser = null;
	File srcDir;
	
	/**
	 * Hold all the files that were loaded to the doc builder and the last time
	 * they were modified.
	 * Only updated file will be loaded.
	 */
	HashMap<File, Long> filesTime;
	
	private HtmlCodeWriter() {
		/*
		 * Find the source folder
		 */
		String testsSourceFolder = JSystemProperties.getInstance().getPreference(FrameworkOptions.TESTS_SOURCE_FOLDER);
		if (testsSourceFolder == null) {
			String testsClassFolderName = null;
			try {
				testsClassFolderName = JSystemProperties.getCurrentTestsPath();
			} catch (Exception e1) {
				// can't find the current test pass
				log.warn("Failed to get current tests path");
			}
			if (testsClassFolderName != null) {
				File testsClassFolder = new File(testsClassFolderName);
				if (new File(testsClassFolder.getParent(),"tests").exists()){
					//We are in a Ant structured project
					testsSourceFolder = (new File(testsClassFolder.getParent(), "tests")).getPath();
					JSystemProperties.getInstance().setPreference(FrameworkOptions.TESTS_SOURCE_FOLDER, testsSourceFolder);
					JSystemProperties.getInstance().setPreference(FrameworkOptions.RESOURCES_SOURCE_FOLDER, testsSourceFolder);					
				}else {
					//We are in a Maven structured project
					testsSourceFolder = (new File(testsClassFolder.getParentFile().getParentFile(), "src/main/java")).getPath();
					JSystemProperties.getInstance().setPreference(FrameworkOptions.TESTS_SOURCE_FOLDER, testsSourceFolder);
					String resourcesSourceFolder = (new File(testsClassFolder.getParentFile().getParentFile(), "src/main/resources")).getPath();
					JSystemProperties.getInstance().setPreference(FrameworkOptions.RESOURCES_SOURCE_FOLDER, resourcesSourceFolder);
				}
			} else {
				testsSourceFolder = System.getProperty("user.dir");
			}
		}
		srcDir = new File(testsSourceFolder);
		javaParser = new JavaParser();
		filesTime = new HashMap<File, Long>();
	}
	public void close (){
		javaParser = null;
		filesTime = null;
	}
	/**
	 * Get the test code formated as HTML.
	 * @param className the test class name.
	 * @return an html with the code formated.
	 * @throws Exception when java2html class is missing, the file is not found or other error occurs
	 */
	public String getCode(String className) throws FileNotFoundException, ClassNotFoundException, Exception {
		File srcFile = new File(srcDir.getPath(), className.replace('.', File.separatorChar) + ".java");
		if (!srcFile.exists()) {
			srcFile = new File(srcDir.getPath(), className.replace('.', File.separatorChar) + ".groovy");
			if (!srcFile.exists()) {
				throw new FileNotFoundException(srcFile.getPath());
			}
		}
		// Create a reader of the raw input text

		// Parse the raw text to a JavaSource object

		Class<?> sourceParserClass = LoadersManager.getInstance().getLoader().loadClass("de.java2html.javasource.JavaSourceParser");
		Object sourceParser = sourceParserClass.newInstance();
		Method parseMethod = sourceParserClass.getMethod("parse", File.class);
		if (parseMethod == null) {
			return "";
		}
		Object source = parseMethod.invoke(sourceParser, srcFile);
		if (source == null) {
			return "";
		}
		Class<?> converterClass = LoadersManager.getInstance().getLoader().loadClass("de.java2html.converter.JavaSource2HTMLConverter");
		StringWriter writer = new StringWriter();
		Object converter = converterClass.getConstructor(source.getClass()).newInstance(source);
		converterClass.getMethod("convert", Writer.class).invoke(converter, writer);
		
		
//		JavaSource source = null;
//		source = new JavaSourceParser().parse(srcFile);

		// Create a converter and write the JavaSource object as Html
//		JavaSource2HTMLConverter converter = new JavaSource2HTMLConverter(source);
//		StringWriter writer = new StringWriter();
//		converter.convert(writer);
		String toReturn = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.0 Transitional//EN\">\n" + "<html><head>\n" +
		// "<title></title>\n" +
				"</head>\n" + "<body>\n" + writer.toString() + "</body>\n" + "</html>\n";

		return toReturn;

	}
	
	/**
	 * Get the class javadoc
	 * @param className the class name
	 * @return the class javadoc or null if not exist or not found
	 * @throws Exception
	 */
	public String getClassJavaDoc(String className) throws Exception {
		CompilationUnit cu = processSource(className);
		if(cu == null){
			return null;
		}
		Optional<ClassOrInterfaceDeclaration> classDecl = cu.findFirst(ClassOrInterfaceDeclaration.class, 
			c -> c.getNameAsString().equals(getSimpleClassName(className)));
		if(classDecl.isPresent() && classDecl.get().getJavadocComment().isPresent()){
			return classDecl.get().getJavadocComment().get().getContent();
		}
		return null;
	}

	/**
	 * get the method javadoc
	 * @param className the class name to look for
	 * @param methodName the method to look for
	 * @return the documenation of the method or null if not exist
	 * @throws Exception
	 */
	public String getMethodJavaDoc(String className, String methodName) throws Exception {
		CompilationUnit cu = processSource(className);
		if(cu == null){
			return null;
		}
		Optional<MethodDeclaration> methodDecl = cu.findFirst(MethodDeclaration.class, 
			m -> m.getNameAsString().equals(methodName));
		if(methodDecl.isPresent() && methodDecl.get().getJavadocComment().isPresent()){
			return methodDecl.get().getJavadocComment().get().getContent();
		}
		return null;
	}
	
	/**
	 * Get the doclet tag for a specifc class and method
	 * @param className the class to look for.
	 * @param methodName the method to look for.
	 * @param annotation the doclet to look for.
	 * @return the doclet if exist of null if not.
	 */
	public String getMethodAnnotation(String className, String methodName, String annotation){
		CompilationUnit cu = processSource(className);
		if(cu == null){
			return null;
		}
		Optional<MethodDeclaration> methodDecl = cu.findFirst(MethodDeclaration.class, 
			m -> m.getNameAsString().equals(methodName));
		if(methodDecl.isPresent() && methodDecl.get().getJavadocComment().isPresent()){
			JavadocComment javadoc = methodDecl.get().getJavadocComment().get();
			// Parse javadoc for specific annotation/tag
			String content = javadoc.getContent();
			String[] lines = content.split("\n");
			for(String line : lines){
				line = line.trim();
				if(line.startsWith("@" + annotation)){
					int spaceIndex = line.indexOf(' ');
					if(spaceIndex > 0 && spaceIndex < line.length() - 1){
						return line.substring(spaceIndex + 1).trim();
					}
				}
			}
		}
		
		// method not found, check superclass
		Class<?> c;
		try {
			c = LoadersManager.getInstance().getLoader().loadClass(className);
		} catch (ClassNotFoundException e) {
			return null;
		}
		if(c != null && !c.equals(Object.class)){
			return getMethodAnnotation(c.getSuperclass().getName(), methodName, annotation);
		}
		return null;
	}
	/**
	 * Process the class and reload it if it changed.
	 * @param className
	 * @return CompilationUnit or null if not found
	 */
	private CompilationUnit processSource(String className){
		File testSrc = new File(srcDir, className.replace('.', File.separatorChar) + ".java");
		if(!testSrc.exists()){ // if the file doesn't exist return
			//Added support for Groovy tests
			testSrc = new File(srcDir, className.replace('.', File.separatorChar) + ".groovy");
			if(!testSrc.exists()){
				return null;
			}
		}
		/*
		 * Check if the last modified time changed
		 * if not return cached result
		 */
		Long time = filesTime.get(testSrc);
		if(time == null || !time.equals(testSrc.lastModified())){ // not exist or changed
			try {
				String code = FileUtils.read(testSrc);
				CompilationUnit cu = javaParser.parse(preProcessCode(code)).getResult().orElse(null);
				filesTime.put(testSrc, testSrc.lastModified());
				return cu;
			} catch (Throwable e) {
				// ignore process fail
				log.debug("Fail to process file: " + testSrc.getAbsolutePath(), e);
				return null;
			}
		}
		// Return cached result - for now we'll re-parse since we don't cache CompilationUnits
		try {
			String code = FileUtils.read(testSrc);
			return javaParser.parse(preProcessCode(code)).getResult().orElse(null);
		} catch (Throwable e) {
			log.debug("Fail to process file: " + testSrc.getAbsolutePath(), e);
			return null;
		}
		
		
	}
	/**
	 * Remove enumeration definition
	 * @param code the original code
	 * @return Reader from the changed code
	 */
	public static Reader preProcessCode(String code){
		return new StringReader(code);
		
	}
	
	/**
	 * Get simple class name from fully qualified class name
	 * @param fullyQualifiedName the full class name
	 * @return simple class name
	 */
	private String getSimpleClassName(String fullyQualifiedName) {
		int lastDot = fullyQualifiedName.lastIndexOf('.');
		return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
	}

}
