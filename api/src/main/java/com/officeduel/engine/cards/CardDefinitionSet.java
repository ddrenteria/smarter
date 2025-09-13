package com.officeduel.engine.cards;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CardDefinitionSet(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("rules_assumptions") RulesAssumptions rulesAssumptions,
        List<CardDef> cards
) {
    public record RulesAssumptions(
            @JsonProperty("win_counter_min") int win_counter_min, 
            @JsonProperty("win_counter_max") int win_counter_max, 
            @JsonProperty("win_at_plus") int win_at_plus, 
            @JsonProperty("lose_at_minus") int lose_at_minus, 
            @JsonProperty("tier_calculation") String tier_calculation,
            @JsonProperty("copies_above_last_tier_clamp") boolean copies_above_last_tier_clamp,
            @JsonProperty("randomness_seed_source") String randomness_seed_source
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
            String source,
            String selection,
            @JsonProperty("on_empty") String on_empty,
            @JsonProperty("fallback_amount") Integer fallback_amount,
            String note,
            @JsonProperty("opponent_side") String opponent_side
    ) {}

    public record Condition(@JsonProperty("copies_of_this_card_equals") Integer copies_of_this_card_equals) {}
}


