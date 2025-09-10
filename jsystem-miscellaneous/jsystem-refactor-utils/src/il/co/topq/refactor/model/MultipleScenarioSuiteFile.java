package il.co.topq.refactor.model;

import il.co.topq.refactor.exception.MultipleScenarioSuiteException;
import il.co.topq.refactor.utils.XmlUtils;

import java.io.File;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class MultipleScenarioSuiteFile extends JSystemFile {

	private final static String XPATH_GET_ALL_SCENARIOS = "//scenarioName[text()='%s.xml']";

	private Document doc;

	public MultipleScenarioSuiteFile(String multipleSuiteAbsolutePathFileName) {
		super(new File(multipleSuiteAbsolutePathFileName));
	}

	private void init() throws MultipleScenarioSuiteException {
		if (doc != null) {
			return;
		}
		log.debug("Init multiple scenario suit file");
		try {
			doc = XmlUtils.parseDocument(file);
		} catch (Exception e) {
			log.error("Failed to parse file " + file.getName(), e);
			throw new MultipleScenarioSuiteException("Failed to parse file " + file.getName());
		}

	}

	public boolean isScenarioExists(String scenarioSourceNamePath) throws MultipleScenarioSuiteException {
		init();
		log.debug("Checking if scenario " + scenarioSourceNamePath + " exists in file");
		try {
			return XmlUtils.getNodeList(doc, String.format(XPATH_GET_ALL_SCENARIOS, scenarioSourceNamePath))
					.getLength() > 0 ? true : false;
		} catch (XPathExpressionException e) {
			throw new MultipleScenarioSuiteException("Failed to get scenarios");
		}
	}

	public void renameScenario(String scenarioSourceNamePath, String scenarioTargetNamePath)
			throws MultipleScenarioSuiteException {
		init();
		NodeList scenariosNodes;
		try {
			scenariosNodes = XmlUtils.getNodeList(doc, String.format(XPATH_GET_ALL_SCENARIOS, scenarioSourceNamePath));
		} catch (XPathExpressionException e) {
			throw new MultipleScenarioSuiteException("Failed to get scenarios");
		}
		for (int i = 0; i < scenariosNodes.getLength(); i++) {
			log.debug("Renaming scenario from " + scenarioSourceNamePath + " to " + scenarioTargetNamePath);
			((Element) scenariosNodes.item(i)).setTextContent(scenarioTargetNamePath + ".xml");
		}

	}

	public void save() throws TransformerException {
		log.debug("Saving file " + file.getName());
		TransformerFactory factory = TransformerFactory.newInstance();
		Transformer transformer = factory.newTransformer();
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		doc.setXmlStandalone(true);
		Result result = new StreamResult(file);
		Source source = new DOMSource(doc);
		transformer.transform(source, result);

	}

}
