package com.github.redhatqe.polarize.junitreporter;

import java.util.*;

/**
 * Created by stoner on 1/23/17.
 */
public class FullResult {
    public int total = 0;
    public int passes = 0;
    public int fails = 0;
    public int errors = 0;
    public int skips = 0;
    public String classname = "";
    public Boolean dataProvider = false;
    public Set<String> errorsByMethod = new TreeSet<>();

    public FullResult add(FullResult x) {
        FullResult fr = new FullResult();
        fr.total = this.total + x.total;
        fr.skips = this.skips + x.skips;
        fr.errors = this.errors + x.errors;
        fr.passes = this.passes + x.passes;
        fr.classname = x.classname;
        fr.dataProvider = this.dataProvider && x.dataProvider;
        return fr;
    }
}
