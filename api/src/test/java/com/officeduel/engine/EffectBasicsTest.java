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

public class EffectBasicsTest {
    @Test
    public void damageAndHealClamp() throws Exception {
        Path cardsPath = Path.of("gameplay cards definition.txt");
        CardDefinitionSet defs = CardDefinitionLoader.load(cardsPath);
        CardIndex index = new CardIndex(defs);
        GameState gs = new GameState(new DeterministicRng(1L), defs.rulesAssumptions().max_lp());
        PlayerState ps = gs.getPlayerA();
        PlayerState opp = gs.getPlayerB();
        ps.setLifePoints(index.maxLp());
        opp.setLifePoints(index.maxLp());
        EffectResolver er = new EffectResolver(gs, index);
        
        // Test dot system: damage to A should decrease dot counter
        var actDmg = new CardDefinitionSet.Action("damage", "self", 5, null, null, null, null, null, null, null, null);
        er.applyActions(List.of(actDmg), new MatchPlayer(ps), new MatchPlayer(opp));
        assertEquals(-5, gs.getSharedDotCounter()); // A takes damage = dots go down
        
        // Test heal: heal to A should increase dot counter
        var actHeal = new CardDefinitionSet.Action("heal", "self", 3, null, null, null, null, null, null, null, null);
        er.applyActions(List.of(actHeal), new MatchPlayer(ps), new MatchPlayer(opp));
        assertEquals(-2, gs.getSharedDotCounter()); // A heals = dots go up
        
        // Test overflow clamp: dots should be clamped between -5 and 5
        var actHealOverflow = new CardDefinitionSet.Action("heal", "self", 999, null, null, null, null, null, null, null, null);
        er.applyActions(List.of(actHealOverflow), new MatchPlayer(ps), new MatchPlayer(opp));
        assertEquals(5, gs.getSharedDotCounter()); // Should be clamped to 5
    }

    @Test
    public void determinismSeeded() throws Exception {
        Path cardsPath = Path.of("gameplay cards definition.txt");
        CardDefinitionSet defs = CardDefinitionLoader.load(cardsPath);
        CardIndex index = new CardIndex(defs);
        GameState g1 = new GameState(new DeterministicRng(42L), defs.rulesAssumptions().max_lp());
        GameState g2 = new GameState(new DeterministicRng(42L), defs.rulesAssumptions().max_lp());
        assertEquals(g1.getRng().seed(), g2.getRng().seed());
    }
}


