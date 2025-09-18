package com.officeduel.cli;

import com.officeduel.engine.cards.CardDefinitionSet;
import com.officeduel.engine.loader.CardDefinitionLoader;
import com.officeduel.engine.core.DeterministicRng;
import com.officeduel.engine.engine.CardIndex;
import com.officeduel.engine.engine.TurnEngine;
import com.officeduel.engine.model.Cards;
import com.officeduel.engine.model.GameState;
import com.officeduel.engine.model.PlayerState;
import com.officeduel.engine.telemetry.StdoutTelemetry;
import com.officeduel.engine.telemetry.Telemetry;
import com.officeduel.engine.replay.ReplaySerializer;

import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        try {
            Path repoRoot = Path.of("");
            Path cardsPath = repoRoot.resolve("gameplay cards definition.txt");
            CardDefinitionSet defs = CardDefinitionLoader.load(cardsPath);
            System.out.println("Loaded cards: " + defs.cards().size());

            long seed = args.length > 0 ? Long.parseLong(args[0]) : 123456789L;
            DeterministicRng rng = new DeterministicRng(seed);
            GameState gs = new GameState(rng);

            // Naive deck: 50 random cards for each player from definition list
            for (int i = 0; i < 50; i++) {
                int idxA = rng.nextInt(defs.cards().size());
                int idxB = rng.nextInt(defs.cards().size());
                gs.getPlayerA().getDeck().addFirst(new Cards(defs.cards().get(idxA).id()));
                gs.getPlayerB().getDeck().addFirst(new Cards(defs.cards().get(idxB).id()));
            }

            CardIndex index = new CardIndex(defs);
            TurnEngine engine = new TurnEngine(gs, index);
            engine.startMatch();

            if (args.length > 1) {
                int matches = Integer.parseInt(args[1]);
                int aWins = 0, bWins = 0;
                for (int m = 0; m < matches; m++) {
                    long s = seed + m;
                    DeterministicRng rr = new DeterministicRng(s);
                    GameState g = new GameState(rr);
                    // decks
                    for (int i = 0; i < 20; i++) {
                        int idxA = rr.nextInt(defs.cards().size());
                        int idxB = rr.nextInt(defs.cards().size());
                        g.getPlayerA().getDeck().addFirst(new Cards(defs.cards().get(idxA).id()));
                        g.getPlayerB().getDeck().addFirst(new Cards(defs.cards().get(idxB).id()));
                    }
                    TurnEngine eng = new TurnEngine(g, index);
                    eng.startMatch();
                    int t = 0;
                    while (g.getPlayerA().getLifePoints() > 0 && g.getPlayerB().getLifePoints() > 0 && t < 200) {
                        // Auto turn removed
                        t++;
                    }
                    if (g.getPlayerA().getLifePoints() > 0) aWins++; else bWins++;
                }
                System.out.println("Sim: A=" + aWins + " B=" + bWins + " out of " + matches);
            } else {
                int turns = 0;
                while (gs.getPlayerA().getLifePoints() > 0 && gs.getPlayerB().getLifePoints() > 0 && turns < 200) {
                    // Auto turn removed
                    turns++;
                }
                System.out.println("Match ended in " + turns + " turns. LP A=" + gs.getPlayerA().getLifePoints() + ", B=" + gs.getPlayerB().getLifePoints());
                if (args.length > 2) {
                    java.nio.file.Path out = java.nio.file.Path.of(args[2]);
                    var replay = ReplaySerializer.fromGameState(gs);
                    ReplaySerializer.writeTo(out, replay);
                    System.out.println("Wrote replay to " + out);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}


