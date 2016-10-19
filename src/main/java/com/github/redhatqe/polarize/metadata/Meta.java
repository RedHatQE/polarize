package com.github.redhatqe.polarize.metadata;

import com.github.redhatqe.polarize.FileHelper;
import com.github.redhatqe.polarize.IJAXBHelper;
import com.github.redhatqe.polarize.JAXBHelper;
import com.github.redhatqe.polarize.importer.testcase.Parameter;
import com.github.redhatqe.polarize.importer.testcase.Testcase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Contains the fully qualified name of a @TestDefinition decorated method
 */
public class Meta<T> {
    public String packName;
    public String className;
    public String methName;
    public String qualifiedName;
    public String project;
    public T annotation;
    public List<Parameter> params = null;
    public String polarionID = "";
    public static final Logger logger = LoggerFactory.getLogger(Meta.class);

    public Meta() {

    }

    public Meta(Meta<T> orig) {
        this.qualifiedName = orig.qualifiedName;
        this.methName = orig.methName;
        this.className = orig.className;
        this.packName = orig.packName;
        this.project = orig.project;
        this.polarionID = orig.polarionID;
        this.annotation = orig.annotation;
    }


    public static <O, R> Meta<R> copy(Meta<O> orig) {
        Meta<R> meta = new Meta<>();
        meta.qualifiedName = orig.qualifiedName;
        meta.methName = orig.methName;
        meta.className = orig.className;
        meta.packName = orig.packName;
        meta.project = orig.project;
        meta.polarionID = orig.polarionID;
        return meta;
    }

    public static <A> Meta<A> create(String qual, String meth, String cls, String pack, String proj, String id, A ann) {
        Meta<A> meta = new Meta<>();
        meta.qualifiedName = qual;
        meta.methName = meth;
        meta.className = cls;
        meta.packName = pack;
        meta.project = proj;
        meta.polarionID = id;
        meta.annotation = ann;
        return meta;
    }

    /**
     *
     * @return
     */
    public Optional<String> getPolarionIDFromTestcase() {
        String id = this.polarionID;
        if (id.equals(""))
            return Optional.empty();
        return Optional.of(id);
    }

    /**
     * FIXME: I think this should go in Meta
     *
     * Unmarshalls an Optional of type T from the given Meta object
     *
     * From the data contained in the Meta object, function looks for the XML description file and unmarshalls it to
     * the class given by class t.
     *
     * @param t class type
     * @param tcPath the testcase path (eg from reporter.properties)
     * @return Optionally a type of T if possible
     */
    public <T1> Optional<T1> getTypeFromMeta(Class<T1> t, String tcPath) {
        //TODO: Check for XML Desc file for TestDefinition
        Path path = FileHelper.makeXmlPath(tcPath, this);
        File xmlDesc = path.toFile();
        if (!xmlDesc.exists())
            return Optional.empty();

        Meta.logger.info("Description file exists: " + xmlDesc.toString());
        Optional<T1> witem;
        JAXBHelper jaxb = new JAXBHelper();
        witem = IJAXBHelper.unmarshaller(t, xmlDesc, jaxb.getXSDFromResource(t));
        if (!witem.isPresent())
            return Optional.empty();
        return witem;
    }

    /**
     * FIXME: I think this should go in Meta
     *
     * Unmarshalls Testcase from XML pointed at in meta, and gets the Polarion ID
     *
     * @param tcPath path to the testcases
     * @return Optionally the String of the Polarion ID
     */
    public Optional<String> getPolarionIDFromXML(String tcPath) {
        Optional<Testcase> tc = this.getTypeFromMeta(Testcase.class, tcPath);

        if (!tc.isPresent()) {
            Meta.logger.info("Unmarshalling failed.  No Testcase present...");
            return Optional.empty();
        }
        else if (tc.get().getId() == null || tc.get().getId().equals("")) {
            Meta.logger.info("No id attribute for <testcase>");
            return Optional.empty();
        }
        Testcase tcase = tc.get();
        Meta.logger.info("Polarion ID for testcase " + tcase.getTitle() + " is " + tcase.getId());
        return Optional.of(tcase.getId());
    }

    /**
     * Returns possible file location to the XML description file based on a Meta type and a project
     *
     * Uses the information from the meta type and project to know where to find XML description file
     *
     * @param tcPath The testcase path (eg from reporter.properties)
     * @return An Optional<File> if the xml exists
     */
    public Optional<File> getFileFromMeta(String tcPath) {
        Path path = FileHelper.makeXmlPath(tcPath, this);
        File xmlDesc = path.toFile();
        if (!xmlDesc.exists())
            return Optional.empty();
        return Optional.of(xmlDesc);
    }
}
