package com.officeduel.engine.model;

import java.util.EnumMap;
import java.util.Map;

public final class Statuses {
    private final Map<StatusType, Integer> durations = new EnumMap<>(StatusType.class);
    private final Map<StatusType, Integer> amounts = new EnumMap<>(StatusType.class);

    public void apply(StatusType type, int amount, int durationTurns) {
        if (amount != 0) {
            amounts.put(type, amount);
        }
        durations.put(type, durationTurns);
    }

    public boolean has(StatusType type) {
        return durations.containsKey(type);
    }

    public int amount(StatusType type) {
        return amounts.getOrDefault(type, 0);
    }

    public void consumeShieldIfAny() {
        if (has(StatusType.SHIELD)) {
            remove(StatusType.SHIELD);
        }
    }

    public void tickEndOfTurn() {
        durations.replaceAll((k, v) -> v == -1 ? -1 : v - 1);
        durations.entrySet().removeIf(e -> e.getValue() == 0);
        amounts.keySet().removeIf(k -> !durations.containsKey(k));
    }

    public void remove(StatusType type) {
        durations.remove(type);
        amounts.remove(type);
    }
}


