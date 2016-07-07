package com.redhat.qe.rhsm;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Created by stoner on 7/7/16.
 */
public class FileHelper {
    public static Optional<Path> makePath(String path) {
        Path p = null;
        try {
           p = Paths.get(path);
        } catch (InvalidPathException ip) {

        }
        return Optional.of(p);
    }
}
