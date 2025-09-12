package com.officeduel.engine.core;

import java.util.SplittableRandom;

public final class DeterministicRng {
    private final long seed;
    private final SplittableRandom random;

    public DeterministicRng(long seed) {
        this.seed = seed;
        this.random = new SplittableRandom(seed);
    }

    public long seed() {
        return seed;
    }

    public int nextInt(int boundExclusive) {
        return random.nextInt(boundExclusive);
    }

    public int nextInt(int originInclusive, int boundExclusive) {
        return random.nextInt(originInclusive, boundExclusive);
    }

    public boolean nextBoolean() {
        return random.nextBoolean();
    }
}


