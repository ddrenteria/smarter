package com.officeduel.engine;

import com.officeduel.engine.cards.CardDefinitionSet;
import com.officeduel.engine.core.DeterministicRng;
import com.officeduel.engine.engine.CardIndex;
import com.officeduel.engine.engine.EffectResolver;
import com.officeduel.engine.engine.TurnEngine;
import com.officeduel.engine.loader.CardDefinitionLoader;
import com.officeduel.engine.model.GameState;
import com.officeduel.engine.model.MatchPlayer;
import com.officeduel.engine.model.PlayerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class DoubleEspressoBugTest {
    
    private CardDefinitionSet defs;
    private CardIndex index;
    private GameState gameState;
    private EffectResolver effectResolver;
    private TurnEngine turnEngine;
    private PlayerState playerA;
    private PlayerState playerB;
    
    @BeforeEach
    void setUp() throws Exception {
        Path cardsPath = Path.of("gameplay cards definition.txt");
        defs = CardDefinitionLoader.load(cardsPath);
        index = new CardIndex(defs);
        gameState = new GameState(new DeterministicRng(12345L));
        effectResolver = new EffectResolver(gameState, index);
        turnEngine = new TurnEngine(gameState, index);
        playerA = gameState.getPlayerA();
        playerB = gameState.getPlayerB();
    }
    
    @Test
    void testDoubleEspressoEffectCalculation() {
        System.out.println("=== Testing Double Espresso Effect Calculation ===");
        
        // Get Double Espresso card definition
        var doubleEspressoDef = defs.cards().stream()
            .filter(card -> "C010".equals(card.id()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Double Espresso card not found"));
        
        System.out.println("Double Espresso definition:");
        System.out.println("  Tiers count: " + doubleEspressoDef.tiers().size());
        for (int i = 0; i < doubleEspressoDef.tiers().size(); i++) {
            var tier = doubleEspressoDef.tiers().get(i);
            System.out.println("  Tier " + i + ": " + tier.actions().size() + " actions");
            for (var action : tier.actions()) {
                System.out.println("    Action: type=" + action.type() + 
                                 ", amount=" + action.amount() + 
                                 ", target=" + action.target());
            }
        }
        
        // Set up initial state: Player A has 1 Double Espresso in tableau
        gameState.getPlayerA().getTableau().add(new com.officeduel.engine.model.Cards("C010"));
        
        // Test tier calculation logic
        int existingCopies = (int) gameState.getPlayerA().getTableau().stream()
            .filter(c -> "C010".equals(c.cardId()))
            .count();
        
        int copies = existingCopies + 1; // Adding one more
        int tierIdx = Math.min(doubleEspressoDef.tiers().size(), Math.max(1, copies)) - 1;
        
        System.out.println("\nTier calculation:");
        System.out.println("  Existing copies: " + existingCopies);
        System.out.println("  Total copies: " + copies);
        System.out.println("  Tier index: " + tierIdx);
        
        var selectedTier = doubleEspressoDef.tiers().get(tierIdx);
        System.out.println("  Selected tier actions:");
        for (var action : selectedTier.actions()) {
            System.out.println("    Action: type=" + action.type() + 
                             ", amount=" + action.amount() + 
                             ", target=" + action.target());
        }
        
        // Test the actual effect application
        System.out.println("\n=== Testing Effect Application ===");
        int initialCounter = gameState.getSharedDotCounter();
        System.out.println("Initial counter: " + initialCounter);
        
        // Apply the effect manually
        effectResolver.applyActions(selectedTier.actions(), 
                                  new MatchPlayer(playerA),
                                  new MatchPlayer(playerB));
        
        int finalCounter = gameState.getSharedDotCounter();
        System.out.println("Final counter: " + finalCounter);
        System.out.println("Counter change: " + (finalCounter - initialCounter));
        
        // Verify the expected behavior
        // With 2 copies, should use tier 1: push 1 opponent
        // This should benefit Player B, so counter should move -1
        assertEquals(1, tierIdx, "Should use tier 1 (index 1) with 2 copies");
        assertEquals(-1, finalCounter - initialCounter, "Counter should move -1 (benefiting Player B)");
    }
    
    @Test
    void testDoubleEspressoTierCalculation() {
        System.out.println("\n=== Testing Double Espresso Tier Calculation Bug ===");
        
        // Set up the exact scenario from the logs
        // Player A tableau: [C011, C001, C005, C009, C002, C010]
        playerA.getTableau().add(new com.officeduel.engine.model.Cards("C011"));
        playerA.getTableau().add(new com.officeduel.engine.model.Cards("C001"));
        playerA.getTableau().add(new com.officeduel.engine.model.Cards("C005"));
        playerA.getTableau().add(new com.officeduel.engine.model.Cards("C009"));
        playerA.getTableau().add(new com.officeduel.engine.model.Cards("C002"));
        playerA.getTableau().add(new com.officeduel.engine.model.Cards("C010"));
        
        System.out.println("Player A tableau: " + playerA.getTableau().stream().map(c -> c.cardId()).toList());
        
        // Get Double Espresso card definition
        var doubleEspressoDef = defs.cards().stream()
            .filter(card -> "C010".equals(card.id()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Double Espresso card not found"));
        
        // Count existing C010 copies for Player A
        int existingC010 = (int) playerA.getTableau().stream()
            .filter(c -> "C010".equals(c.cardId()))
            .count();
        System.out.println("Player A existing C010 copies: " + existingC010);
        
        // Test tier calculation logic (same as TurnEngine.resolveRecruit)
        int copies = existingC010 + 1; // Adding one more
        int tierIdx = Math.min(doubleEspressoDef.tiers().size(), Math.max(1, copies)) - 1;
        
        System.out.println("Tier calculation:");
        System.out.println("  Total copies after adding: " + copies);
        System.out.println("  Tier index: " + tierIdx);
        System.out.println("  Tiers available: " + doubleEspressoDef.tiers().size());
        
        var selectedTier = doubleEspressoDef.tiers().get(tierIdx);
        System.out.println("  Selected tier actions:");
        for (var action : selectedTier.actions()) {
            System.out.println("    Action: type=" + action.type() + 
                             ", amount=" + action.amount() + 
                             ", target=" + action.target());
        }
        
        // Test the actual effect application
        System.out.println("\n=== Testing Effect Application ===");
        int initialCounter = gameState.getSharedDotCounter();
        System.out.println("Initial counter: " + initialCounter);
        
        // Apply the effect manually
        effectResolver.applyActions(selectedTier.actions(), 
                                  new MatchPlayer(playerA),
                                  new MatchPlayer(playerB));
        
        int finalCounter = gameState.getSharedDotCounter();
        System.out.println("Final counter: " + finalCounter);
        System.out.println("Counter change: " + (finalCounter - initialCounter));
        
        // Print recent effects for debugging
        System.out.println("\nRecent effects:");
        for (var effect : gameState.getRecentEffects()) {
            System.out.println("  " + effect.playerName() + ": " + effect.effectDescription());
        }
        
        // Verify the expected behavior
        // With 2 copies, should use tier 1: push 1 opponent
        // This should benefit Player B, so counter should move -1
        assertEquals(1, tierIdx, "Should use tier 1 (index 1) with 2 copies");
        
        // The bug we're investigating: logs show "Push 2" but should be "Push 1"
        // Let's see what actually happens
        var firstAction = selectedTier.actions().get(0);
        System.out.println("\nBug investigation:");
        System.out.println("  Expected amount from JSON: 1");
        System.out.println("  Actual amount from action: " + firstAction.amount());
        System.out.println("  Expected target: opponent");
        System.out.println("  Actual target: " + firstAction.target());
        
        assertEquals("push", firstAction.type(), "Action should be push");
        assertEquals(1, firstAction.amount(), "Amount should be 1, not 2 (this is the bug!)");
        assertEquals("opponent", firstAction.target(), "Target should be opponent");
    }
}
