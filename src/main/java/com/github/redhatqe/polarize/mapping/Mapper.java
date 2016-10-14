package com.github.redhatqe.polarize.mapping;

import java.util.Map;

/**
 * A POJO that represents a mapping of qualified method names -> { projectID: polarionID }
 */
public class Mapper {

    public class TCDef {
        public String xmlPath;
    }

    public Map<String, String> testmethods;
}
