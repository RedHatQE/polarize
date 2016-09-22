package com.github.redhatqe.polarize.metadata;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;

/**
 * Can only be used as an inner annotation (it can't decorate any other Elements)
 */
@Repeatable(TestSteps.class)
@Target({})
public @interface TestStep {
    String expected() default "";         // Optional: What the expected value should be from running this
    String description() default "";      // Optional: Description of what the step does
    Param[] params() default {};
}
