package com.redhat.qe.rhsm;


import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Set;

/**
 * Created by stoner on 5/17/16.
 *
 * This is an implementation of an annotation processor for the Polarion interface.  It will check to see
 * if the annotation has an xmlString value.  If it does, it will find and load it, using those values for
 * the metadata of the test.
 *
 * If there is none, it will use the values supplied in the annotation for the data.  If the testCaseID or
 * requirementIDs are empty, then it will generate an XML file according to the schema for the XML Work Item
 * generator.  It will then take this workitem-metadata.xml file and hand it to the work item importer.
 * It will wait for the response from this tool which should be another xml file in return.  The response
 * XML should contain the test case ID or requirement ID
 */
public class PolarionProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        return false;
    }

    public synchronized void init(ProcessingEnvironment env) { }

    public Set<String> getSupportedAnnotationTypes() {
        return null;
    }

    public SourceVersion getSupportedSourceVersion() {
        return null;
    }
}
