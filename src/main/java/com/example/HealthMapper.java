package com.example;

import com.datasonnet.Mapper;
import com.datasonnet.document.DefaultDocument;
import com.datasonnet.document.MediaTypes;
import java.nio.file.Files;
import java.nio.file.Paths;

public class HealthMapper {

    /** Transforms a health‑JSON payload using the DataSonnet script. */
    public static String map(String inputJson) throws Exception {
        String script = Files.readString(
                Paths.get(HealthMapper.class.getClassLoader()
                .getResource("health-mapping.ds").toURI()));
        return new Mapper(script).transform(
                new DefaultDocument<>(inputJson, MediaTypes.APPLICATION_JSON)).getContent();
    }

}
