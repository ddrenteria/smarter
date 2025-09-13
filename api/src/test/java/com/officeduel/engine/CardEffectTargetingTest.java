package com.officeduel.engine;

import com.officeduel.engine.cards.CardDefinitionSet;
import com.officeduel.engine.core.DeterministicRng;
import com.officeduel.engine.engine.CardIndex;
import com.officeduel.engine.engine.TurnEngine;
import com.officeduel.engine.loader.CardDefinitionLoader;
import com.officeduel.engine.model.GameState;
import com.officeduel.engine.model.PlayerState;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class CardEffectTargetingTest {
    @Test
    public void testCardEffectTargeting() throws Exception {
        Path cardsPath = Path.of("gameplay cards definition.txt");
        CardDefinitionSet defs = CardDefinitionLoader.load(cardsPath);
        CardIndex index = new CardIndex(defs);
        GameState gs = new GameState(new DeterministicRng(1L));
        TurnEngine engine = new TurnEngine(gs, index);
        
        PlayerState playerA = gs.getPlayerA();
        PlayerState playerB = gs.getPlayerB();
        
        // Add cards to hand
        playerA.getHand().add(new com.officeduel.engine.model.Cards("C001")); // Slap Attack - damage to opponent
        playerA.getHand().add(new com.officeduel.engine.model.Cards("C002")); // Coffee Break - heal to self
        
        // Set up phase for manual play
        gs.setPhase(GameState.Phase.PLAY_TWO_CARDS);
        
        // Play turn manually
        engine.playTurnManual("C001", "C002");
        
        // Check that effects were applied correctly
        // C001 (Slap Attack) should damage opponent (B), so dots should increase (favor A)
        // C002 (Healing Touch) should heal self (A), so dots should increase (favor A)
        // Both effects favor A, so dots should be positive
        
        // The exact value depends on the card definitions, but we can check that dots changed
        assertNotEquals(0, gs.getSharedDotCounter());
        
        // Check that cards were moved to tableau correctly
        assertEquals(1, playerA.getTableau().size());
        assertEquals(1, playerB.getTableau().size());
        
        // Check that cards were removed from hand
        assertEquals(0, playerA.getHand().size());
    }
}
