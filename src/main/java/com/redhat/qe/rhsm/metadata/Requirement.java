package com.redhat.qe.rhsm.metadata;

/**
 * Created by stoner on 6/10/16.
 */
public @interface Requirement {
    String polarionId();
    String severity() default "Must Have";           // defaults to Must Have
    String reqtype() default "Functional";           // defaults to Functional
    String priority() default "medium";              // defaults to medium
    String feature() default "";                     // Optional
    String author()                     ;            // required (someone has to OK this)
    String description();                            // required
}
