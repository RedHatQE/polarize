package com.github.redhatqe.byzantine.utils;

import java.util.Map;
import java.util.Optional;

public class Environ {

    public static Optional<String> getVar(String key) {
        Map<String, String> env = System.getenv();
        String val = env.get(key);
        return Optional.ofNullable(val);
    }
}
