package com.officeduel.engine.replay;

import java.util.List;

public record Replay(
        long seed,
        List<String> deckA,
        List<String> deckB,
        List<String> history,
        int finalLpA,
        int finalLpB
) {}


