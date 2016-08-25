package com.github.redhatqe.polarize;

import com.github.redhatqe.polarize.exceptions.XSDValidationError;
import com.github.redhatqe.polarize.importer.xunit.Testsuites;
import com.github.redhatqe.polarize.schema.ReqType;
import com.github.redhatqe.polarize.schema.TestCaseMetadata;
import com.github.redhatqe.polarize.schema.Testcase;
import com.github.redhatqe.polarize.schema.WorkItem;

import java.net.URL;


/**
 * Created by Sean Toner on 7/19/2016.
 */
public class JAXBHelper implements IJAXBHelper {

    public URL getXSDFromResource(Class<?> t) {
        URL xsd;
        if (t == WorkItem.class) {
            xsd = JAXBHelper.class.getClassLoader().getResource("schema/workitem.xsd");
        }
        else if (t == Testcase.class) {
            xsd = JAXBHelper.class.getClass().getResource("schema/testcase.xsd");
        }
        else if (t == ReqType.class) {
            xsd = JAXBHelper.class.getClass().getResource("schema/requirement.xsd");
        }
        else if (t == TestCaseMetadata.class) {
            xsd = JAXBHelper.class.getClassLoader().getResource("schema/workitems.xsd");
        }
        else if (t == Testsuites.class) {
            xsd = JAXBHelper.class.getClassLoader().getResource("importers/xunit.xsd");
        }
        else if (t == com.github.redhatqe.polarize.importer.testcase.Testcase.class) {
            xsd = JAXBHelper.class.getClassLoader().getResource("testcase_importer/testcase-importer.xsd");
        }
        else if (t == com.github.redhatqe.polarize.importer.testcase.Testcases.class) {
            xsd = JAXBHelper.class.getClassLoader().getResource("testcase_importer/testcase-importer.xsd");
        }
        else
            throw new XSDValidationError();

        return xsd;
    }
}
