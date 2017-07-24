package com.github.redhatqe.byzantine.exceptions;

/**
 * Thrown when a cli value is invalid
 */
public class InvalidCLIArg extends Error {
    private String msg;
    public InvalidCLIArg(String err) {
        super(err);
        msg = err;
    }

    public String getMsg() {
        return msg;
    }
}
