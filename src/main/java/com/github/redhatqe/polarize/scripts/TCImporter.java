package com.github.redhatqe.polarize.scripts;

import com.github.redhatqe.polarize.IJAXBHelper;
import com.github.redhatqe.polarize.JAXBHelper;
import com.github.redhatqe.polarize.TestDefinitionProcessor;
import com.github.redhatqe.polarize.configuration.ConfigType;
import com.github.redhatqe.polarize.configuration.XMLConfig;

import com.github.redhatqe.polarize.importer.testcase.*;
import com.github.redhatqe.polarize.junitreporter.XUnitReporter;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Creates
 */
public class TCImporter {
    private final static Logger logger = LoggerFactory.getLogger(XUnitReporter.class);
    private static XMLConfig config = new XMLConfig(null);
    private final static ConfigType cfg = config.config;

    public static void main(String[] args) {
        OptionParser parser = new OptionParser();

        String defaultUrl = config.polarion.getUrl();
        defaultUrl += config.testcase.getEndpoint().getRoute();

        /* TODO: Uncomment this when ready to add a Testcase import request
        OptionSpec<String> urlOpt = parser.accepts("url").withRequiredArg().ofType(String.class)
                .defaultsTo(defaultUrl);
        OptionSpec<String> userOpt = parser.accepts("user").withRequiredArg().ofType(String.class)
                .defaultsTo(config.kerb.getUser());
        OptionSpec<String> pwOpt = parser.accepts("pass").withRequiredArg().ofType(String.class)
                .defaultsTo(config.kerb.getPassword());
                */
        OptionSpec<String> output = parser.accepts("output").withRequiredArg().ofType(String.class).required();
        OptionSpec<String> selNameOpt = parser.accepts("sel-name").withRequiredArg().ofType(String.class)
                .defaultsTo(config.testcase.getSelector().getName());
        OptionSpec<String> selValOpt = parser.accepts("sel-val").withRequiredArg().ofType(String.class)
                .defaultsTo(config.testcase.getSelector().getVal());
        OptionSpec<String> projOpt = parser.accepts("project").withRequiredArg().ofType(String.class)
                .defaultsTo("RedHatEnterpriseLinux7");
        OptionSpec<String> xmlDescOpt = parser.accepts("xml-desc").withRequiredArg().ofType(String.class);


        OptionSet opts = parser.parse(args);
        String tcsXML = opts.valueOf(output);
        // TODO: uncomment this when ready to do a Testcase import request
        //String url = opts.valueOf(urlOpt);
        //String pw = opts.valueOf(pwOpt);
        //String user = opts.valueOf(userOpt);
        String selName = opts.valueOf(selNameOpt);
        String selVal = opts.valueOf(selValOpt);
        String projID = opts.valueOf(projOpt);
        String xmlDesc = opts.valueOf(xmlDescOpt);

        // Get all the XML files under a directory.  Marshall them to a object, adding it to the testcases
        JAXBHelper jaxb = new JAXBHelper();

        Path xmldesc = Paths.get(xmlDesc);
        try {
            DirectoryStream<Path> stream = Files.newDirectoryStream(xmldesc);
            for (Path p : stream) {
                Testcases tcs = new Testcases();
                List<Testcase> testcases = new ArrayList<>();
                if(p.toFile().isDirectory()) {
                    DirectoryStream<Path> pstream = Files.newDirectoryStream(p, "*.xml");
                    for(Path xp: pstream) {
                        File xmlPath = xp.toFile();
                        Optional<Testcase> maybeTC = IJAXBHelper.unmarshaller(Testcase.class, xmlPath,
                                jaxb.getXSDFromResource(Testcase.class));
                        maybeTC.ifPresent(t -> testcases.add(t));
                    }

                    // Filter for only the Testcase that have data provider
                    List<Testcase> dataProviderTests;
                    dataProviderTests = testcases.stream()
                            .filter(tests -> {
                                TestSteps ts = tests.getTestSteps();
                                boolean dataProvider = false;
                                // There should only be one TestStep per TestCase
                                for(TestStep step: ts.getTestStep()) {
                                    // There should be only one TestStepColumn per TestStep
                                    for(TestStepColumn col: step.getTestStepColumn()) {
                                        dataProvider = !col.getContent().isEmpty();
                                    }
                                }
                                return dataProvider;
                            })
                            .collect(Collectors.toList());

                    // Get rid of all the <custom-field> in the TestCase
                    dataProviderTests.forEach(t -> {
                        //t.getCustomFields().getCustomField().clear();
                        t.setCustomFields(null);
                    });

                    //tcs.getTestcase().addAll(dataProviderTests);
                    Iterator<Path> lastName = p.iterator();
                    String fparent = "";
                    while(lastName.hasNext()) {
                        Path next = lastName.next();
                        fparent = next.toString();
                    }
                    File xml = new File(tcsXML + "-" + fparent + ".xml");
                    Map<String, List<Testcase>> tcMap = new HashMap<>();
                    tcMap.put(projID, dataProviderTests);
                    TestDefinitionProcessor.initTestcases(selName, selVal, projID, xml, tcMap, tcs);
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }



        // TODO: Make a TestCase import request
    }
}
