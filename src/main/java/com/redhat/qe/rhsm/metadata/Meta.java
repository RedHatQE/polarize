package com.redhat.qe.rhsm.metadata;

/**
 * Contains the fully qualified name of a @Polarion decorated method
 */
public class Meta<T> {
    public String packName;
    public String className;
    public String methName;
    public String qualifiedName;
    public T annotation;

    public Meta() {

    }

    public Meta(Meta orig) {
        this.qualifiedName = orig.qualifiedName;
        this.methName = orig.methName;
        this.className = orig.className;
        this.packName = orig.packName;
    }
}
