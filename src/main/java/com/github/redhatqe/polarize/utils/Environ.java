package com.github.redhatqe.polarize.utils;

import java.util.Map;
import java.util.Optional;

/**
 * Created by stoner on 5/22/17.
 */
public class Environ {

    public static Optional<String> getVar(String key) {
        Map<String, String> env = System.getenv();
        String val = env.get(key);
        return Optional.ofNullable(val);
    }
}
