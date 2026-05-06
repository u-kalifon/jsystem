/*
 * Copyright 2005-2010 Ignis Software Tools Ltd. All rights reserved.
 */
package com.aqua.services.welcome;

import org.junit.Test;

import jsystem.framework.TestProperties;
import jsystem.framework.report.Reporter;
import junit.framework.SystemTestCase4;

public class Welcome extends SystemTestCase4 {

	/**
	 * Logs a welcome message.<br>
	 */
	@TestProperties(name="Welcome to JSystem. To run this test, press on the play button.")
	@Test
	public void welcome(){
		report.step("Welcome to JSystem framework.");
		report.report("How to continue from here:");
		report.report("You can start by reading JSystem <a href=\"https://github.com/Top-Q/jsystem-docs/wiki\">documentation.</a>", "", Reporter.PASS, false, true, false, false);
		report.report("For questions go to our site: <a href=\"http://www.jsystemtest.org\">www.jsystemtest.org</a>, post a question to our <a href=\"http://sourceforge.net/forum/forum.php?forum_id=397999\">help forum</a>.", "", Reporter.PASS, false, true, false, false);
		report.report("or send us a mail <a rel=\"nofollow\" href=\"mailto:jsystemtest@gmail.com\">jsystemtest@gmail.com</a>");
	}
}

