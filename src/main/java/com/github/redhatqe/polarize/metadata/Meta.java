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

    public Meta() {

    }

    public Meta(Meta orig) {
        this.qualifiedName = orig.qualifiedName;
        this.methName = orig.methName;
        this.className = orig.className;
        this.packName = orig.packName;
        this.project = orig.project;
    }
}
