package com.github.redhatqe.byzantine.exceptions;

/**
 * Thrown when the key for the dispatch is not known
 */
public class DispatchError extends Error {
    public DispatchError(String err) {
        super(err);
    }
}
