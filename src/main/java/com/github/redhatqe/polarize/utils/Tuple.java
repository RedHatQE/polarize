package com.github.redhatqe.polarize.utils;

/**
 * Created by stoner on 9/14/16.
 */
public class Tuple<F, S> {
    public F first;
    public S second;

    public Tuple(F f, S s) {
        this.first = f;
        this.second = s;
    }

    public Tuple() {

    }
}
