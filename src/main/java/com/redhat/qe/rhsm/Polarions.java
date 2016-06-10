package com.redhat.qe.rhsm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Created by stoner on 5/16/16.
 */
@Target(ElementType.METHOD)
public @interface Polarions {
    Polarion[] value();
}
