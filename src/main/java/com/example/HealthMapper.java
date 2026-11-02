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

    // Demo main – you can run `mvn exec:java` later
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Pass path to input JSON");
            System.exit(1);
        }
        String input = Files.readString(Paths.get(args[0]));
        System.out.println(map(input));
    }
}
