package com.officeduel.engine;

import com.officeduel.engine.cards.CardDefinitionSet;
import com.officeduel.engine.core.DeterministicRng;
import com.officeduel.engine.engine.CardIndex;
import com.officeduel.engine.engine.EffectResolver;
import com.officeduel.engine.loader.CardDefinitionLoader;
import com.officeduel.engine.model.GameState;
import com.officeduel.engine.model.MatchPlayer;
import com.officeduel.engine.model.PlayerState;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CopyAndStatusTest {
    @Test
    public void copyLastAppliesTwice() throws Exception {
        Path cardsPath = Path.of("gameplay cards definition.txt");
        CardDefinitionSet defs = CardDefinitionLoader.load(cardsPath);
        CardIndex index = new CardIndex(defs);
        GameState gs = new GameState(new DeterministicRng(7L), defs.rulesAssumptions().max_lp());
        PlayerState ps = gs.getPlayerA();
        PlayerState opp = gs.getPlayerB();
        ps.setLifePoints(index.maxLp());
        opp.setLifePoints(index.maxLp());
        EffectResolver er = new EffectResolver(gs, index);
        
        // Test dot system: damage to opponent (B) should increase dot counter
        var dmg = new CardDefinitionSet.Action("damage", "opponent", 2, null, null, null, null, null, null, null, null);
        er.applyActions(List.of(dmg), new MatchPlayer(ps), new MatchPlayer(opp));
        assertEquals(2, gs.getSharedDotCounter()); // B takes damage = dots go up (favor A)
        
        // Test copy: should apply damage twice more (total 3 times), but clamped to 5
        var copy = new CardDefinitionSet.Action("copy_last_card_effect", "self", null, null, null, null, null, null, 2, null, null);
        er.applyActions(List.of(copy), new MatchPlayer(ps), new MatchPlayer(opp));
        assertEquals(5, gs.getSharedDotCounter()); // 2 + 2 + 2 = 6, but clamped to 5
    }
}


