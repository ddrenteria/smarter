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

public class DotCounterTest {
    @Test
    public void testDotCounterBasicLogic() throws Exception {
        Path cardsPath = Path.of("gameplay cards definition.txt");
        CardDefinitionSet defs = CardDefinitionLoader.load(cardsPath);
        CardIndex index = new CardIndex(defs);
        GameState gs = new GameState(new DeterministicRng(1L));
        EffectResolver er = new EffectResolver(gs, index);
        
        PlayerState playerA = gs.getPlayerA();
        PlayerState playerB = gs.getPlayerB();
        
        // Test initial state
        assertEquals(0, gs.getSharedDotCounter());
        
        // Test damage to A decreases dots
        var damageToA = new CardDefinitionSet.Action("push", "self", -3, null, null, null, null, null, null, null, null, null, null, null, null, null);
        er.applyActions(List.of(damageToA), new MatchPlayer(playerA), new MatchPlayer(playerB));
        assertEquals(-3, gs.getSharedDotCounter());
        
        // Test damage to B increases dots (favor A)
        var damageToB = new CardDefinitionSet.Action("push", "opponent", 2, null, null, null, null, null, null, null, null, null, null, null, null, null);
        er.applyActions(List.of(damageToB), new MatchPlayer(playerA), new MatchPlayer(playerB));
        assertEquals(-1, gs.getSharedDotCounter()); // -3 + 2 = -1
        
        // Test heal to A increases dots
        var healToA = new CardDefinitionSet.Action("push", "self", 4, null, null, null, null, null, null, null, null, null, null, null, null, null);
        er.applyActions(List.of(healToA), new MatchPlayer(playerA), new MatchPlayer(playerB));
        assertEquals(3, gs.getSharedDotCounter()); // -1 + 4 = 3
        
        // Test heal to B decreases dots (favor B)
        var healToB = new CardDefinitionSet.Action("push", "opponent", -1, null, null, null, null, null, null, null, null, null, null, null, null, null);
        er.applyActions(List.of(healToB), new MatchPlayer(playerA), new MatchPlayer(playerB));
        assertEquals(2, gs.getSharedDotCounter()); // 3 - 1 = 2
    }
    
    @Test
    public void testDotCounterClamping() throws Exception {
        Path cardsPath = Path.of("gameplay cards definition.txt");
        CardDefinitionSet defs = CardDefinitionLoader.load(cardsPath);
        CardIndex index = new CardIndex(defs);
        GameState gs = new GameState(new DeterministicRng(1L));
        EffectResolver er = new EffectResolver(gs, index);
        
        PlayerState playerA = gs.getPlayerA();
        PlayerState playerB = gs.getPlayerB();
        
        // Test positive clamping at 5
        gs.setSharedDotCounter(4);
        var damageToB = new CardDefinitionSet.Action("push", "opponent", 3, null, null, null, null, null, null, null, null, null, null, null, null, null);
        er.applyActions(List.of(damageToB), new MatchPlayer(playerA), new MatchPlayer(playerB));
        assertEquals(5, gs.getSharedDotCounter()); // 4 + 3 = 7, but clamped to 5
        
        // Test negative clamping at -5
        gs.setSharedDotCounter(-4);
        var damageToA = new CardDefinitionSet.Action("push", "self", -3, null, null, null, null, null, null, null, null, null, null, null, null, null);
        er.applyActions(List.of(damageToA), new MatchPlayer(playerA), new MatchPlayer(playerB));
        assertEquals(-5, gs.getSharedDotCounter()); // -4 - 3 = -7, but clamped to -5
    }
}

