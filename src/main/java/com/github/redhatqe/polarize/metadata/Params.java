package com.github.redhatqe.polarize.metadata;

import java.lang.annotation.Target;

/**
 * Container for @Param since that annotation is Repeatable
 */
@Target({})
public @interface Params {
    Param[] value();
}
