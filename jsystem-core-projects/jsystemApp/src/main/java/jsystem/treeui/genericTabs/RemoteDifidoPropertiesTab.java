package jsystem.treeui.genericTabs;

import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JPanel;

import jsystem.extensions.report.difido.RemoteDifidoProperties;
import jsystem.treeui.interfaces.JSystemTab;
import jsystem.treeui.teststable.TestsTableController;

/**
 * Generic tab for displaying the remote Difido properties.
 *
 */
public class RemoteDifidoPropertiesTab implements JSystemTab {

	private static Logger log = LoggerFactory.getLogger(RemoteDifidoPropertiesTab.class);

	@Override
	public JPanel init() {
		// Making sure that the remote Difido properties file will be created
		new RemoteDifidoProperties();

		PropertiesGuiPanel panel = null;
		try {
			panel = new PropertiesGuiPanel(new File(RemoteDifidoProperties.FILE_NAME));
		} catch (IOException e) {
			log.warn("Failed to open the remote Difido tab due to " + e.getMessage());
			e.printStackTrace();
		}
		return panel;
	}

	@Override
	public String getTabName() {
		return "Remote Difido Properties";
	}

	@Override
	public void setTestsTableController(TestsTableController testsTableController) {
		// Not used
	}

}
