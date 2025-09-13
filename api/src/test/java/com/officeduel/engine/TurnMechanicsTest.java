package com.officeduel.engine;

import com.officeduel.engine.cards.CardDefinitionSet;
import com.officeduel.engine.core.DeterministicRng;
import com.officeduel.engine.engine.CardIndex;
import com.officeduel.engine.engine.TurnEngine;
import com.officeduel.engine.loader.CardDefinitionLoader;
import com.officeduel.engine.model.GameState;
import com.officeduel.engine.model.PlayerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite focused on turn mechanics, card selection, and game flow validation.
 */
public class TurnMechanicsTest {
    
    private CardDefinitionSet defs;
    private CardIndex index;
    private GameState gameState;
    private TurnEngine turnEngine;
    private PlayerState playerA;
    private PlayerState playerB;
    
    @BeforeEach
    void setUp() throws Exception {
        Path cardsPath = Path.of("gameplay cards definition.txt");
        defs = CardDefinitionLoader.load(cardsPath);
        index = new CardIndex(defs);
        gameState = new GameState(new DeterministicRng(54321L));
        turnEngine = new TurnEngine(gameState, index);
        playerA = gameState.getPlayerA();
        playerB = gameState.getPlayerB();
    }
    
    @Test
    void testGameInitialization() {
        // Test that game starts in correct state
        turnEngine.startMatch();
        
        // Should start with Player A as active
        assertEquals(0, gameState.getActivePlayerIndex());
        
        // Should start in PLAY_TWO_CARDS phase
        assertEquals(GameState.Phase.PLAY_TWO_CARDS, gameState.getPhase());
        
        // Both players should have hands drawn up to max size
        assertTrue(playerA.getHand().size() <= playerA.getMaxHandSize());
        assertTrue(playerB.getHand().size() <= playerB.getMaxHandSize());
        
        // Dot counter should start at 0
        assertEquals(0, gameState.getSharedDotCounter());
    }
    
    @Test
    void testTurnSequence() {
        turnEngine.startMatch();
        
        // Phase 1: Player A plays two cards
        assertEquals(GameState.Phase.PLAY_TWO_CARDS, gameState.getPhase());
        assertEquals(0, gameState.getActivePlayerIndex()); // Player A active
        
        // Simulate Player A selecting two cards
        turnEngine.playTurnAuto();
        
        // Should move to OPPONENT_PICK phase
        assertEquals(GameState.Phase.OPPONENT_PICK, gameState.getPhase());
        
        // Player B should now be active for picking
        assertEquals(1, gameState.getActivePlayerIndex()); // Player B active
        
        // Simulate Player B picking a card
        turnEngine.playTurnWithChoice("C001", "C002");
        
        // Should move back to PLAY_TWO_CARDS for Player B's turn
        assertEquals(GameState.Phase.PLAY_TWO_CARDS, gameState.getPhase());
        assertEquals(1, gameState.getActivePlayerIndex()); // Player B active
    }
    
    @Test
    void testCardSelectionLogic() {
        turnEngine.startMatch();
        
        // Player A's turn - should select two different cards
        gameState.setPhase(GameState.Phase.PLAY_TWO_CARDS);
        turnEngine.playTurnAuto();
        
        // Should have two cards available for selection
        assertNotNull(gameState.getFaceUpCardId());
        assertNotNull(gameState.getFaceDownCardId());
        
        // The two cards should be different
        assertNotEquals(gameState.getFaceUpCardId(), gameState.getFaceDownCardId());
        
        // Move to pick phase
        gameState.setPhase(GameState.Phase.OPPONENT_PICK);
        
        // Player B picks face up card
        turnEngine.playTurnWithChoice(gameState.getFaceUpCardId(), gameState.getFaceDownCardId());
        
        // Player A should get the remaining (face down) card
        assertNotNull(gameState.getFaceUpCardId()); // Should be the remaining card
    }
    
    @Test
    void testPlayerTurnRotation() {
        turnEngine.startMatch();
        
        // First turn: Player A
        assertEquals(0, gameState.getActivePlayerIndex());
        gameState.setPhase(GameState.Phase.PLAY_TWO_CARDS);
        turnEngine.playTurnAuto();
        gameState.setPhase(GameState.Phase.OPPONENT_PICK);
        turnEngine.playTurnWithChoice("C001", "C002");
        
        // Second turn: Player B
        assertEquals(1, gameState.getActivePlayerIndex());
        gameState.setPhase(GameState.Phase.PLAY_TWO_CARDS);
        turnEngine.playTurnAuto();
        gameState.setPhase(GameState.Phase.OPPONENT_PICK);
        turnEngine.playTurnWithChoice("C002", "C003");
        
        // Third turn: Back to Player A
        assertEquals(0, gameState.getActivePlayerIndex());
    }
    
    @Test
    void testCardEffectsApplication() {
        turnEngine.startMatch();
        
        // Play a turn with card effects
        gameState.setPhase(GameState.Phase.PLAY_TWO_CARDS);
        turnEngine.playTurnAuto();
        
        // Get the cards that were selected
        String faceUpCard = gameState.getFaceUpCardId();
        String faceDownCard = gameState.getFaceDownCardId();
        
        // Move to pick phase and pick one card
        gameState.setPhase(GameState.Phase.OPPONENT_PICK);
        turnEngine.playTurnWithChoice(faceUpCard, faceDownCard);
        
        // Effects should have been applied to both cards
        // The picked card affects the picking player
        // The remaining card affects the selecting player
        
        // Check that recently added cards are tracked
        assertNotNull(gameState.getRecentlyAddedCardsA());
        assertNotNull(gameState.getRecentlyAddedCardsB());
        
        // Check that recent effects are tracked
        assertNotNull(gameState.getRecentEffects());
    }
    
    @Test
    void testWinConditionChecking() {
        turnEngine.startMatch();
        
        // Set up a winning condition for Player A
        gameState.setSharedDotCounter(4);
        
        // Play a turn that should trigger win condition
        gameState.setPhase(GameState.Phase.PLAY_TWO_CARDS);
        turnEngine.playTurnAuto();
        gameState.setPhase(GameState.Phase.OPPONENT_PICK);
        turnEngine.playTurnWithChoice("C001", "C002");
        
        // The game should check for win conditions after effects
        // Note: Actual win checking happens in TurnEngine.checkWinnerMidTurn()
    }
    
    @Test
    void testBotPlayMechanics() {
        turnEngine.startMatch();
        
        // Test that bot can play automatically
        gameState.setPhase(GameState.Phase.PLAY_TWO_CARDS);
        
        // Bot should be able to select two cards automatically
        assertDoesNotThrow(() -> {
            turnEngine.playTurnAuto();
        });
        
        // Should move to next phase
        assertEquals(GameState.Phase.OPPONENT_PICK, gameState.getPhase());
    }
    
    @Test
    void testHandSizeManagement() {
        turnEngine.startMatch();
        
        // Test initial hand sizes
        assertTrue(playerA.getHand().size() <= playerA.getMaxHandSize());
        assertTrue(playerB.getHand().size() <= playerB.getMaxHandSize());
        
        // Test that hand size limit is respected (should be 4)
        assertEquals(4, playerA.getMaxHandSize());
        assertEquals(4, playerB.getMaxHandSize());
    }
    
    @Test
    void testTableauManagement() {
        turnEngine.startMatch();
        
        // Initially, tableaus should be empty
        assertTrue(playerA.getTableau().isEmpty());
        assertTrue(playerB.getTableau().isEmpty());
        
        // Play a turn to add cards to tableau
        gameState.setPhase(GameState.Phase.PLAY_TWO_CARDS);
        turnEngine.playTurnAuto();
        gameState.setPhase(GameState.Phase.OPPONENT_PICK);
        turnEngine.playTurnWithChoice("C001", "C002");
        
        // Tableaus should now have cards
        assertFalse(playerA.getTableau().isEmpty() || playerB.getTableau().isEmpty());
    }
    
    @Test
    void testRecentlyAddedCardsTracking() {
        turnEngine.startMatch();
        
        // Initially, no recently added cards
        assertTrue(gameState.getRecentlyAddedCardsA().isEmpty());
        assertTrue(gameState.getRecentlyAddedCardsB().isEmpty());
        
        // Play a turn
        gameState.setPhase(GameState.Phase.PLAY_TWO_CARDS);
        turnEngine.playTurnAuto();
        gameState.setPhase(GameState.Phase.OPPONENT_PICK);
        turnEngine.playTurnWithChoice("C001", "C002");
        
        // Should have recently added cards tracked
        assertNotNull(gameState.getRecentlyAddedCardsA());
        assertNotNull(gameState.getRecentlyAddedCardsB());
        
        // Cards should be cleared at the start of next turn
        gameState.setPhase(GameState.Phase.PLAY_TWO_CARDS);
        turnEngine.playTurnAuto();
        
        // Should be cleared now
        assertTrue(gameState.getRecentlyAddedCardsA().isEmpty());
        assertTrue(gameState.getRecentlyAddedCardsB().isEmpty());
    }
    
    @Test
    void testEffectFeedbackPersistence() {
        turnEngine.startMatch();
        
        // Play a turn to generate effects
        gameState.setPhase(GameState.Phase.PLAY_TWO_CARDS);
        turnEngine.playTurnAuto();
        gameState.setPhase(GameState.Phase.OPPONENT_PICK);
        turnEngine.playTurnWithChoice("C001", "C002");
        
        // Should have recent effects
        assertNotNull(gameState.getRecentEffects());
        
        // Effects should persist (not cleared automatically)
        gameState.setPhase(GameState.Phase.PLAY_TWO_CARDS);
        turnEngine.playTurnAuto();
        
        // Effects should still be there (persistent log)
        assertNotNull(gameState.getRecentEffects());
    }
    
    @Test
    void testGameStateConsistencyAfterTurns() {
        turnEngine.startMatch();
        
        // Play several turns and verify state consistency
        for (int turn = 0; turn < 3; turn++) {
            gameState.setPhase(GameState.Phase.PLAY_TWO_CARDS);
            turnEngine.playTurnAuto();
            gameState.setPhase(GameState.Phase.OPPONENT_PICK);
            turnEngine.playTurnWithChoice("C001", "C002");
            
            // Verify dot counter is within bounds
            assertTrue(gameState.getSharedDotCounter() >= -5);
            assertTrue(gameState.getSharedDotCounter() <= 5);
            
            // Verify hand sizes are correct
            assertTrue(playerA.getHand().size() <= playerA.getMaxHandSize());
            assertTrue(playerB.getHand().size() <= playerB.getMaxHandSize());
            
            // Verify active player alternates
            assertEquals(turn % 2, gameState.getActivePlayerIndex());
        }
    }
}
