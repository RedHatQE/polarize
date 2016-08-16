package com.redhat.qe.rhsm;

import com.redhat.qe.rhsm.exceptions.XMLDescriptonCreationError;
import com.redhat.qe.rhsm.exceptions.XSDValidationError;
import com.redhat.qe.rhsm.importer.xunit.Testsuites;
import com.redhat.qe.rhsm.schema.ReqType;
import com.redhat.qe.rhsm.schema.TestCaseMetadata;
import com.redhat.qe.rhsm.schema.Testcase;
import com.redhat.qe.rhsm.schema.WorkItem;

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
        else
            throw new XSDValidationError();

        return xsd;
    }
}
