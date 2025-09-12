package com.officeduel.engine.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.officeduel.engine.model.GameState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public final class ReplaySerializer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static Replay fromGameState(GameState gs) {
        List<String> deckA = gs.getPlayerA().getDeck().stream().map(c -> c.cardId()).collect(Collectors.toList());
        List<String> deckB = gs.getPlayerB().getDeck().stream().map(c -> c.cardId()).collect(Collectors.toList());
        return new Replay(
                gs.getRng().seed(),
                deckA,
                deckB,
                List.copyOf(gs.getHistory()),
                gs.getPlayerA().getLifePoints(),
                gs.getPlayerB().getLifePoints()
        );
    }

    public static void writeTo(Path path, Replay replay) throws IOException {
        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(replay);
        Files.writeString(path, json);
    }
}


