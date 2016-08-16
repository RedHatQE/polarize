package com.redhat.qe.rhsm;

import com.redhat.qe.rhsm.exceptions.InvalidArgumentType;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Created by stoner on 8/16/16.
 */
public interface IFileHelper {
    static Optional<Path> makePath(String path) {
        Path p = null;
        try {
           p = Paths.get(path);
        } catch (InvalidPathException ip) {

        }
        return Optional.of(p);
    }

    static boolean pathExists(Path p) {
        return p.toFile().exists();
    }

    static Path
    makeXmlPath(String base, String project, String cName, String methName) throws InvalidArgumentType {
        String xmlname;
        xmlname = methName;
        if (xmlname == null || xmlname.equals(""))
            xmlname = cName;

        Path basePath = Paths.get(base, project, cName);
        String fullPath = Paths.get(basePath.toString(), xmlname + ".xml").toString();
        return Paths.get(fullPath);
    }

    static Path makeRequirementXmlPath(String base, String last) throws InvalidArgumentType {
        return Paths.get(base, last);
    }

    static Boolean makeDirs(Path path) {
        Path parent = path.getParent();
        return parent.toFile().mkdirs();
    }
}
