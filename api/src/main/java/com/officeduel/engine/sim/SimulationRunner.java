package com.officeduel.engine.sim;

import com.officeduel.engine.cards.CardDefinitionSet;
import com.officeduel.engine.core.DeterministicRng;
import com.officeduel.engine.engine.CardIndex;
import com.officeduel.engine.engine.TurnEngine;
import com.officeduel.engine.loader.CardDefinitionLoader;
import com.officeduel.engine.model.Cards;
import com.officeduel.engine.model.GameState;

import java.nio.file.Path;

public final class SimulationRunner {
    public record Result(int aWins, int bWins, int matches) {}

    public static Result run(Path cardsPath, long seed, int matches) throws Exception {
        CardDefinitionSet defs = CardDefinitionLoader.load(cardsPath);
        CardIndex index = new CardIndex(defs);
        int aWins = 0, bWins = 0;
        for (int m = 0; m < matches; m++) {
            long s = seed + m;
            DeterministicRng rng = new DeterministicRng(s);
            GameState g = new GameState(rng);
            for (int i = 0; i < 20; i++) {
                int idxA = rng.nextInt(defs.cards().size());
                int idxB = rng.nextInt(defs.cards().size());
                g.getPlayerA().getDeck().addFirst(new Cards(defs.cards().get(idxA).id()));
                g.getPlayerB().getDeck().addFirst(new Cards(defs.cards().get(idxB).id()));
            }
            TurnEngine eng = new TurnEngine(g, index);
            eng.startMatch();
            int t = 0;
            while (g.getPlayerA().getLifePoints() > 0 && g.getPlayerB().getLifePoints() > 0 && t < 200) {
                // Auto turn removed - simulation disabled
                t++;
            }
            if (g.getPlayerA().getLifePoints() > 0) aWins++; else bWins++;
        }
        return new Result(aWins, bWins, matches);
    }
}


