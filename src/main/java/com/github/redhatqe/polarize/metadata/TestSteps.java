package com.github.redhatqe.polarize.metadata;

import java.lang.annotation.Target;

/**
 * Created by stoner on 6/10/16.
 */
@Target({})
public @interface TestSteps {
    TestStep[] value() default {};
}
