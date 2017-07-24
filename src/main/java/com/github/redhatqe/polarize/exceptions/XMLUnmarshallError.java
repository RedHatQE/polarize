package com.github.redhatqe.polarize.exceptions;

/**
 * Created by stoner on 8/22/16.
 */
public class XMLUnmarshallError extends Error {
    public XMLUnmarshallError() {
        super();
    }

    public XMLUnmarshallError(String err) {
        super(err);
    }
}
