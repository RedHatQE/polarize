package com.github.redhatqe.polarize.exceptions;

import com.github.redhatqe.polarize.importer.ImporterRequest;

/**
 * Created by stoner on 11/2/16.
 */
public class ImportRequestError extends Error {
    public ImportRequestError(String err) {
        super(err);
    }
}
