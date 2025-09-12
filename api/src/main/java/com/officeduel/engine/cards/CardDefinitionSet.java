package com.officeduel.engine.cards;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CardDefinitionSet(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("rules_assumptions") RulesAssumptions rulesAssumptions,
        List<CardDef> cards
) {
    public record RulesAssumptions(
            @JsonProperty("max_lp") int max_lp, 
            @JsonProperty("copies_above_three_treated_as_three") boolean copies_above_three_treated_as_three, 
            @JsonProperty("tier_calculation") String tier_calculation
    ) {}

    public record CardDef(
            String id,
            String name,
            List<String> tags,
            List<Tier> tiers
    ) {}

    public record Tier(List<Action> actions) {}

    public record Action(
            String type,
            String target,
            Integer amount,
            Integer count,
            @JsonProperty("duration_turns") Integer duration_turns,
            String status,
            @JsonProperty("still_draws") Boolean still_draws,
            Integer delta,
            Integer times,
            Condition condition,
            String source
    ) {}

    public record Condition(@JsonProperty("copies_of_this_card_equals") Integer copies_of_this_card_equals) {}
}


