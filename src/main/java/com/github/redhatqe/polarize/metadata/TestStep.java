package com.github.redhatqe.polarize.metadata;

import java.lang.annotation.Repeatable;

/**
 * Created by stoner on 6/10/16.
 */
@Repeatable(TestSteps.class)
public @interface TestStep {
    String expected() default "";         // Optional: What the expected value should be from running this
    String description() default "";      // Optional: Description of what the step does
    String method() default "";
    int numParams() default 0;
}
