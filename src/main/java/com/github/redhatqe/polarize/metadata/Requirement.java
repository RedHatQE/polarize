package com.github.redhatqe.polarize.metadata;

import java.lang.annotation.*;
import com.github.redhatqe.polarize.metadata.DefTypes.Importance;
import com.github.redhatqe.polarize.metadata.DefTypes.Project;
import com.github.redhatqe.polarize.metadata.DefTypes.TestTypes;
import com.github.redhatqe.polarize.metadata.DefTypes.Severity;


/**
 * Created by stoner on 6/10/16.
 */
@Repeatable(Requirements.class)
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Requirement {
    String id() default "";                          // if "", must have xmlDesc
    Project project() default Project.RHEL6;         // required if annotating a class.  If embedded in @TestDefinition
                                                     // it uses the TestDefinition
    String author() default "";                      // if "", must have xmlDesc
    String description() default "";                 // if "", must have xmlDesc
    String xmlDesc() default "";                     // Optional if id, author and description are supplied
    Severity severity() default Severity.MUSTHAVE;   // defaults to Must Have
    TestTypes reqtype() default TestTypes.FUNCTIONAL;// defaults to Functional
    Importance priority() default Importance.MEDIUM; // defaults to medium
    String feature() default "";                     // Optional: path to a gherkin feature file
    boolean update() default false;                  // If true, if xml desc file exists, update with new one
}
