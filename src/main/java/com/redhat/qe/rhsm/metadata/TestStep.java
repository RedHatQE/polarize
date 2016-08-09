package com.redhat.qe.rhsm.metadata;

import java.lang.annotation.Repeatable;

/**
 * Created by stoner on 6/10/16.
 */
@Repeatable(TestSteps.class)
public @interface TestStep {
    String expected() default "";
    String description() default "";
    String method() default "";
}
