package com.officeduel.engine.model;

public final class MatchPlayer {
    private final PlayerState state;
    private final PlayerBuffs buffs = new PlayerBuffs();

    public MatchPlayer(PlayerState state) {
        this.state = state;
    }

    public PlayerState state() { return state; }
    public PlayerBuffs buffs() { return buffs; }
}


