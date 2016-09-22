package com.github.redhatqe.polarize.utils;

/**
 * Created by stoner on 9/21/16.
 */
public class Tuple3<F, S, T> {
    public F first;
    public S second;
    public T third;

    public Tuple3(F f, S s, T t) {
        this.first = f;
        this.second = s;
        this.third = t;
    }

    public Tuple3() {

    }
}
