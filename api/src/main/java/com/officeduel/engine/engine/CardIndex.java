package com.officeduel.engine.engine;

import com.officeduel.engine.cards.CardDefinitionSet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CardIndex {
    private final Map<String, CardDefinitionSet.CardDef> byId = new HashMap<>();
    private final int maxLp;

    public CardIndex(CardDefinitionSet defs) {
        for (CardDefinitionSet.CardDef c : defs.cards()) {
            byId.put(c.id(), c);
        }
        this.maxLp = defs.rulesAssumptions().max_lp();
    }

    public CardDefinitionSet.CardDef get(String id) { return byId.get(id); }
    public int maxLp() { return maxLp; }
    public List<CardDefinitionSet.CardDef> getAllCards() { return List.copyOf(byId.values()); }
}


