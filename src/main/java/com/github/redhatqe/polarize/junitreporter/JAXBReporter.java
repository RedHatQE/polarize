package com.github.redhatqe.polarize.junitreporter;


import com.github.redhatqe.polarize.IJAXBHelper;
import com.github.redhatqe.polarize.exceptions.XSDValidationError;
import com.github.redhatqe.polarize.importer.xunit.Testcase;
import com.github.redhatqe.polarize.importer.xunit.Testsuite;
import com.github.redhatqe.polarize.importer.xunit.Testsuites;
import com.github.redhatqe.polarize.schema.WorkItem;

import javax.xml.bind.JAXB;
import java.net.URL;

/**
 * Created by stoner on 8/16/16.
 */
public class JAXBReporter implements IJAXBHelper {
    @Override
    public URL getXSDFromResource(Class<?> t) {
        URL xsd;
        if (t == Testcase.class || t == Testsuite.class || t == Testsuites.class) {
            xsd = JAXBReporter.class.getClassLoader().getResource("importers/xunit.xsd");
        }
        else if (t == WorkItem.class) {
            xsd = JAXBReporter.class.getClassLoader().getResource("schema/workitem.xsd");
        }
        else
            throw new XSDValidationError();
        return xsd;
    }
}