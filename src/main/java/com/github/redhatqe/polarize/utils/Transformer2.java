package com.github.redhatqe.polarize.utils;

/**
 * Created by stoner on 9/23/16.
 */
public interface Transformer2<P1, P2, R> {
    public R transform(P1 p1, P2 p2);
}
