package com.redhat.qe.rhsm;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import com.redhat.qe.rhsm.exceptions.InvalidArgumentType;
import com.redhat.qe.rhsm.metadata.Meta;
import com.redhat.qe.rhsm.metadata.Polarion;
import com.redhat.qe.rhsm.metadata.Requirement;

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

    public static boolean pathExists(Path p) {
        return p.toFile().exists();
    }

    /**
     * Creates a Path to look up or create an xml description
     *
     * It uses this pattern:
     *
     * base/[project]/requirements|testcases/[class]/[methodName].xml as the path to the File
     *
     * @param base
     * @param meta
     * @return
     */
    public static <T> Path makeXmlPath(String base, Meta<T> meta) throws InvalidArgumentType {
        String proj;
        String type;
        if (meta.annotation instanceof Polarion) {
            Polarion p = (Polarion) meta.annotation;
            proj = p.projectID();
            type = "testcases";
        }
        else if (meta.annotation instanceof Requirement){
            Requirement r = (Requirement) meta.annotation;
            proj = r.project();
            type = "requirements";
        }
        else
            throw new InvalidArgumentType();

        Path basePath = Paths.get(base, proj, meta.className);
        String fullPath = Paths.get(basePath.toString(), meta.methName + ".xml").toString();
        Path path = Paths.get(fullPath);

        return path;
    }
}
