package com.github.redhatqe.byzantine.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;


public class Serializer {
    public static <T> void toJson(T cfg, String path) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer().withDefaultPrettyPrinter();
        writer.writeValue(new File(path), cfg);
    }

    public static <T> T fromJson(Class<T> cfg, File json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, cfg);
    }

    public static <T> void toYaml(T cfg, String path) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        ObjectWriter writer = mapper.writer().withDefaultPrettyPrinter();
        writer.writeValue(new File(path), cfg);
    }

    public static <T> T fromYaml(Class<T> cfg, File yaml) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return  mapper.readValue(yaml, cfg);
    }

}
