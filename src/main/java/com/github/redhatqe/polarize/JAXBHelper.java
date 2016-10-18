package com.github.redhatqe.polarize;

import com.github.redhatqe.polarize.configuration.ConfigType;
import com.github.redhatqe.polarize.exceptions.XSDValidationError;
import com.github.redhatqe.polarize.importer.xunit.Testsuites;
import com.github.redhatqe.polarize.importer.testcase.Testcase;
import com.github.redhatqe.polarize.importer.testcase.Testcases;

import javax.xml.bind.JAXB;
import java.net.URL;


/**
 * Created by Sean Toner on 7/19/2016.
 */
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
        else if (t == ConfigType.class) {
            xsd = JAXBHelper.class.getClassLoader().getResource("configuration/xml-config.xsd");
        }
        else
            throw new XSDValidationError();

        return xsd;
    }
}
