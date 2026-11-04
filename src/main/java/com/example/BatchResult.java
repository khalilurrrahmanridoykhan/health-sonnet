package com.example;

import java.util.List;

public record BatchResult(
        String bundleJson,
        List<String> processedTrackedEntities,
        List<String> processedEvents,
        List<String> rejectedTrackedEntities,
        List<String> rejectedEvents,
        List<String> warnings
) {
}
