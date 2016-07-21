package com.redhat.qe.rhsm.metadata;

import java.lang.annotation.*;

/**
 * Created by stoner on 6/10/16.
 */
@Repeatable(Requirements.class)
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Requirement {
    String id() default "";                          // if "", must have xmlDesc
    String project() default "";                     // required if annotating a class.  If embedded in @TestCase it
                                                     // uses the value of project from it
    String author() default "";                      // if "", must have xmlDesc
    String description() default "";                 // if "", must have xmlDesc
    String xmlDesc() default "";                     // Optional if id, author and description are supplied
    String severity() default "Must Have";           // defaults to Must Have
    String reqtype() default "Functional";           // defaults to Functional
    String priority() default "medium";              // defaults to medium
    String feature() default "";                     // Optional: path to a gherkin feature file
    boolean override() default true;                 // If true, if xml desc file exists, generate new one
}
