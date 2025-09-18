package com.officeduel.engine.model;

import com.officeduel.engine.core.DeterministicRng;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class GameState {
    public enum Phase { PLAY_TWO_CARDS, OPPONENT_PICK, RESOLUTION, END_STEP }

    private final DeterministicRng rng;
    private final PlayerState playerA;
    private final PlayerState playerB;
    private int activePlayerIndex = 0; // 0 -> A, 1 -> B
    private Phase phase = Phase.PLAY_TWO_CARDS;
    private final List<String> history = new ArrayList<>();
    private String faceUpCardId;
    private String faceDownCardId;
    private List<com.officeduel.engine.cards.CardDefinitionSet.Action> lastActionsAppliedPlayerA = new ArrayList<>();
    private List<com.officeduel.engine.cards.CardDefinitionSet.Action> lastActionsAppliedPlayerB = new ArrayList<>();

    private int sharedDotCounter = 0; // Shared counter: 0 = neutral, winPointsToReach = A wins, -winPointsToReach = B wins
    private int winPointsToReach = 5; // Configurable win condition
    
    // Effect feedback tracking
    private final List<EffectFeedback> recentEffects = new CopyOnWriteArrayList<>();
    private int previousLpA = 0;
    private int previousLpB = 0;
    private int previousDotCounter = 0;
    
    // Recently added cards tracking
    private final List<String> recentlyAddedCardsA = new ArrayList<>();
    private final List<String> recentlyAddedCardsB = new ArrayList<>();
    
    // Revealed cards tracking
    private final List<String> revealedCardsA = new ArrayList<>();
    private final List<String> revealedCardsB = new ArrayList<>();

    public GameState(DeterministicRng rng) {
        this.rng = rng;
        this.playerA = new PlayerState(0); // LP not used in push system, but keeping for compatibility
        this.playerB = new PlayerState(0);
        this.sharedDotCounter = 0; // Start at neutral
        this.winPointsToReach = 5; // Default win condition
        this.previousLpA = 0;
        this.previousLpB = 0;
        this.previousDotCounter = 0;
    }

    public DeterministicRng getRng() { return rng; }
    public PlayerState getPlayerA() { return playerA; }
    public PlayerState getPlayerB() { return playerB; }
    public PlayerState getActivePlayer() { return activePlayerIndex == 0 ? playerA : playerB; }
    public PlayerState getInactivePlayer() { return activePlayerIndex == 0 ? playerB : playerA; }
    public int getActivePlayerIndex() { return activePlayerIndex; }
    public void swapActive() { activePlayerIndex = 1 - activePlayerIndex; }
    public Phase getPhase() { return phase; }
    public void setPhase(Phase p) { this.phase = p; }
    public List<String> getHistory() { return history; }

    public String getFaceUpCardId() { return faceUpCardId; }
    public String getFaceDownCardId() { return faceDownCardId; }
    public void setFaceUpCardId(String id) { this.faceUpCardId = id; }
    public void setFaceDownCardId(String id) { this.faceDownCardId = id; }

    public List<com.officeduel.engine.cards.CardDefinitionSet.Action> getLastActionsAppliedFor(PlayerState ps) {
        return ps == playerA ? lastActionsAppliedPlayerA : lastActionsAppliedPlayerB;
    }

    public void setLastActionsAppliedFor(PlayerState ps, List<com.officeduel.engine.cards.CardDefinitionSet.Action> actions) {
        if (ps == playerA) {
            this.lastActionsAppliedPlayerA = new ArrayList<>(actions);
        } else {
            this.lastActionsAppliedPlayerB = new ArrayList<>(actions);
        }
    }

    public int getSharedDotCounter() { return sharedDotCounter; }
    public void setSharedDotCounter(int dots) { this.sharedDotCounter = dots; }
    public void addToDotCounter(int delta) { 
        this.sharedDotCounter += delta;
        // Clamp between -winPointsToReach and winPointsToReach
        this.sharedDotCounter = Math.max(-winPointsToReach, Math.min(winPointsToReach, this.sharedDotCounter));
    }

    public int getWinPointsToReach() { return winPointsToReach; }
    public void setWinPointsToReach(int winPointsToReach) { this.winPointsToReach = winPointsToReach; }

    public int winnerIndexOrMinusOne() {
        // Dynamic dot system: winPointsToReach = A wins, -winPointsToReach = B wins
        if (sharedDotCounter >= winPointsToReach) return 0;  // A wins
        if (sharedDotCounter <= -winPointsToReach) return 1; // B wins
        return -1; // No winner yet
    }
    
    // Effect feedback methods
    public List<EffectFeedback> getRecentEffects() { return new ArrayList<>(recentEffects); }
    public void clearRecentEffects() { recentEffects.clear(); }
    
    // Recently added cards methods
    public List<String> getRecentlyAddedCardsA() { return new ArrayList<>(recentlyAddedCardsA); }
    public List<String> getRecentlyAddedCardsB() { return new ArrayList<>(recentlyAddedCardsB); }
    public void addRecentlyAddedCardA(String cardId) { recentlyAddedCardsA.add(cardId); }
    public void addRecentlyAddedCardB(String cardId) { recentlyAddedCardsB.add(cardId); }
    public void clearRecentlyAddedCardsA() { recentlyAddedCardsA.clear(); }
    public void clearRecentlyAddedCardsB() { recentlyAddedCardsB.clear(); }
    
    public void addEffectFeedback(String playerName, String cardName, String effectDescription) {
        int dotChange = sharedDotCounter - previousDotCounter;
        
        recentEffects.add(new EffectFeedback(
            playerName, cardName, effectDescription, 
            dotChange, System.currentTimeMillis()
        ));
        
        // Keep only last 10 effects to avoid memory issues
        if (recentEffects.size() > 10) {
            recentEffects.remove(0);
        }
        
        // Update previous values for next effect
        previousDotCounter = sharedDotCounter;
    }
    
    // Revealed cards management
    public List<String> getRevealedCardsA() { return new ArrayList<>(revealedCardsA); }
    public List<String> getRevealedCardsB() { return new ArrayList<>(revealedCardsB); }
    
    public void addRevealedCardA(String cardId) { revealedCardsA.add(cardId); }
    public void addRevealedCardB(String cardId) { revealedCardsB.add(cardId); }
    
    public void clearRevealedCardsA() { revealedCardsA.clear(); }
    public void clearRevealedCardsB() { revealedCardsB.clear(); }
    
    // Status effects management
    public Map<StatusType, Integer> getActiveStatusesA() {
        return playerA.getBuffs().getStatuses().getActiveStatuses();
    }
    
    public Map<StatusType, Integer> getActiveStatusesB() {
        return playerB.getBuffs().getStatuses().getActiveStatuses();
    }
    
    public record EffectFeedback(
            String playerName,
            String cardName,
            String effectDescription,
            int dotChange,
            long timestamp
    ) {}
}


