package com.redhat.qe.rhsm.importer;

import com.redhat.qe.rhsm.JAXBHelper;
import com.redhat.qe.rhsm.importer.xunit.Testcase;
import com.redhat.qe.rhsm.importer.xunit.Testsuites;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Class that will modify the xunit result file so that it can be used by XUnit Importer
 *
 * It will read in an xunit result file and unmarshall to an appropriate java object.  Since we can now map a
 * class.methodName to a xml description file, and this description file will contain the unique Polarion ID, we can
 * add this data to the xunit xml file.
 */
public class XUnitHelper {
    private String xunitPath;


    /**
     *
     * @param xunitFile
     * @return
     */
    public Optional<Testsuites> loadXunitFile(File xunitFile) {
        return JAXBHelper.unmarshaller(Testsuites.class, xunitFile, JAXBHelper.getXSDFromResource(Testsuites.class));
    }

    /**
     * Iterate though the suites, and for each suite, iterate through the test cases.  For each test case, look up
     * the qualified name, and find the matching XML description file.  Read in the description file and get the
     * Polarion test ID.
     * 
     * @param suites
     */
    public void addProperties(Testsuites suites) {

    }
}
