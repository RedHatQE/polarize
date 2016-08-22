package com.github.redhatqe.polarize;

/*
  Created by stoner on 3/23/16.
 */
/**
 * Container of information from a class method, and the @Test annotation
 */
public class MetaData {
    public String methodName;
    public String className;
    public String description;
    public Boolean enabled;
    public Boolean isDataProvider;
    public String dataProvider;

    public MetaData(String mn, String cn, String desc, Boolean enabled, Boolean isProvider, String provider) {
        this.methodName = mn;
        this.className = cn;
        this.description = desc;
        this.enabled = enabled;
        this.isDataProvider = isProvider;
        this.dataProvider = provider;
    }
}