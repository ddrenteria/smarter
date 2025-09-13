package com.officeduel.service.dto;

import java.util.List;
import java.util.Map;

public record MatchStateDto(
        String id,
        int lpA,
        int lpB,
        int handSizeA,
        int handSizeB,
        int tableauSizeA,
        int tableauSizeB,
        int activePlayer,
        List<String> activeHandCardIds,
        List<String> tableauCardIdsA,
        List<String> tableauCardIdsB,
        Map<String, CardInfo> cardDefinitions,
        String phase,
        String faceUpCardId,
        String faceDownCardId,
        boolean waitingForOpponentChoice,
        int sharedDotCounter,
        // New Milestone 1 fields
        String playerA,
        String playerB,
        boolean readyA,
        boolean readyB,
        boolean started,
        boolean playerAIsBot,
        boolean playerBIsBot,
        // Effect feedback
        List<EffectFeedback> recentEffects,
        // Revealed cards
        List<String> revealedCardsA,
        List<String> revealedCardsB,
        // Active status effects
        Map<String, Integer> activeStatusesA,
        Map<String, Integer> activeStatusesB
) {
    public record CardInfo(
            String name,
            List<String> tags,
            List<TierInfo> tiers
    ) {}
    
    public record TierInfo(
            List<ActionInfo> actions
    ) {}
    
    public record ActionInfo(
            String type,
            String target,
            Integer amount,
            Integer count,
            String description
    ) {}
    
    public record EffectFeedback(
            String playerName,
            String cardName,
            String effectDescription,
            int dotChange,
            int lpChangeA,
            int lpChangeB,
            long timestamp
    ) {}
}


