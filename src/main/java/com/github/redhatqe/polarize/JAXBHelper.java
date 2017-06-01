package com.github.redhatqe.polarize;


import com.github.redhatqe.polarize.exceptions.XSDValidationError;

import com.github.redhatqe.polarize.reporter.importer.testcase.Testcase;
import com.github.redhatqe.polarize.reporter.importer.testcase.Testcases;
import com.github.redhatqe.polarize.reporter.importer.xunit.Testsuites;
import com.github.redhatqe.polarize.reporter.jaxb.IJAXBHelper;

import java.net.URL;


public class JAXBHelper implements IJAXBHelper {

    public URL getXSDFromResource(Class<?> t) {
        URL xsd;
        if (t == Testcase.class) {
            xsd = JAXBHelper.class.getClass().getResource("schema/testcase.xsd");
        }
        else if (t == Testsuites.class) {
            xsd = JAXBHelper.class.getClassLoader().getResource("importers/xunit.xsd");
        }
        else if (t == Testcase.class) {
            xsd = JAXBHelper.class.getClassLoader().getResource("testcase_importer/testcase-importer.xsd");
        }
        else if (t == Testcases.class) {
            xsd = JAXBHelper.class.getClassLoader().getResource("testcase_importer/testcase-importer.xsd");
        }
        else
            throw new XSDValidationError();

        return xsd;
    }
}
