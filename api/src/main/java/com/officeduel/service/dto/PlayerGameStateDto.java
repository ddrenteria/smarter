package com.officeduel.service.dto;

import java.util.List;
import java.util.Map;

/**
 * Game state from a specific player's perspective.
 * Always contains 'self' (requesting player) and 'opponent' data.
 */
public record PlayerGameStateDto(
        String matchId,
        PlayerDto self,
        PlayerDto opponent,
        int selfDotCounter,  // Dot counter from requesting player's perspective (+5 = I win, -5 = I lose)
        String phase,
        String faceUpCardId,
        String faceDownCardId,
        boolean waitingForOpponentChoice,
        boolean isMyTurn,
        boolean canChooseCards,  // Can this player choose between the two cards in OPPONENT_PICK phase
        String winner,  // Name of winner, or null if game not finished
        Map<String, CardInfo> cardDefinitions,
        List<EffectFeedback> recentEffects,
        boolean started
) {
    public record PlayerDto(
            String name,
            int handSize,
            List<String> handCardIds,  // Only visible to self
            int tableauSize,
            List<String> tableauCardIds,
            List<String> revealedCards,
            List<String> recentlyAddedCards,
            Map<String, Integer> activeStatuses,
            boolean ready,
            boolean isBot
    ) {}
    
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
            long timestamp
    ) {}
}
