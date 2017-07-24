package com.github.redhatqe.byzantine.configuration;


import com.github.redhatqe.byzantine.parser.Setter;

import java.util.Map;

public interface Handlers<T> {
    Map<String, Setter<T>> getHandler();
}
