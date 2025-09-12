package com.officeduel.engine.loader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.officeduel.engine.cards.CardDefinitionSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CardDefinitionLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private CardDefinitionLoader() {}

    public static CardDefinitionSet load(Path path) throws IOException {
        String content = Files.readString(path);
        CardDefinitionSet set = MAPPER.readValue(content, CardDefinitionSet.class);
        if (set.schemaVersion() <= 0) {
            throw new IllegalArgumentException("Invalid schema_version");
        }
        if (set.cards() == null || set.cards().isEmpty()) {
            throw new IllegalArgumentException("No cards defined");
        }
        return set;
    }
}


