package com.github.redhatqe.polarize.metadata;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;

/**
 * This class is used to fill in information for com.github.redhatqe.polarize.importer.testcase.Parameter
 * It can only be used as an inner annotation type (it can't decorate any other method, annotation, class, etc).
 */
@Repeatable(Params.class)
@Target({})
public @interface Param {
    String name();
    String scope() default "local";
}
