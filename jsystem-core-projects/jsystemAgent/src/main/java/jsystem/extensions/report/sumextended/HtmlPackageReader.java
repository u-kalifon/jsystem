/*
 * Created on 26/04/2005
 *
 * Copyright 2005-2010 Ignis Software Tools Ltd. All rights reserved.
 */
package jsystem.extensions.report.sumextended;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jsystem.utils.FileUtils;

/**
 * @author uri.koaz
 * 
 * TODO To change the template for this generated type comment go to Window -
 * Preferences - Java - Code Style - Code Templates
 */
public class HtmlPackageReader implements DataReader {
	private static Logger log = LoggerFactory.getLogger(HtmlPackageReader.class);

	private File srcDirectory;

	public HtmlPackageReader(String path) {
		srcDirectory = new File(path);
	}

	public String getTitle(String packageName) {
		File packageDir = new File(srcDirectory, packageName.replace('.', '/'));
		File packageFile = new File(packageDir, "package.html");

		if (!packageFile.exists()) {
			return null;
		}
		String html;
		try {
			html = FileUtils.read(packageFile);
		} catch (IOException e) {
			log.info("Fail to read file", e);
			return null;
		}
		Pattern p = Pattern.compile("<title.*>(.*)</title>");
		Matcher m = p.matcher(html);
		if (!m.find()) {
			log.debug("Title wasn't found");
			return null;
		}
		return m.group(1);
	}

	public String getDescription(String packageName) {
		File packageDir = new File(srcDirectory, packageName.replace('.', '/'));
		File packageFile = new File(packageDir, "package.html");

		if (!packageFile.exists()) {
			return null;
		}
		String html;

		try {
			html = FileUtils.read(packageFile);
		} catch (IOException e) {
			log.info("Fail to read file", e);
			return null;
		}

		Pattern p = Pattern.compile("<body>(.*)</body>", Pattern.DOTALL);
		Matcher m = p.matcher(html);
		if (!m.find()) {
			log.debug("Body wasn't found");
			return null;
		}
		return m.group(1);

	}

}
