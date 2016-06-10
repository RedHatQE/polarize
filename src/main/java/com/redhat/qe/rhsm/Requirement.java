package com.redhat.qe.rhsm;

/**
 * Created by stoner on 6/10/16.
 */
public @interface Requirement {
    String polarionId();
    String config();
    String feature();
}
