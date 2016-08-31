package com.github.redhatqe.polarize.importer;

import com.github.redhatqe.polarize.Configurator;
import com.github.redhatqe.polarize.IFileHelper;
import com.github.redhatqe.polarize.importer.xunit.Property;
import com.github.redhatqe.polarize.importer.xunit.Testcase;
import com.github.redhatqe.polarize.importer.xunit.Testsuites;
import com.github.redhatqe.polarize.IJAXBHelper;
import com.github.redhatqe.polarize.JAXBHelper;
import com.github.redhatqe.polarize.exceptions.PolarionMappingError;
import com.github.redhatqe.polarize.exceptions.XMLDescriptionError;
import com.github.redhatqe.polarize.importer.xunit.Testsuite;
import com.github.redhatqe.polarize.schema.WorkItem;
import com.github.redhatqe.polarize.importer.xunit.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.UnmarshalException;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Class that will modify the xunit result file so that it can be used by XUnit Importer
 *
 * It will read in an xunit result file and unmarshall to an appropriate java object.  Since we can now map a
 * class.methodName to a xml description file, and this description file will contain the unique Polarion ID, we can
 * add this data to the xunit xml file.
 */
public class XUnitHelper {
    private static Logger logger;

    static {
        logger = LoggerFactory.getLogger(Configurator.class);
    }

    /**
     *
     * @param xunitFile
     * @return
     */
    public static Optional<Testsuites> loadXunitFile(File xunitFile) {
        JAXBHelper jaxb = new JAXBHelper();
        return IJAXBHelper.unmarshaller(Testsuites.class, xunitFile, jaxb.getXSDFromResource(Testsuites.class));
    }

    /**
     * Adds the required <properties> element required for the XUnit importer
     *
     * This method iterates though the suites, and for each suite, iterates through the test cases.  For each test case,
     * it looks up the qualified name, and finds the matching XML description file.  It reads in the description file
     * and gets the Polarion test ID.
     *
     * @param suites
     */
    public static void addProperties(Testsuites suites, TestSuitesProperties props) {
        String projectID = props.getProjectID();

        for(Testsuite suite : suites.getTestsuite()) {
            XUnitHelper.addPropertiesTestSuite(suite, projectID);
        }
    }

    public static void addPropertiesTestSuite(Testsuite suite, String project) {
        for(Testcase tc : suite.getTestcase()) {
            XUnitHelper.addTestCasesProperties(tc, project);
        }
    }

    private static void addTestCasesProperties(Testcase tc, String projectID) {
        String cName = tc.getClassname();
        String mName = tc.getName();

        Map<String, String> config = Configurator.loadConfiguration();
        String tcPath = config.get("testcases.xml.path");
        Path descFile = IFileHelper.makeXmlPath(tcPath, projectID, cName, mName);
        File descPath = descFile.toFile();

        Optional<WorkItem> wi;
        JAXBHelper jaxb = new JAXBHelper();
        wi = IJAXBHelper.unmarshaller(WorkItem.class, descPath, jaxb.getXSDFromResource(WorkItem.class));

        if (!wi.isPresent()) {
            logger.error(String.format("No xml description file found for %s.%s", cName, mName));
            throw new XMLDescriptionError();
        }

        WorkItem item = wi.get();
        com.github.redhatqe.polarize.schema.Testcase test = item.getTestcase();
        String polarionID = test.getWorkitemId();
        if (polarionID == null || polarionID.equals("")) {
            logger.error("The Polarion workitem-id was either empty or an empty string.");
            throw new PolarionMappingError();
        }

        Properties properties = new Properties();
        List<Property> props = properties.getProperty();
        Property id = new Property();
        id.setName("polarion-testcase-id");
        id.setValue(polarionID);
        props.add(id);
        tc.setProperties(properties);
    }

    /**
     * FIXME: Really need to have real unit tests in place
     *
     * Loads a junit report file
     *
     * @param args
     */
    public static void main(String[] args) throws UnmarshalException {
        TestSuitesProperties props = new TestSuitesProperties("PLATTP", "ci-user");
        String junit = args[0];
        File junitFile = Paths.get(junit).toFile();
        if (!junitFile.exists()) {
            XUnitHelper.logger.error("The path to the junit file does not exist");
            return;
        }

        Optional<Testsuite> suite;
        JAXBHelper jaxb = new JAXBHelper();
        suite = IJAXBHelper.unmarshaller(Testsuite.class, junitFile, jaxb.getXSDFromResource(Testsuites.class));
        if (!suite.isPresent())
            throw new javax.xml.bind.UnmarshalException("Could not unmarshall object");

        XUnitHelper.addPropertiesTestSuite(suite.get(), props.getProjectID());
    }
}
