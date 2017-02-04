package com.github.redhatqe.polarize.metadata;

/**
 * Annotation for a TestType that mimics the Polarion test types
 *
 * Unfortunately, there's not a clean way to do this with annotations since Annotations only have limited type support
 * and Annotations themselves can neither be extended nor implemented.  If testtype is FUNCTIONAL, then the only
 * allowable subtype1 and 2 is DefTypes.Subtypes.EMPTY.  This will have to be checked for at runtime rather than by
 * the compiler
 */
public @interface TestType {
    DefTypes.TestTypes testtype() default DefTypes.TestTypes.FUNCTIONAL;
    DefTypes.Subtypes subtype1() default DefTypes.Subtypes.RELIABILITY;
    DefTypes.Subtypes subtype2() default DefTypes.Subtypes.EMPTY;
}
