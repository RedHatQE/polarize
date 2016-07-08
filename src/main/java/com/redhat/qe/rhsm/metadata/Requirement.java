package com.redhat.qe.rhsm.metadata;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by stoner on 6/10/16.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Requirement {
    String id() default "";                          // if "", must have xmlDesc
    String project() default "";                     // required if annotating a class.  If embedded in @Polarion it
                                                     // uses the value of project from it
    String author() default "";                      // if "", must have xmlDesc
    String description() default "";                 // if "", must have xmlDesc
    String xmlDesc() default "";                     // Optional if id, author and description are supplied
    String severity() default "Must Have";           // defaults to Must Have
    String reqtype() default "Functional";           // defaults to Functional
    String priority() default "medium";              // defaults to medium
    String feature() default "";                     // Optional: path to a gherkin feature file
}
