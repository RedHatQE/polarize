package com.github.redhatqe.polarize.metadata;

import com.github.redhatqe.polarize.importer.testcase.Parameter;

import java.util.List;

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
    public String testCaseID = "";

    public Meta() {

    }

    public Meta(Meta<T> orig) {
        this.qualifiedName = orig.qualifiedName;
        this.methName = orig.methName;
        this.className = orig.className;
        this.packName = orig.packName;
        this.project = orig.project;
        this.testCaseID = orig.testCaseID;
        this.annotation = orig.annotation;
    }


    public static <O, R> Meta<R> copy(Meta<O> orig) {
        Meta<R> meta = new Meta<>();
        meta.qualifiedName = orig.qualifiedName;
        meta.methName = orig.methName;
        meta.className = orig.className;
        meta.packName = orig.packName;
        meta.project = orig.project;
        meta.testCaseID = orig.testCaseID;
        return meta;
    }
}
