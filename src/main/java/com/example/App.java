package com.example;

import java.nio.file.Files;
import java.nio.file.Paths;

public class App
{
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Pass path to input JSON");
            System.exit(1);
        }

        String input = Files.readString(Paths.get(args[0]));
        BatchResult batchResult = BatchProcessor.process(input);
        String bundleJson = batchResult.bundleJson();

        System.out.println(bundleJson);
        if (!FhirBundleValidator.validate(bundleJson)) {
            System.exit(1);
        }
    }
}
