package com.officeduel.service;

import com.officeduel.engine.cards.CardDefinitionSet;
import com.officeduel.engine.engine.CardIndex;
import com.officeduel.service.dto.MatchStateDto;
import com.officeduel.service.dto.PlayerGameStateDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/match")
public class MatchController {
    private final MatchRegistry registry;

    public MatchController(MatchRegistry registry) {
        this.registry = registry;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestParam(name = "seed", required = false) Long seed) {
        long s = seed != null ? seed : System.currentTimeMillis();
        String id = registry.createMatch(s);
        return ResponseEntity.ok(Map.of("id", id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MatchStateDto> get(@PathVariable String id, @RequestParam(required = false) Integer seat) {
        var e = registry.get(id);
        if (e == null) return ResponseEntity.notFound().build();
        
        // If match not started yet, return lobby state
        if (!e.started()) {
        return ResponseEntity.ok(new MatchStateDto(
                id, 0, 0, 0, 0, 0, 
                List.of(), List.of(), List.of(), 
                getCardDefinitions(), "LOBBY", null, null, false, 0,
                e.playerA(), e.playerB(), e.readyA(), e.readyB(), false, e.playerAIsBot(), e.playerBIsBot(),
                List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(), Map.of()
        ));
        }
        
        var gs = e.state();
        var engine = e.engine();
        if (engine == null) return ResponseEntity.status(500).build();
        
        // Fog of war: only show hand to the seat that owns it
        var handIds = List.<String>of();
        if (seat != null) {
            if (seat == 0) {
                handIds = gs.getPlayerA().getHand().stream().map(c -> c.cardId()).toList();
            } else if (seat == 1) {
                handIds = gs.getPlayerB().getHand().stream().map(c -> c.cardId()).toList();
            }
        }
        
        var tableauIdsA = gs.getPlayerA().getTableau().stream().map(c -> c.cardId()).toList();
        var tableauIdsB = gs.getPlayerB().getTableau().stream().map(c -> c.cardId()).toList();
        
        return ResponseEntity.ok(new MatchStateDto(
                id,
                gs.getPlayerA().getHand().size(),
                gs.getPlayerB().getHand().size(),
                gs.getPlayerA().getTableau().size(),
                gs.getPlayerB().getTableau().size(),
                gs.getActivePlayerIndex(),
                handIds,
                tableauIdsA,
                tableauIdsB,
                getCardDefinitions(),
                gs.getPhase().name(),
                gs.getFaceUpCardId(),
                gs.getPhase() == com.officeduel.engine.model.GameState.Phase.OPPONENT_PICK ? "???" : gs.getFaceDownCardId(),
                gs.getPhase() == com.officeduel.engine.model.GameState.Phase.OPPONENT_PICK,
                gs.getSharedDotCounter(),
                e.playerA(), e.playerB(), e.readyA(), e.readyB(), true, e.playerAIsBot(), e.playerBIsBot(),
                convertEffectFeedback(gs.getRecentEffects()),
                gs.getRevealedCardsA(),
                gs.getRevealedCardsB(),
                gs.getRecentlyAddedCardsA(),
                gs.getRecentlyAddedCardsB(),
                convertStatusMap(gs.getActiveStatusesA()),
                convertStatusMap(gs.getActiveStatusesB())
        ));
    }


    public record SubmitTurnBody(String faceUpId, String faceDownId) {}
    public record OpponentChoiceBody(boolean chooseFaceUp) {}
    public record JoinMatchBody(String name) {}
    public record JoinMatchResponse(int seat, String token) {}

    @PostMapping("/{id}/play")
    public ResponseEntity<MatchStateDto> play(@PathVariable String id, @RequestBody SubmitTurnBody body, @RequestParam(required = false) Integer seat) {
        var e = registry.get(id);
        if (e == null) return ResponseEntity.notFound().build();
        if (e.engine() == null) return ResponseEntity.status(409).body(null); // Match not started
        if (body == null || body.faceUpId() == null || body.faceDownId() == null) return ResponseEntity.badRequest().build();
        
        // Validate that both cards are different (no duplicate card selection)
        if (body.faceUpId().equals(body.faceDownId())) {
            return ResponseEntity.badRequest().build(); // Cannot select the same card twice
        }
        
        try {
            // Set the cards for the turn but don't resolve yet
            e.state().setFaceUpCardId(body.faceUpId());
            e.state().setFaceDownCardId(body.faceDownId());
            e.state().setPhase(com.officeduel.engine.model.GameState.Phase.OPPONENT_PICK);
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(null); // Conflict - wrong phase
        }
        
        var gs = e.state();
        // Fog of war: only show hand to the seat that owns it
        var handIds = List.<String>of();
        if (seat != null && seat == gs.getActivePlayerIndex()) {
            var active = gs.getActivePlayerIndex() == 0 ? gs.getPlayerA() : gs.getPlayerB();
            handIds = active.getHand().stream().map(c -> c.cardId()).toList();
        }
        
        var tableauIdsA = gs.getPlayerA().getTableau().stream().map(c -> c.cardId()).toList();
        var tableauIdsB = gs.getPlayerB().getTableau().stream().map(c -> c.cardId()).toList();
        
        return ResponseEntity.ok(new MatchStateDto(
                id,
                gs.getPlayerA().getHand().size(),
                gs.getPlayerB().getHand().size(),
                gs.getPlayerA().getTableau().size(),
                gs.getPlayerB().getTableau().size(),
                gs.getActivePlayerIndex(),
                handIds,
                tableauIdsA,
                tableauIdsB,
                getCardDefinitions(),
                gs.getPhase().name(),
                gs.getFaceUpCardId(),
                gs.getPhase() == com.officeduel.engine.model.GameState.Phase.OPPONENT_PICK ? "???" : gs.getFaceDownCardId(),
                true,
                gs.getSharedDotCounter(),
                e.playerA(), e.playerB(), e.readyA(), e.readyB(), true, e.playerAIsBot(), e.playerBIsBot(),
                convertEffectFeedback(gs.getRecentEffects()),
                gs.getRevealedCardsA(),
                gs.getRevealedCardsB(),
                gs.getRecentlyAddedCardsA(),
                gs.getRecentlyAddedCardsB(),
                convertStatusMap(gs.getActiveStatusesA()),
                convertStatusMap(gs.getActiveStatusesB())
        ));
    }

    @PostMapping("/{id}/choose")
    public synchronized ResponseEntity<MatchStateDto> choose(@PathVariable String id, @RequestBody OpponentChoiceBody body, @RequestParam(required = false) Integer seat) {
        var e = registry.get(id);
        if (e == null) return ResponseEntity.notFound().build();
        if (e.engine() == null) return ResponseEntity.status(409).body(null); // Match not started
        if (body == null) return ResponseEntity.badRequest().build();
        
        // Check if we're in the correct phase
        if (e.state().getPhase() != com.officeduel.engine.model.GameState.Phase.OPPONENT_PICK) {
            System.out.println("Choose endpoint called but wrong phase: " + e.state().getPhase());
            return ResponseEntity.status(409).body(null); // Conflict - wrong phase
        }
        
        try {
            // Debug logging
            System.out.println("Choose endpoint called:");
            System.out.println("  Match ID: " + id);
            System.out.println("  Choose face up: " + body.chooseFaceUp());
            System.out.println("  Current phase: " + e.state().getPhase());
            System.out.println("  Face up card ID: " + e.state().getFaceUpCardId());
            System.out.println("  Face down card ID: " + e.state().getFaceDownCardId());
            
            // Now resolve the turn with the opponent's choice
            String picked = body.chooseFaceUp() ? e.state().getFaceUpCardId() : e.state().getFaceDownCardId();
            String remaining = body.chooseFaceUp() ? e.state().getFaceDownCardId() : e.state().getFaceUpCardId();
            
            System.out.println("  Picked card: " + picked);
            System.out.println("  Remaining card: " + remaining);
            
            // Use the existing manual turn method with the opponent's choice
            e.engine().playTurnWithChoice(picked, remaining);
        } catch (IllegalStateException ex) {
            System.out.println("  IllegalStateException: " + ex.getMessage());
            return ResponseEntity.status(409).body(null); // Conflict - wrong phase
        } catch (Exception ex) {
            System.out.println("  Unexpected error: " + ex.getMessage());
            ex.printStackTrace();
            return ResponseEntity.status(500).body(null);
        }
        
        var gs = e.state();
        // Fog of war: only show hand to the seat that owns it
        var handIds = List.<String>of();
        if (seat != null && seat == gs.getActivePlayerIndex()) {
            var active = gs.getActivePlayerIndex() == 0 ? gs.getPlayerA() : gs.getPlayerB();
            handIds = active.getHand().stream().map(c -> c.cardId()).toList();
        }
        
        var tableauIdsA = gs.getPlayerA().getTableau().stream().map(c -> c.cardId()).toList();
        var tableauIdsB = gs.getPlayerB().getTableau().stream().map(c -> c.cardId()).toList();
        
        return ResponseEntity.ok(new MatchStateDto(
                id,
                gs.getPlayerA().getHand().size(),
                gs.getPlayerB().getHand().size(),
                gs.getPlayerA().getTableau().size(),
                gs.getPlayerB().getTableau().size(),
                gs.getActivePlayerIndex(),
                handIds,
                tableauIdsA,
                tableauIdsB,
                getCardDefinitions(),
                gs.getPhase().name(),
                gs.getFaceUpCardId(),
                gs.getFaceDownCardId(),
                gs.getPhase() == com.officeduel.engine.model.GameState.Phase.OPPONENT_PICK,
                gs.getSharedDotCounter(),
                e.playerA(), e.playerB(), e.readyA(), e.readyB(), true, e.playerAIsBot(), e.playerBIsBot(),
                convertEffectFeedback(gs.getRecentEffects()),
                gs.getRevealedCardsA(),
                gs.getRevealedCardsB(),
                gs.getRecentlyAddedCardsA(),
                gs.getRecentlyAddedCardsB(),
                convertStatusMap(gs.getActiveStatusesA()),
                convertStatusMap(gs.getActiveStatusesB())
        ));
    }

    private Map<String, MatchStateDto.CardInfo> getCardDefinitions() {
        Map<String, MatchStateDto.CardInfo> definitions = new HashMap<>();
        var cardIndex = registry.getCardIndex();
        for (var card : cardIndex.getAllCards()) {
            var tiers = card.tiers().stream()
                .map(tier -> new MatchStateDto.TierInfo(
                    tier.actions().stream()
                        .map(action -> new MatchStateDto.ActionInfo(
                            action.type(),
                            action.target(),
                            action.amount(),
                            action.count(),
                            generateActionDescription(action)
                        ))
                        .toList()
                ))
                .toList();
            
            definitions.put(card.id(), new MatchStateDto.CardInfo(
                card.name(),
                card.tags(),
                tiers
            ));
        }
        return definitions;
    }

    private String generateActionDescription(CardDefinitionSet.Action action) {
        StringBuilder desc = new StringBuilder();
        String target = getTargetDescription(action.target());
        
        switch (action.type()) {
            case "damage" -> desc.append("Inflige ").append(action.amount()).append(" de daño a ").append(target);
            case "heal" -> desc.append("Cura ").append(action.amount()).append(" LP a ").append(target);
            case "draw" -> desc.append("Hace que ").append(target).append(" robe ").append(action.count()).append(" carta(s)");
            case "skip_next_turn" -> desc.append("Hace que ").append(target).append(" salte el siguiente turno");
            case "block_next_draw" -> desc.append("Bloquea ").append(action.count()).append(" robo(s) de ").append(target);
            case "discard_random" -> desc.append("Hace que ").append(target).append(" descarte ").append(action.count()).append(" carta(s) al azar");
            case "discard_hand" -> desc.append("Hace que ").append(target).append(" descarte toda la mano");
            case "set_lp_to_full" -> desc.append("Restaura los LP de ").append(target).append(" al máximo");
            case "status" -> {
                String statusName = getStatusDescription(action.status());
                desc.append("Aplica estado ").append(statusName).append(" a ").append(target);
            }
            case "reveal_random_cards" -> desc.append("Revela ").append(action.count()).append(" carta(s) de ").append(target);
            case "steal_random_card_from_hand" -> desc.append("Roba ").append(action.count()).append(" carta(s) de la mano de ").append(target);
            case "steal_card_from_tableau_and_play" -> desc.append("Roba una carta del tableau de ").append(target).append(" y la juega");
            case "destroy_random_cards_in_tableau" -> desc.append("Destruye ").append(action.count()).append(" carta(s) del tableau de ").append(target);
            case "modify_max_hand_size" -> desc.append("Modifica el tamaño de mano de ").append(target).append(" en ").append(action.delta());
            case "grant_extra_face_down_play" -> desc.append("Permite que ").append(target).append(" juegue ").append(action.count()).append(" carta(s) boca abajo extra");
            case "win_if_condition" -> desc.append("Gana si ").append(target).append(" cumple la condición");
            case "copy_last_card_effect" -> desc.append("Copia el último efecto ").append(action.times()).append(" vez(es) para ").append(target);
            case "push" -> {
                int amount = action.amount() != null ? action.amount() : 0;
                if (amount > 0) {
                    desc.append("Empuja <span class='push-positive'>+").append(amount).append("</span> puntos");
                } else if (amount < 0) {
                    desc.append("Empuja <span class='push-negative'>").append(amount).append("</span> puntos");
                } else {
                    desc.append("Empuja 0 puntos");
                }
            }
            case "noop" -> {
                // No operation - don't show anything or show a minimal description
                return ""; // Return empty string so noop doesn't appear
            }
            default -> desc.append(action.type()).append(" a ").append(target);
        }
        return desc.toString();
    }
    
    private String getTargetDescription(String target) {
        if (target == null) {
            return "objetivo desconocido";
        }
        return switch (target) {
            case "self" -> "ti mismo";
            case "opponent" -> "el oponente";
            case "both" -> "ambos jugadores";
            default -> target;
        };
    }
    
    private String getStatusDescription(String status) {
        if (status == null) {
            return "estado desconocido";
        }
        return switch (status.toLowerCase()) {
            case "shield_next_push_against_you" -> "Escudo contra próximo empuje";
            case "reflect_next_push" -> "Refleja próximo empuje";
            case "randomize_next_card_effect" -> "Efecto aleatorio en próxima carta";
            case "global_random_effects" -> "Efectos aleatorios globales";
            default -> status;
        };
    }
    
    private List<MatchStateDto.EffectFeedback> convertEffectFeedback(List<com.officeduel.engine.model.GameState.EffectFeedback> effects) {
        if (effects == null) {
            return List.of();
        }
        return effects.stream()
            .map(effect -> new MatchStateDto.EffectFeedback(
                effect.playerName(),
                effect.cardName(),
                effect.effectDescription(),
                effect.dotChange(),
                effect.timestamp()
            ))
            .toList();
    }
    
    private Map<String, Integer> convertStatusMap(Map<com.officeduel.engine.model.StatusType, Integer> statusMap) {
        if (statusMap == null) {
            return Map.of();
        }
        return statusMap.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                entry -> entry.getKey().name(),
                Map.Entry::getValue
            ));
    }
    
    @PostMapping("/{id}/join")
    public ResponseEntity<JoinMatchResponse> join(@PathVariable String id, @RequestBody JoinMatchBody body) {
        if (body == null || body.name() == null || body.name().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        String trimmedName = body.name().trim();
        if (trimmedName.length() > 15) {
            return ResponseEntity.badRequest().build(); // Name too long
        }
        
        var result = registry.joinMatch(id, trimmedName);
        if (result.seat() == -1) return ResponseEntity.notFound().build();
        if (result.seat() == -2) return ResponseEntity.status(409).body(new JoinMatchResponse(-1, null)); // Conflict - match full
        
        return ResponseEntity.ok(new JoinMatchResponse(result.seat(), result.token()));
    }
    
    @PostMapping("/{id}/ready")
    public ResponseEntity<Map<String, String>> ready(@PathVariable String id, @RequestParam int seat) {
        boolean success = registry.setReady(id, seat);
        if (!success) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("status", "ready"));
    }
    
    @PostMapping("/bot")
    public ResponseEntity<Map<String, String>> createWithBot(@RequestParam(name = "seed", required = false) Long seed) {
        long s = seed != null ? seed : System.currentTimeMillis();
        String id = registry.createMatchWithBot(s);
        return ResponseEntity.ok(Map.of("id", id));
    }
    
    @PostMapping("/{id}/add-bot")
    public ResponseEntity<Map<String, String>> addBot(@PathVariable String id) {
        registry.addBotToMatch(id);
        return ResponseEntity.ok(Map.of("status", "bot_added"));
    }
    
    /**
     * New endpoint that returns game state from player perspective (self/opponent)
     */
    @GetMapping("/{id}/state")
    public ResponseEntity<PlayerGameStateDto> getPlayerState(@PathVariable String id, 
                                                            @RequestHeader("X-Player-Token") String token) {
        var entry = registry.get(id);
        if (entry == null) return ResponseEntity.notFound().build();
        
        int playerSeat = registry.getPlayerSeat(id, token);
        if (playerSeat == -1) return ResponseEntity.status(401).build(); // Unauthorized
        
        // If match not started yet, return lobby state
        if (!entry.started()) {
            var selfPlayer = playerSeat == 0 ? 
                new PlayerGameStateDto.PlayerDto(entry.playerA() != null ? entry.playerA() : "Player A", 0, List.of(), 0, List.of(), 
                                               List.of(), List.of(), Map.of(), entry.readyA(), entry.playerAIsBot()) :
                new PlayerGameStateDto.PlayerDto(entry.playerB() != null ? entry.playerB() : "Player B", 0, List.of(), 0, List.of(), 
                                               List.of(), List.of(), Map.of(), entry.readyB(), entry.playerBIsBot());
            
            var opponentPlayer = playerSeat == 0 ? 
                new PlayerGameStateDto.PlayerDto(entry.playerB() != null ? entry.playerB() : "Waiting...", 0, List.of(), 0, List.of(), 
                                               List.of(), List.of(), Map.of(), entry.readyB(), entry.playerBIsBot()) :
                new PlayerGameStateDto.PlayerDto(entry.playerA() != null ? entry.playerA() : "Waiting...", 0, List.of(), 0, List.of(), 
                                               List.of(), List.of(), Map.of(), entry.readyA(), entry.playerAIsBot());
            
            System.out.println("🔧 LOBBY STATE: Sending timer values to frontend: playTwoCardsTimeSeconds=" + entry.playTwoCardsTimeSeconds() + ", opponentPickTimeSeconds=" + entry.opponentPickTimeSeconds());
            
            return ResponseEntity.ok(new PlayerGameStateDto(
                id, selfPlayer, opponentPlayer, 0, "LOBBY", null, null, false, false, false, null,
                convertCardDefinitions(getCardDefinitions()), List.of(), false, entry.playTwoCardsTimeSeconds(), entry.opponentPickTimeSeconds()
            ));
        }
        
        var gs = entry.state();
        var engine = entry.engine();
        if (engine == null) return ResponseEntity.status(500).build();
        
        // Determine self and opponent based on token
        boolean isPlayerA = playerSeat == 0;
        var selfState = isPlayerA ? gs.getPlayerA() : gs.getPlayerB();
        var opponentState = isPlayerA ? gs.getPlayerB() : gs.getPlayerA();
        
        // Build self player (with hand visible)
        var selfHandIds = selfState.getHand().stream().map(c -> c.cardId()).toList();
        var selfTableauIds = selfState.getTableau().stream().map(c -> c.cardId()).toList();
        var selfRevealed = isPlayerA ? gs.getRevealedCardsA() : gs.getRevealedCardsB();
        var selfRecentlyAdded = isPlayerA ? gs.getRecentlyAddedCardsA() : gs.getRecentlyAddedCardsB();
        var selfStatuses = isPlayerA ? convertStatusMap(gs.getActiveStatusesA()) : convertStatusMap(gs.getActiveStatusesB());
        
        var selfPlayer = new PlayerGameStateDto.PlayerDto(
            isPlayerA ? (entry.playerA() != null ? entry.playerA() : "Player A") : (entry.playerB() != null ? entry.playerB() : "Player B"),
            selfState.getHand().size(),
            selfHandIds,
            selfState.getTableau().size(),
            selfTableauIds,
            selfRevealed,
            selfRecentlyAdded,
            selfStatuses,
            isPlayerA ? entry.readyA() : entry.readyB(),
            isPlayerA ? entry.playerAIsBot() : entry.playerBIsBot()
        );
        
        // Build opponent player (hand hidden)
        var opponentTableauIds = opponentState.getTableau().stream().map(c -> c.cardId()).toList();
        var opponentRevealed = isPlayerA ? gs.getRevealedCardsB() : gs.getRevealedCardsA();
        var opponentRecentlyAdded = isPlayerA ? gs.getRecentlyAddedCardsB() : gs.getRecentlyAddedCardsA();
        var opponentStatuses = isPlayerA ? convertStatusMap(gs.getActiveStatusesB()) : convertStatusMap(gs.getActiveStatusesA());
        
        var opponentPlayer = new PlayerGameStateDto.PlayerDto(
            isPlayerA ? (entry.playerB() != null ? entry.playerB() : "Player B") : (entry.playerA() != null ? entry.playerA() : "Player A"),
            opponentState.getHand().size(),
            List.of(), // Hand hidden for opponent
            opponentState.getTableau().size(),
            opponentTableauIds,
            opponentRevealed,
            opponentRecentlyAdded,
            opponentStatuses,
            isPlayerA ? entry.readyB() : entry.readyA(),
            isPlayerA ? entry.playerBIsBot() : entry.playerAIsBot()
        );
        
        // Determine if it's this player's turn
        boolean isMyTurn = (playerSeat == gs.getActivePlayerIndex());
        
        // Determine if this player can choose cards in OPPONENT_PICK phase
        // Only the opponent (inactive player) can choose
        boolean canChooseCards = (gs.getPhase() == com.officeduel.engine.model.GameState.Phase.OPPONENT_PICK) 
                                && (playerSeat != gs.getActivePlayerIndex());
        
        // Calculate dot counter from requesting player's perspective
        // sharedDotCounter: +5 = Player A wins, -5 = Player B wins
        // For Player A: return as-is (+5 = I win, -5 = I lose)
        // For Player B: invert (-5 = I win, +5 = I lose)
        int selfDotCounter = isPlayerA ? gs.getSharedDotCounter() : -gs.getSharedDotCounter();
        
        // Determine winner based on dots
        String winner = null;
        int winnerIndex = gs.winnerIndexOrMinusOne();
        System.out.println("DEBUG WINNER: sharedDotCounter = " + gs.getSharedDotCounter() + ", winnerIndex = " + winnerIndex);
        System.out.println("DEBUG WINNER: playerA = " + entry.playerA() + ", playerB = " + entry.playerB());
        
        // FIX: Invert the winner logic as it was backwards
        if (winnerIndex == 0) {
            winner = entry.playerB() != null ? entry.playerB() : "Player B";
            System.out.println("DEBUG WINNER: sharedDotCounter >= 5, but Player B actually wins, winner = " + winner);
        } else if (winnerIndex == 1) {
            winner = entry.playerA() != null ? entry.playerA() : "Player A";
            System.out.println("DEBUG WINNER: sharedDotCounter <= -5, but Player A actually wins, winner = " + winner);
        } else {
            System.out.println("DEBUG WINNER: No winner yet");
        }
        
        System.out.println("DEBUG TURNS: playerSeat = " + playerSeat + ", activePlayerIndex = " + gs.getActivePlayerIndex() + ", isMyTurn = " + isMyTurn + ", canChooseCards = " + canChooseCards);
        System.out.println("DEBUG DOTS: sharedDotCounter = " + gs.getSharedDotCounter() + ", isPlayerA = " + isPlayerA + ", selfDotCounter = " + selfDotCounter + ", winner = " + winner);
        
        // Auto-play bot turns from backend
        scheduleAutoBotPlay(id, entry, gs);
        
        System.out.println("🔧 GAME STATE: Sending timer values to frontend: playTwoCardsTimeSeconds=" + entry.playTwoCardsTimeSeconds() + ", opponentPickTimeSeconds=" + entry.opponentPickTimeSeconds());
        
        return ResponseEntity.ok(new PlayerGameStateDto(
            id,
            selfPlayer,
            opponentPlayer,
            selfDotCounter,
            gs.getPhase().name(),
            gs.getFaceUpCardId(),
            gs.getPhase() == com.officeduel.engine.model.GameState.Phase.OPPONENT_PICK ? "???" : gs.getFaceDownCardId(),
            gs.getPhase() == com.officeduel.engine.model.GameState.Phase.OPPONENT_PICK,
            isMyTurn,
            canChooseCards,
            winner,
            convertCardDefinitions(getCardDefinitions()),
            convertEffectFeedbackForPlayerDto(gs.getRecentEffects()),
            true,
            entry.playTwoCardsTimeSeconds(),
            entry.opponentPickTimeSeconds()
        ));
    }
    
    /**
     * New play endpoint that uses token-based authentication
     */
    @PostMapping("/{id}/play-token")
    public ResponseEntity<PlayerGameStateDto> playWithToken(@PathVariable String id, 
                                                           @RequestBody SubmitTurnBody body,
                                                           @RequestHeader("X-Player-Token") String token) {
        var entry = registry.get(id);
        if (entry == null) return ResponseEntity.notFound().build();
        if (entry.engine() == null) return ResponseEntity.status(409).build(); // Match not started
        if (body == null || body.faceUpId() == null || body.faceDownId() == null) return ResponseEntity.badRequest().build();
        
        int playerSeat = registry.getPlayerSeat(id, token);
        if (playerSeat == -1) return ResponseEntity.status(401).build(); // Unauthorized
        
        var gs = entry.state();
        if (gs.getActivePlayerIndex() != playerSeat) {
            return ResponseEntity.status(409).build(); // Not your turn
        }
        
        try {
            entry.engine().playTurnManual(body.faceUpId(), body.faceDownId());
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).build(); // Wrong phase
        } catch (Exception ex) {
            return ResponseEntity.status(500).build();
        }
        
        // Return updated state
        return getPlayerState(id, token);
    }
    
    /**
     * New choose endpoint that uses token-based authentication
     */
    @PostMapping("/{id}/choose-token")
    public ResponseEntity<PlayerGameStateDto> chooseWithToken(@PathVariable String id, 
                                                            @RequestBody OpponentChoiceBody body,
                                                            @RequestHeader("X-Player-Token") String token) {
        var entry = registry.get(id);
        if (entry == null) return ResponseEntity.notFound().build();
        if (entry.engine() == null) return ResponseEntity.status(409).build(); // Match not started
        if (body == null) return ResponseEntity.badRequest().build();
        
        int playerSeat = registry.getPlayerSeat(id, token);
        if (playerSeat == -1) return ResponseEntity.status(401).build(); // Unauthorized
        
        var gs = entry.state();
        if (gs.getPhase() != com.officeduel.engine.model.GameState.Phase.OPPONENT_PICK) {
            return ResponseEntity.status(409).build(); // Wrong phase
        }
        
        // CRITICAL: Only the opponent (inactive player) should be able to choose
        if (playerSeat == gs.getActivePlayerIndex()) {
            System.out.println("DEBUG OPPONENT_PICK: Card submitter (seat " + playerSeat + ") tried to choose, but only opponent should choose");
            return ResponseEntity.status(403).build(); // Forbidden - you submitted the cards, opponent must choose
        }
        
        try {
            String picked = body.chooseFaceUp() ? gs.getFaceUpCardId() : gs.getFaceDownCardId();
            String remaining = body.chooseFaceUp() ? gs.getFaceDownCardId() : gs.getFaceUpCardId();
            entry.engine().playTurnWithChoice(picked, remaining);
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).build(); // Wrong phase
        } catch (Exception ex) {
            return ResponseEntity.status(500).build();
        }
        
        // Return updated state
        return getPlayerState(id, token);
    }
    
    /**
     * Ready endpoint that uses token-based authentication
     */
    @PostMapping("/{id}/ready-token")
    public ResponseEntity<PlayerGameStateDto> readyWithToken(@PathVariable String id, 
                                                           @RequestHeader("X-Player-Token") String token) {
        var entry = registry.get(id);
        if (entry == null) return ResponseEntity.notFound().build();
        
        int playerSeat = registry.getPlayerSeat(id, token);
        if (playerSeat == -1) return ResponseEntity.status(401).build(); // Unauthorized
        
        boolean success = registry.setReady(id, playerSeat);
        if (!success) return ResponseEntity.status(500).build();
        
        // Return updated state
        return getPlayerState(id, token);
    }
    
    public record UpdateTimerSettingsBody(int playTwoCardsTimeSeconds, int opponentPickTimeSeconds) {}
    
    /**
     * Update timer settings for a match (only host can do this)
     */
    @PostMapping("/{id}/timer-settings")
    public ResponseEntity<String> updateTimerSettings(@PathVariable String id, @RequestHeader("X-Player-Token") String token, @RequestBody UpdateTimerSettingsBody body) {
        System.out.println("🔧 TIMER SETTINGS: Received request for match " + id);
        System.out.println("🔧 TIMER SETTINGS: playTwoCardsTimeSeconds = " + body.playTwoCardsTimeSeconds);
        System.out.println("🔧 TIMER SETTINGS: opponentPickTimeSeconds = " + body.opponentPickTimeSeconds);
        
        int playerSeat = registry.getPlayerSeat(id, token);
        System.out.println("🔧 TIMER SETTINGS: playerSeat = " + playerSeat);
        if (playerSeat == -1) return ResponseEntity.status(401).build(); // Unauthorized
        
        var entry = registry.get(id);
        if (entry == null) return ResponseEntity.notFound().build();
        
        System.out.println("🔧 TIMER SETTINGS: Current entry timers = " + entry.playTwoCardsTimeSeconds() + "s, " + entry.opponentPickTimeSeconds() + "s");
        
        // Determine who is the host based on game type
        boolean isHost = false;
        if (entry.playerAIsBot() && !entry.playerBIsBot()) {
            // Bot vs Human: Human (Player B) is host
            isHost = (playerSeat == 1);
        } else if (!entry.playerAIsBot() && entry.playerBIsBot()) {
            // Human vs Bot: Human (Player A) is host
            isHost = (playerSeat == 0);
        } else if (!entry.playerAIsBot() && !entry.playerBIsBot()) {
            // Human vs Human: Player A is host
            isHost = (playerSeat == 0);
        }
        
        if (!isHost) {
            return ResponseEntity.status(403).body("Only the host can change timer settings");
        }
        
        // Validate timer values
        if (body.playTwoCardsTimeSeconds < 10 || body.playTwoCardsTimeSeconds > 600) {
            return ResponseEntity.badRequest().body("Play Two Cards time must be between 10 and 600 seconds");
        }
        if (body.opponentPickTimeSeconds < 5 || body.opponentPickTimeSeconds > 300) {
            return ResponseEntity.badRequest().body("Opponent Pick time must be between 5 and 300 seconds");
        }
        
        boolean success = registry.updateTimerSettings(id, body.playTwoCardsTimeSeconds, body.opponentPickTimeSeconds);
        System.out.println("🔧 TIMER SETTINGS: Update success = " + success);
        
        if (success) {
            var updatedEntry = registry.get(id);
            System.out.println("🔧 TIMER SETTINGS: Updated entry timers = " + updatedEntry.playTwoCardsTimeSeconds() + "s, " + updatedEntry.opponentPickTimeSeconds() + "s");
        }
        
        return success ? ResponseEntity.ok("Timer settings updated") : ResponseEntity.badRequest().body("Could not update timer settings");
    }
    
    /**
     * Convert MatchStateDto.CardInfo to PlayerGameStateDto.CardInfo
     */
    private Map<String, PlayerGameStateDto.CardInfo> convertCardDefinitions(Map<String, MatchStateDto.CardInfo> original) {
        Map<String, PlayerGameStateDto.CardInfo> converted = new HashMap<>();
        for (var entry : original.entrySet()) {
            var original_card = entry.getValue();
            var converted_tiers = original_card.tiers().stream().map(tier -> 
                new PlayerGameStateDto.TierInfo(
                    tier.actions().stream().map(action ->
                        new PlayerGameStateDto.ActionInfo(
                            action.type(),
                            action.target(),
                            action.amount(),
                            action.count(),
                            action.description()
                        )
                    ).toList()
                )
            ).toList();
            
            converted.put(entry.getKey(), new PlayerGameStateDto.CardInfo(
                original_card.name(),
                original_card.tags(),
                converted_tiers
            ));
        }
        return converted;
    }
    
    /**
     * Convert MatchStateDto.EffectFeedback to PlayerGameStateDto.EffectFeedback
     */
    private List<PlayerGameStateDto.EffectFeedback> convertEffectFeedbackForPlayerDto(
            List<com.officeduel.engine.model.GameState.EffectFeedback> original) {
        return original.stream().map(effect ->
            new PlayerGameStateDto.EffectFeedback(
                effect.playerName(),
                effect.cardName(),
                effect.effectDescription(),
                effect.dotChange(),
                effect.timestamp()
            )
        ).toList();
    }
    
    // Track bot actions to avoid duplicates
    private final java.util.Set<String> scheduledBotActions = java.util.concurrent.ConcurrentHashMap.newKeySet();
    
    /**
     * Schedule bot auto-play from backend
     */
    private void scheduleAutoBotPlay(String id, MatchRegistry.Entry entry, com.officeduel.engine.model.GameState gs) {
        // Check if any player is a bot and should act
        boolean playerAIsBot = entry.playerAIsBot();
        boolean playerBIsBot = entry.playerBIsBot();
        int activePlayerIndex = gs.getActivePlayerIndex();
        var phase = gs.getPhase();
        
        // Create unique key for this game state to avoid duplicate bot actions
        String actionKey = id + "-" + phase + "-" + activePlayerIndex + "-" + gs.getHistory().size();
        
        boolean shouldActPlayerA = false;
        boolean shouldActPlayerB = false;
        
        if (phase == com.officeduel.engine.model.GameState.Phase.PLAY_TWO_CARDS) {
            // Active player should play cards
            if (activePlayerIndex == 0 && playerAIsBot) shouldActPlayerA = true;
            if (activePlayerIndex == 1 && playerBIsBot) shouldActPlayerB = true;
        } else if (phase == com.officeduel.engine.model.GameState.Phase.OPPONENT_PICK) {
            // In OPPONENT_PICK phase, the INACTIVE player should choose
            // Bot should only auto-choose if the INACTIVE player is a bot AND the ACTIVE player is human
            int inactivePlayerIndex = 1 - activePlayerIndex;
            boolean activePlayerIsBot = (activePlayerIndex == 0) ? playerAIsBot : playerBIsBot;
            boolean inactivePlayerIsBot = (inactivePlayerIndex == 0) ? playerAIsBot : playerBIsBot;
            
            System.out.println("DEBUG OPPONENT_PICK: activePlayer=" + activePlayerIndex + " (isBot=" + activePlayerIsBot + 
                              "), inactivePlayer=" + inactivePlayerIndex + " (isBot=" + inactivePlayerIsBot + ")");
            
            // CORRECT LOGIC: Only schedule bot action if:
            // 1. The ACTIVE player is HUMAN (human played the cards), AND  
            // 2. The INACTIVE player is BOT (bot should choose between the two cards)
            if (!activePlayerIsBot && inactivePlayerIsBot) {
                if (inactivePlayerIndex == 0) {
                    shouldActPlayerA = true;
                    System.out.println("DEBUG OPPONENT_PICK: Human played cards, scheduling bot (Player A) to choose");
                } else {
                    shouldActPlayerB = true;
                    System.out.println("DEBUG OPPONENT_PICK: Human played cards, scheduling bot (Player B) to choose");
                }
            }
            
            // If bot played cards, human should choose manually (NO auto-action)
            if (activePlayerIsBot && !inactivePlayerIsBot) {
                System.out.println("DEBUG OPPONENT_PICK: Bot played cards, letting human (Player " + 
                                  (inactivePlayerIndex == 0 ? "A" : "B") + ") choose manually");
            }
        }
        
        if ((shouldActPlayerA || shouldActPlayerB) && !scheduledBotActions.contains(actionKey)) {
            // Mark this action as scheduled to avoid duplicates
            scheduledBotActions.add(actionKey);
            
            // Schedule bot action in background thread
            final int botPlayerIndex = shouldActPlayerA ? 0 : 1;
            final var finalEntry = entry;
            final var finalGs = gs;
            final var finalPhase = phase;
            final String finalActionKey = actionKey;
            
            System.out.println("Scheduling bot action for Player " + (botPlayerIndex == 0 ? "A" : "B") + " in phase " + phase + 
                              " (activePlayer=" + activePlayerIndex + ", playerAIsBot=" + playerAIsBot + ", playerBIsBot=" + playerBIsBot + ")");
            
            new Thread(() -> {
                try {
                    Thread.sleep(2000); // 2 second delay
                    executeBotAction(finalEntry, finalGs, botPlayerIndex, finalPhase);
                    // Clean up the action key after execution
                    scheduledBotActions.remove(finalActionKey);
                } catch (Exception e) {
                    System.out.println("Bot auto-play failed: " + e.getMessage());
                    scheduledBotActions.remove(finalActionKey);
                }
            }).start();
        }
    }
    
    private void executeBotAction(MatchRegistry.Entry entry, com.officeduel.engine.model.GameState gs, 
                                 int botPlayerIndex, com.officeduel.engine.model.GameState.Phase phase) {
        try {
            if (phase == com.officeduel.engine.model.GameState.Phase.PLAY_TWO_CARDS) {
                // Bot plays two random cards but does NOT auto-choose
                var botPlayer = botPlayerIndex == 0 ? gs.getPlayerA() : gs.getPlayerB();
                var handCards = botPlayer.getHand();
                
                if (handCards.size() >= 2) {
                    var card1 = handCards.get(0).cardId();
                    var card2 = handCards.get(1).cardId();
                    
                    System.out.println("Bot (Player " + (botPlayerIndex == 0 ? "A" : "B") + ") auto-playing cards: " + card1 + ", " + card2);
                    
                    // Use custom method that only plays cards without auto-choosing
                    playCardsOnly(entry.engine(), gs, card1, card2);
                }
            } else if (phase == com.officeduel.engine.model.GameState.Phase.OPPONENT_PICK) {
                // Bot chooses randomly
                boolean chooseFaceUp = Math.random() < 0.5;
                String picked = chooseFaceUp ? gs.getFaceUpCardId() : gs.getFaceDownCardId();
                String remaining = chooseFaceUp ? gs.getFaceDownCardId() : gs.getFaceUpCardId();
                
                System.out.println("Bot (Player " + (botPlayerIndex == 0 ? "A" : "B") + ") auto-choosing: " + (chooseFaceUp ? "face up" : "face down"));
                entry.engine().playTurnWithChoice(picked, remaining);
            }
        } catch (Exception e) {
            System.out.println("Bot action execution failed: " + e.getMessage());
        }
    }
    
    /**
     * Custom method that only plays cards and transitions to OPPONENT_PICK phase
     * without auto-choosing. Based on TurnEngine.playTurnAuto() but stops before auto-choice.
     */
    private void playCardsOnly(com.officeduel.engine.engine.TurnEngine engine, 
                              com.officeduel.engine.model.GameState gs, 
                              String faceUpId, String faceDownId) {
        try {
            // Verify we're in the correct phase
            if (gs.getPhase() != com.officeduel.engine.model.GameState.Phase.PLAY_TWO_CARDS) {
                System.out.println("ERROR: playCardsOnly called in wrong phase: " + gs.getPhase());
                return;
            }
            
            System.out.println("DEBUG playCardsOnly: Playing cards " + faceUpId + ", " + faceDownId + " and transitioning to OPPONENT_PICK");
            
            // Set the cards and change phase to OPPONENT_PICK (like playTurnAuto lines 133-135)
            gs.setFaceUpCardId(faceUpId);
            gs.setFaceDownCardId(faceDownId);
            gs.setPhase(com.officeduel.engine.model.GameState.Phase.OPPONENT_PICK);
            
            System.out.println("DEBUG playCardsOnly: Phase changed to " + gs.getPhase() + ", waiting for opponent to choose");
            
            // DON'T auto-choose - let the human choose!
        } catch (Exception e) {
            System.out.println("ERROR in playCardsOnly: " + e.getMessage());
        }
    }
    
}


