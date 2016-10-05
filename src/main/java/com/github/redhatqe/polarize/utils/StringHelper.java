package com.github.redhatqe.polarize.utils;

/**
 * Created by stoner on 10/5/16.
 */
public class StringHelper {
    public static String removeQuotes(String original) {
        return original.replaceAll("\"", "");
    }
}
