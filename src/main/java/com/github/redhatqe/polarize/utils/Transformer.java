package com.github.redhatqe.polarize.utils;

/**
 * A lambda interface that takes some type P, and returns some other type R
 * Recall that a Consumer has a type like:  accept :: a -> ()
 * And a Supplier has a type like: get :: () -> a
 * Whereas this has a type like: transform :: p -> r
 */
public interface Transformer<P, R> {
    R transform(P p);
}
