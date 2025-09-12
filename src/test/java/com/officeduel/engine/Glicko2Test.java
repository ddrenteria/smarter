package com.officeduel.engine;

import com.officeduel.engine.rating.Glicko2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Glicko2Test {
    @Test
    public void simpleUpdateConverges() {
        Glicko2.Rating a = new Glicko2.Rating(1500, 350, 0.06);
        Glicko2.Rating b = new Glicko2.Rating(1500, 350, 0.06);
        // A wins
        Glicko2.Rating a1 = Glicko2.update(a, b, 1.0);
        Glicko2.Rating b1 = Glicko2.update(b, a, 0.0);
        assertTrue(a1.r() > a.r());
        assertTrue(b1.r() < b.r());
        assertTrue(a1.rd() <= a.rd());
        assertTrue(b1.rd() <= b.rd());
    }
}


