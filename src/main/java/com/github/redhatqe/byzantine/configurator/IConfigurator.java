package com.github.redhatqe.byzantine.configurator;

import com.github.redhatqe.byzantine.utils.Tuple;

import java.util.List;

/**
 * The idea of the IConfigurator is one of a composition pipeline.  pipe is a function that takes in some starting
 * state T, and returns a new version of T.  The second argument is a list of a tuple, where the first element of the
 * pair is a key, and the second is a value.
 *
 */
public interface IConfigurator<T> {
    T pipe(T cfg, List<Tuple<String, String>> args);
}
