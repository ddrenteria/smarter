package com.officeduel.engine.model;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

public final class PlayerState {
    private int lifePoints;
    private final Deque<Cards> deck = new LinkedList<>();
    private final List<Cards> hand = new ArrayList<>();
    private final List<Cards> tableau = new ArrayList<>();
    private final List<Cards> discard = new ArrayList<>();
    private int maxHandSize = 5;
    private boolean skipNextTurn = false;
    private int blockNextDrawCount = 0;
    private final PlayerBuffs buffs = new PlayerBuffs();
    private int extraFaceDownPlays = 0;

    public PlayerState(int lifePoints) {
        this.lifePoints = lifePoints;
    }

    public int getLifePoints() { return lifePoints; }
    public void setLifePoints(int lp) { this.lifePoints = lp; }

    public Deque<Cards> getDeck() { return deck; }
    public List<Cards> getHand() { return hand; }
    public List<Cards> getTableau() { return tableau; }
    public List<Cards> getDiscard() { return discard; }

    public int getMaxHandSize() { return maxHandSize; }
    public void setMaxHandSize(int value) { this.maxHandSize = value; }

    public boolean isSkipNextTurn() { return skipNextTurn; }
    public void setSkipNextTurn(boolean value) { this.skipNextTurn = value; }

    public int getBlockNextDrawCount() { return blockNextDrawCount; }
    public void incrementBlockNextDraw(int delta) { this.blockNextDrawCount += delta; }
    public boolean consumeBlockDrawIfAny() {
        if (blockNextDrawCount > 0) { blockNextDrawCount--; return true; }
        return false;
    }

    public PlayerBuffs getBuffs() { return buffs; }

    public int getExtraFaceDownPlays() { return extraFaceDownPlays; }
    public void addExtraFaceDownPlays(int delta) { this.extraFaceDownPlays += delta; }
    public boolean consumeExtraFaceDownPlayIfAny() {
        if (extraFaceDownPlays > 0) { extraFaceDownPlays--; return true; }
        return false;
    }
}


