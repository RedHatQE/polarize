package com.github.redhatqe.polarize.reporter;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.camel.util.StringHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapLoader {
    /**
     * From the path pointing to a mapping.json file, create a Map from it
     *
     * The key to the Map is the unique name of a method (usually its fully qualified name), and the value is a Map
     * of the form Project - IdParams.  This second map is needed because one test method is often used across multiple
     * projects.
     *
     *
     * @param fpath path to a mapping.json file
     * @return an in-memory map of unique methodname - project - IdParams
     */
    public static Map<String, Map<String, IdParams>> loadMapping(File fpath) {
        ObjectMapper mapper = new ObjectMapper();

        Map<String, Map<String, IdParams>> mapped = new HashMap<>();
        try {
            if (fpath.exists()) {
                // First key is name, second is project, third is "id" or "parameters"
                JsonNode node = mapper.readTree(fpath);
                node.fields().forEachRemaining(entry -> {
                    String name = entry.getKey();
                    JsonNode projectToInner = entry.getValue();
                    Map<String, IdParams> innerMap = new HashMap<>();
                    projectToInner.fields().forEachRemaining(e -> {
                        String project = e.getKey();
                        JsonNode inner = e.getValue();
                        String id = inner.get("id").toString();

                        IdParams idp = new IdParams();
                        idp.id = StringHelper.removeQuotes(id);
                        JsonNode paramNode = inner.get("parameters");
                        if (paramNode instanceof ArrayNode) {
                            ArrayNode params = (ArrayNode) paramNode;
                            idp.parameters = params.findValuesAsText("parameters");
                            List<String> p = new ArrayList<>();
                            for(int i = 0; i < params.size(); i++) {
                                p.add(StringHelper.removeQuotes(params.get(i).toString()));
                            }
                            idp.parameters = p;
                        }
                        innerMap.put(project, idp);
                    });
                    mapped.put(name, innerMap);
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mapped;
    }
}
