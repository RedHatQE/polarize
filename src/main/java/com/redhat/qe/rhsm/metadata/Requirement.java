package com.redhat.qe.rhsm.metadata;

/**
 * Created by stoner on 6/10/16.
 */
public @interface Requirement {
    String polarionId();
    String config();
    String feature();
}
