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

public class DuplicateCardTest {
    @Test
    public void testDuplicateCardRemoval() throws Exception {
        Path cardsPath = Path.of("gameplay cards definition.txt");
        CardDefinitionSet defs = CardDefinitionLoader.load(cardsPath);
        CardIndex index = new CardIndex(defs);
        GameState gs = new GameState(new DeterministicRng(1L));
        TurnEngine engine = new TurnEngine(gs, index);
        
        PlayerState playerA = gs.getPlayerA();
        
        // Add duplicate cards to hand (use real card IDs from the game)
        String cardId = "C001"; // Use a real card ID
        playerA.getHand().add(new com.officeduel.engine.model.Cards(cardId));
        playerA.getHand().add(new com.officeduel.engine.model.Cards(cardId));
        playerA.getHand().add(new com.officeduel.engine.model.Cards("C002")); // Use another real card ID
        
        // Verify we have 3 cards, 2 duplicates
        assertEquals(3, playerA.getHand().size());
        long duplicateCount = playerA.getHand().stream()
            .filter(c -> c.cardId().equals(cardId))
            .count();
        assertEquals(2, duplicateCount);
        
        // Play turn with duplicate cards
        engine.playTurnManual(cardId, "C002");
        
        // Verify only one duplicate was removed
        assertEquals(1, playerA.getHand().size());
        long remainingDuplicates = playerA.getHand().stream()
            .filter(c -> c.cardId().equals(cardId))
            .count();
        assertEquals(1, remainingDuplicates);
    }
}
