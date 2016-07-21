package com.redhat.qe.rhsm;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import com.redhat.qe.rhsm.exceptions.InvalidArgumentType;
import com.redhat.qe.rhsm.metadata.Meta;
import com.redhat.qe.rhsm.metadata.TestCase;
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
        String xmlname;
        if (meta.annotation instanceof TestCase) {
            TestCase p = (TestCase) meta.annotation;
            proj = p.projectID();
        }
        else if (meta.annotation instanceof Requirement){
            Requirement r = (Requirement) meta.annotation;
            proj = r.project();
        }
        else
            throw new InvalidArgumentType();

        xmlname = meta.methName;
        if (xmlname == null || xmlname.equals(""))
            xmlname = meta.className;

        Path basePath = Paths.get(base, proj, meta.className);
        String fullPath = Paths.get(basePath.toString(), xmlname + ".xml").toString();
        return Paths.get(fullPath);
    }

    public static Path makeRequirementXmlPath(String base, String last) throws InvalidArgumentType {
        return Paths.get(base, last);
    }

    public static Boolean makeDirs(Path path) {
        Path parent = path.getParent();
        return parent.toFile().mkdirs();
    }
}
