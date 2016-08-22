package com.github.redhatqe.polarize;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.github.redhatqe.polarize.metadata.Meta;
import com.github.redhatqe.polarize.metadata.Requirement;
import com.github.redhatqe.polarize.metadata.TestDefinition;
import com.github.redhatqe.polarize.exceptions.InvalidArgumentType;

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

}
