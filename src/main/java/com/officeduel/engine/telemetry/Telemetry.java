package com.officeduel.engine.telemetry;

public interface Telemetry {
    void emit(Event event);

    sealed interface Event permits MatchStart, CardPlayed, LifeChange, MatchEnd {}

    record MatchStart(long seed) implements Event {}
    record CardPlayed(int playerIndex, String cardId, int tier) implements Event {}
    record LifeChange(int playerIndex, int delta, String reason) implements Event {}
    record MatchEnd(int winnerIndex, int lpA, int lpB) implements Event {}
}


