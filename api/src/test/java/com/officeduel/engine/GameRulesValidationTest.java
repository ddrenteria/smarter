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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite to validate all game rules and mechanics work correctly.
 * This includes dot counter logic, card effects, turn mechanics, and win conditions.
 */
public class GameRulesValidationTest {
    
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
    void testDotCounterInitialState() {
        // Game should start with dot counter at 0
        assertEquals(0, gameState.getSharedDotCounter());
    }
    
    @Test
    void testDotCounterPushMechanics() {
        // Test that push effects work correctly for both players
        // When Player A gets positive push, dots should increase (favor A)
        var pushToA = new CardDefinitionSet.Action("push", "self", 2, null, null, null, null, null, null, null, null, null, null, null, null, null);
        effectResolver.applyActions(List.of(pushToA), new MatchPlayer(playerA), new MatchPlayer(playerB));
        assertEquals(2, gameState.getSharedDotCounter());
        
        // When Player B gets positive push, dots should decrease (favor B)
        var pushToB = new CardDefinitionSet.Action("push", "opponent", 3, null, null, null, null, null, null, null, null, null, null, null, null, null);
        effectResolver.applyActions(List.of(pushToB), new MatchPlayer(playerA), new MatchPlayer(playerB));
        assertEquals(-1, gameState.getSharedDotCounter()); // 2 - 3 = -1
        
        // When Player A gets negative push, dots should decrease (harm A)
        var negativePushToA = new CardDefinitionSet.Action("push", "self", -2, null, null, null, null, null, null, null, null, null, null, null, null, null);
        effectResolver.applyActions(List.of(negativePushToA), new MatchPlayer(playerA), new MatchPlayer(playerB));
        assertEquals(-3, gameState.getSharedDotCounter()); // -1 - 2 = -3
        
        // When Player B gets negative push, dots should increase (harm B)
        var negativePushToB = new CardDefinitionSet.Action("push", "opponent", -1, null, null, null, null, null, null, null, null, null, null, null, null, null);
        effectResolver.applyActions(List.of(negativePushToB), new MatchPlayer(playerA), new MatchPlayer(playerB));
        assertEquals(-2, gameState.getSharedDotCounter()); // -3 + 1 = -2
    }
    
    @Test
    void testDotCounterClamping() {
        // Test that dot counter is properly clamped between -5 and 5
        gameState.setSharedDotCounter(4);
        
        // Push that would exceed +5 should be clamped
        var largePush = new CardDefinitionSet.Action("push", "self", 10, null, null, null, null, null, null, null, null, null, null, null, null, null);
        effectResolver.applyActions(List.of(largePush), new MatchPlayer(playerA), new MatchPlayer(playerB));
        assertEquals(5, gameState.getSharedDotCounter());
        
        // Reset and test negative clamping
        gameState.setSharedDotCounter(-4);
        var largeNegativePush = new CardDefinitionSet.Action("push", "self", -10, null, null, null, null, null, null, null, null, null, null, null, null, null);
        effectResolver.applyActions(List.of(largeNegativePush), new MatchPlayer(playerA), new MatchPlayer(playerB));
        assertEquals(-5, gameState.getSharedDotCounter());
    }
    
    @Test
    void testWinConditions() {
        // Test that Player A wins when dots reach +5
        gameState.setSharedDotCounter(4);
        var winningPush = new CardDefinitionSet.Action("push", "self", 2, null, null, null, null, null, null, null, null, null, null, null, null, null);
        effectResolver.applyActions(List.of(winningPush), new MatchPlayer(playerA), new MatchPlayer(playerB));
        assertEquals(5, gameState.getSharedDotCounter());
        // Note: Win condition checking is done in TurnEngine, not EffectResolver
        
        // Test that Player B wins when dots reach -5
        gameState.setSharedDotCounter(-4);
        var losingPush = new CardDefinitionSet.Action("push", "self", -2, null, null, null, null, null, null, null, null, null, null, null, null, null);
        effectResolver.applyActions(List.of(losingPush), new MatchPlayer(playerA), new MatchPlayer(playerB));
        assertEquals(-5, gameState.getSharedDotCounter());
    }
    
    @Test
    void testDamageEffects() {
        // Test damage effects work correctly
        var damageAction = new CardDefinitionSet.Action("damage", "opponent", 3, null, null, null, null, null, null, null, null, null, null, null, null, null);
        effectResolver.applyActions(List.of(damageAction), new MatchPlayer(playerA), new MatchPlayer(playerB));
        
        // Player B should take damage (dots should favor A)
        assertEquals(3, gameState.getSharedDotCounter());
    }
    
    @Test
    void testHandSizeManagement() {
        // Test that hand size is properly managed (should start at 4)
        assertEquals(4, playerA.getMaxHandSize());
        assertEquals(4, playerB.getMaxHandSize());
        
        // Test drawing cards up to hand size
        turnEngine.startMatch();
        assertTrue(playerA.getHand().size() <= 4);
        assertTrue(playerB.getHand().size() <= 4);
    }
    
    @Test
    void testCardEffectTargeting() {
        // Test that card effects target the correct player
        // When Player A plays a card that affects "self", it should affect Player A
        var selfEffect = new CardDefinitionSet.Action("push", "self", 2, null, null, null, null, null, null, null, null, null, null, null, null, null);
        int initialDots = gameState.getSharedDotCounter();
        effectResolver.applyActions(List.of(selfEffect), new MatchPlayer(playerA), new MatchPlayer(playerB));
        assertEquals(initialDots + 2, gameState.getSharedDotCounter()); // Should favor A
        
        // When Player A plays a card that affects "opponent", it should affect Player B
        var opponentEffect = new CardDefinitionSet.Action("push", "opponent", 1, null, null, null, null, null, null, null, null, null, null, null, null, null);
        initialDots = gameState.getSharedDotCounter();
        effectResolver.applyActions(List.of(opponentEffect), new MatchPlayer(playerA), new MatchPlayer(playerB));
        assertEquals(initialDots - 1, gameState.getSharedDotCounter()); // Should favor B
    }
    
    @Test
    void testTurnRotation() {
        // Test that turns rotate correctly between players
        turnEngine.startMatch();
        
        // Game should start with Player A as active
        assertEquals(0, gameState.getActivePlayerIndex());
        
        // Play a complete turn using playTurnAuto (handles everything automatically)
        gameState.setPhase(GameState.Phase.PLAY_TWO_CARDS);
        turnEngine.playTurnAuto(); // Player A selects cards, opponent picks, effects resolve, turn ends
        
        // After playTurnAuto, it should be Player B's turn and back to PLAY_TWO_CARDS phase
        assertEquals(1, gameState.getActivePlayerIndex());
        assertEquals(GameState.Phase.PLAY_TWO_CARDS, gameState.getPhase());
        
        // Play another complete turn
        turnEngine.playTurnAuto(); // Player B selects cards, opponent picks, effects resolve, turn ends
        
        // Should now be back to Player A's turn
        assertEquals(0, gameState.getActivePlayerIndex());
        assertEquals(GameState.Phase.PLAY_TWO_CARDS, gameState.getPhase());
    }
    
    @Test
    void testCardSelectionRestrictions() {
        // Test that in PLAY_TWO_CARDS phase, both cards are different
        turnEngine.startMatch();
        
        // Test manual card selection with different cards
        gameState.setPhase(GameState.Phase.PLAY_TWO_CARDS);
        turnEngine.playTurnManual("C001", "C002"); // Select two different cards
        
        // Verify that two different cards were selected
        assertNotNull(gameState.getFaceUpCardId());
        assertNotNull(gameState.getFaceDownCardId());
        assertNotEquals(gameState.getFaceUpCardId(), gameState.getFaceDownCardId());
        
        // Move to pick phase and test selection
        gameState.setPhase(GameState.Phase.OPPONENT_PICK);
        assertDoesNotThrow(() -> {
            turnEngine.playTurnWithChoice(gameState.getFaceUpCardId(), gameState.getFaceDownCardId());
        });
        
        // Note: The actual prevention of same cards happens in the frontend/API layer
        // The engine itself doesn't prevent same cards, but the game rules should
    }
    
    @Test
    void testRecentlyAddedCardsTracking() {
        // Test that recently added cards are properly tracked
        turnEngine.startMatch();
        gameState.setPhase(GameState.Phase.PLAY_TWO_CARDS);
        
        // Play a turn to add cards to tableau
        turnEngine.playTurnAuto();
        gameState.setPhase(GameState.Phase.OPPONENT_PICK);
        turnEngine.playTurnWithChoice("C001", "C002");
        
        // Should have recently added cards tracked
        assertNotNull(gameState.getRecentlyAddedCardsA());
        assertNotNull(gameState.getRecentlyAddedCardsB());
    }
    
    @Test
    void testEffectFeedbackGeneration() {
        // Test that effect feedback is generated for recent effects
        var pushAction = new CardDefinitionSet.Action("push", "self", 2, null, null, null, null, null, null, null, null, null, null, null, null, null);
        effectResolver.applyActions(List.of(pushAction), new MatchPlayer(playerA), new MatchPlayer(playerB));
        
        // Should have recent effects
        assertNotNull(gameState.getRecentEffects());
        assertFalse(gameState.getRecentEffects().isEmpty());
    }
    
    @Test
    void testStatusEffects() {
        // Test that status effects are properly applied and tracked
        // Note: Status effects are not fully implemented in the current system
        // This test is commented out until status system is implemented
        // var statusAction = new CardDefinitionSet.Action("status", "self", 0, "poisoned", null, null, null, null, null, null, null, null, null, null, null, null);
        // effectResolver.applyActions(List.of(statusAction), new MatchPlayer(playerA), new MatchPlayer(playerB));
        
        // Player A should have the poisoned status
        // assertTrue(playerA.getStatuses().hasStatus("poisoned"));
    }
    
    @Test
    void testCardRevealMechanics() {
        // Test card reveal effects work correctly
        var revealAction = new CardDefinitionSet.Action("reveal_random_cards", "opponent", 1, null, null, null, null, null, null, null, null, null, null, null, null, null);
        effectResolver.applyActions(List.of(revealAction), new MatchPlayer(playerA), new MatchPlayer(playerB));
        
        // Should have revealed cards tracked
        assertNotNull(gameState.getRevealedCardsB());
    }
    
    @Test
    void testMultipleEffectResolution() {
        // Test that multiple effects are resolved correctly in sequence
        var effect1 = new CardDefinitionSet.Action("push", "self", 2, null, null, null, null, null, null, null, null, null, null, null, null, null);
        var effect2 = new CardDefinitionSet.Action("push", "opponent", 1, null, null, null, null, null, null, null, null, null, null, null, null, null);
        var effect3 = new CardDefinitionSet.Action("damage", "self", 1, null, null, null, null, null, null, null, null, null, null, null, null, null);
        
        List<CardDefinitionSet.Action> actions = List.of(effect1, effect2, effect3);
        effectResolver.applyActions(actions, new MatchPlayer(playerA), new MatchPlayer(playerB));
        
        // Net effect: +2 (effect1) - 1 (effect2) - 1 (effect3) = 0
        assertEquals(0, gameState.getSharedDotCounter());
    }
    
    @Test
    void testGameStateConsistency() {
        // Test that game state remains consistent after various operations
        turnEngine.startMatch();
        
        // Initial state should be consistent
        assertTrue(gameState.getSharedDotCounter() >= -5 && gameState.getSharedDotCounter() <= 5);
        assertTrue(playerA.getHand().size() <= playerA.getMaxHandSize());
        assertTrue(playerB.getHand().size() <= playerB.getMaxHandSize());
        
        // After playing effects, state should remain consistent
        var pushAction = new CardDefinitionSet.Action("push", "self", 3, null, null, null, null, null, null, null, null, null, null, null, null, null);
        effectResolver.applyActions(List.of(pushAction), new MatchPlayer(playerA), new MatchPlayer(playerB));
        
        assertTrue(gameState.getSharedDotCounter() >= -5 && gameState.getSharedDotCounter() <= 5);
        assertTrue(playerA.getHand().size() <= playerA.getMaxHandSize());
        assertTrue(playerB.getHand().size() <= playerB.getMaxHandSize());
    }
    
    @Test
    void testEdgeCaseZeroEffects() {
        // Test that zero-value effects don't cause issues
        var zeroPush = new CardDefinitionSet.Action("push", "self", 0, null, null, null, null, null, null, null, null, null, null, null, null, null);
        int initialDots = gameState.getSharedDotCounter();
        effectResolver.applyActions(List.of(zeroPush), new MatchPlayer(playerA), new MatchPlayer(playerB));
        assertEquals(initialDots, gameState.getSharedDotCounter());
    }
    
    @Test
    void testOppositePlayerEffects() {
        // Test that effects work correctly when Player B is the source
        var pushFromB = new CardDefinitionSet.Action("push", "self", 2, null, null, null, null, null, null, null, null, null, null, null, null, null);
        effectResolver.applyActions(List.of(pushFromB), new MatchPlayer(playerB), new MatchPlayer(playerA));
        
        // When Player B gets +2 push, dots should decrease (favor B)
        assertEquals(-2, gameState.getSharedDotCounter());
    }
}
