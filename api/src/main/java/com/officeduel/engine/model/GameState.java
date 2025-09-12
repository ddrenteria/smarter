package com.officeduel.engine.model;

import com.officeduel.engine.core.DeterministicRng;

import java.util.ArrayList;
import java.util.List;

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

    public GameState(DeterministicRng rng, int initialLp) {
        this.rng = rng;
        this.playerA = new PlayerState(initialLp);
        this.playerB = new PlayerState(initialLp);
        this.sharedDotCounter = 0; // Start at neutral
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
}


