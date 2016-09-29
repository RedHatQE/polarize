package com.github.redhatqe.polarize;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;

import com.github.redhatqe.polarize.metadata.Meta;
import com.github.redhatqe.polarize.exceptions.InvalidArgumentType;
import com.github.redhatqe.polarize.metadata.QualifiedName;
import com.github.redhatqe.polarize.metadata.TestDefinition;

/**
 * Created by stoner on 7/7/16.
 */
public class FileHelper implements IFileHelper {

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
    public static <T> Path makeXmlPath(String base, Meta<T> meta, String projID) throws InvalidArgumentType {
        String xmlname;
        xmlname = meta.methName;
        String packClass = meta.packName;
        if (xmlname == null || xmlname.equals("")) {
            xmlname = meta.className;
        }
        else
            packClass += "." + meta.className;

        Path basePath = Paths.get(base, projID, packClass);
        String fullPath = Paths.get(basePath.toString(), xmlname + ".xml").toString();
        return Paths.get(fullPath);
    }

    public static <T> Path makeXmlPath(String base, Meta<T> meta) throws InvalidArgumentType {
        String xmlname;
        xmlname = meta.methName;
        String packClass = meta.packName;
        if (xmlname == null || xmlname.equals("")) {
            xmlname = meta.className;
        }
        else
            packClass += "." + meta.className;

        Path basePath = Paths.get(base, meta.project, packClass);
        String fullPath = Paths.get(basePath.toString(), xmlname + ".xml").toString();
        return Paths.get(fullPath);
    }

    /**
     * Given a qualified name like com.github.redhat.qe.polarize.Foo.barMethod returns package, class and method
     *
     * @param path
     * @return
     */
    public static QualifiedName getClassMethodFromDottedString(String path) throws Exception {
        QualifiedName qual = new QualifiedName();
        String[] dots = path.split("\\.");
        if (dots.length < 3)
            throw new Exception(String.format("%s not a valid dotted name. Must have package.class.methodname", path));

        qual.methName = dots[dots.length - 1];
        qual.className = dots[dots.length - 2];

        String[] pkg = new String[dots.length - 2];
        System.arraycopy(dots, 0, pkg, 0, pkg.length);
        qual.packName = Arrays.stream(pkg).reduce("", (acc, n) -> acc + "." + n).substring(1);
        return qual;
    }

    public static Optional<Path> getXmlPath(String base, String qualname, String projID) {
        Path path = null;
        try {
            QualifiedName qual = FileHelper.getClassMethodFromDottedString(qualname);
            Meta<TestDefinition> meta = new Meta<>();
            meta.project = projID;
            meta.className = qual.className;
            meta.packName = qual.packName;
            meta.methName = qual.methName;
            path = FileHelper.makeXmlPath(base, meta);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (path == null)
            return Optional.empty();
        else
            return Optional.of(path);
    }

    public static void main(String[] args) {
        try {
            QualifiedName q = FileHelper.getClassMethodFromDottedString("rhsm.cli.tests.Foo.barMathod");
        } catch (Exception e) {
            e.printStackTrace();
        }

        Optional<Path> p = FileHelper.getXmlPath("/home/stoner/Projects/testpolarize/testcases",
                                                 "com.github.redhatqe.rhsm.testpolarize.TestReq", "PLATTP");
        if(p.isPresent()) {
            Path path = p.get();
            System.out.println(path.toString());
        }
    }
}
