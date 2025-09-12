package com.officeduel.engine.model;

import com.officeduel.engine.core.DeterministicRng;

import java.util.ArrayList;
import java.util.List;
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

    private int sharedDotCounter = 0; // Shared counter: 0 = neutral, 5 = A wins, -5 = B wins
    
    // Effect feedback tracking
    private final List<EffectFeedback> recentEffects = new CopyOnWriteArrayList<>();
    private int previousLpA = 0;
    private int previousLpB = 0;
    private int previousDotCounter = 0;

    public GameState(DeterministicRng rng, int initialLp) {
        this.rng = rng;
        this.playerA = new PlayerState(initialLp);
        this.playerB = new PlayerState(initialLp);
        this.sharedDotCounter = 0; // Start at neutral
        this.previousLpA = initialLp;
        this.previousLpB = initialLp;
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
        // Clamp between -5 and 5
        this.sharedDotCounter = Math.max(-5, Math.min(5, this.sharedDotCounter));
    }

    public int winnerIndexOrMinusOne() {
        // New dot system: 5 = A wins, -5 = B wins
        if (sharedDotCounter >= 5) return 0;  // A wins
        if (sharedDotCounter <= -5) return 1; // B wins
        return -1; // No winner yet
    }
    
    // Effect feedback methods
    public List<EffectFeedback> getRecentEffects() { return new ArrayList<>(recentEffects); }
    public void clearRecentEffects() { recentEffects.clear(); }
    
    public void addEffectFeedback(String playerName, String cardName, String effectDescription) {
        int lpChangeA = playerA.getLifePoints() - previousLpA;
        int lpChangeB = playerB.getLifePoints() - previousLpB;
        int dotChange = sharedDotCounter - previousDotCounter;
        
        recentEffects.add(new EffectFeedback(
            playerName, cardName, effectDescription, 
            dotChange, lpChangeA, lpChangeB, 
            System.currentTimeMillis()
        ));
        
        // Keep only last 10 effects to avoid memory issues
        if (recentEffects.size() > 10) {
            recentEffects.remove(0);
        }
        
        // Update previous values for next effect
        previousLpA = playerA.getLifePoints();
        previousLpB = playerB.getLifePoints();
        previousDotCounter = sharedDotCounter;
    }
    
    public record EffectFeedback(
            String playerName,
            String cardName,
            String effectDescription,
            int dotChange,
            int lpChangeA,
            int lpChangeB,
            long timestamp
    ) {}
}


