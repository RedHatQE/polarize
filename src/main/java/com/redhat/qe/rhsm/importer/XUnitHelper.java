package com.redhat.qe.rhsm.importer;

import com.redhat.qe.rhsm.Configurator;
import com.redhat.qe.rhsm.FileHelper;
import com.redhat.qe.rhsm.JAXBHelper;
import com.redhat.qe.rhsm.exceptions.PolarionMappingError;
import com.redhat.qe.rhsm.exceptions.XMLDescriptionError;
import com.redhat.qe.rhsm.importer.xunit.*;
import com.redhat.qe.rhsm.schema.WorkItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
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
        return JAXBHelper.unmarshaller(Testsuites.class, xunitFile, JAXBHelper.getXSDFromResource(Testsuites.class));
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
            for(Testcase tc : suite.getTestcase()) {
                XUnitHelper.addTestCasesProperties(tc, projectID);
            }
        }
    }

    private static void addTestCasesProperties(Testcase tc, String projectID) {
        String cName = tc.getClassname();
        String mName = tc.getName();

        Map<String, String> config = Configurator.loadConfiguration();
        String tcPath = config.get("tcPath");
        Path descFile = FileHelper.makeXmlPath(tcPath, projectID, cName, mName);
        File descPath = descFile.toFile();

        Optional<WorkItem> wi;
        wi = JAXBHelper.unmarshaller(WorkItem.class, descPath, JAXBHelper.getXSDFromResource(WorkItem.class));

        if (!wi.isPresent()) {
            logger.error(String.format("No xml description file found for %s.%s", cName, mName));
            throw new XMLDescriptionError();
        }

        WorkItem item = wi.get();
        com.redhat.qe.rhsm.schema.Testcase test = item.getTestcase();
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
    }
}
