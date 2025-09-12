package com.officeduel.service;

import com.officeduel.engine.cards.CardDefinitionSet;
import com.officeduel.engine.engine.CardIndex;
import com.officeduel.service.dto.MatchStateDto;
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
                id, 0, 0, 0, 0, 0, 0, 0, 
                List.of(), List.of(), List.of(), 
                getCardDefinitions(), "LOBBY", null, null, false, 0,
                e.playerA(), e.playerB(), e.readyA(), e.readyB(), false, e.playerAIsBot(), e.playerBIsBot(),
                List.of()
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
                gs.getPlayerA().getLifePoints(),
                gs.getPlayerB().getLifePoints(),
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
                convertEffectFeedback(gs.getRecentEffects())
        ));
    }

    @PostMapping("/{id}/auto")
    public ResponseEntity<MatchStateDto> auto(@PathVariable String id, @RequestParam(required = false) Integer seat) {
        var e = registry.get(id);
        if (e == null) return ResponseEntity.notFound().build();
        if (e.engine() == null) return ResponseEntity.status(409).body(null); // Match not started
        
        try {
            e.engine().playTurnAuto();
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
                gs.getPlayerA().getLifePoints(),
                gs.getPlayerB().getLifePoints(),
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
                convertEffectFeedback(gs.getRecentEffects())
        ));
    }

    public record SubmitTurnBody(String faceUpId, String faceDownId) {}
    public record OpponentChoiceBody(boolean chooseFaceUp) {}
    public record JoinMatchBody(String name) {}
    public record JoinMatchResponse(int seat) {}

    @PostMapping("/{id}/play")
    public ResponseEntity<MatchStateDto> play(@PathVariable String id, @RequestBody SubmitTurnBody body, @RequestParam(required = false) Integer seat) {
        var e = registry.get(id);
        if (e == null) return ResponseEntity.notFound().build();
        if (e.engine() == null) return ResponseEntity.status(409).body(null); // Match not started
        if (body == null || body.faceUpId() == null || body.faceDownId() == null) return ResponseEntity.badRequest().build();
        
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
                gs.getPlayerA().getLifePoints(),
                gs.getPlayerB().getLifePoints(),
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
                convertEffectFeedback(gs.getRecentEffects())
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
                gs.getPlayerA().getLifePoints(),
                gs.getPlayerB().getLifePoints(),
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
                convertEffectFeedback(gs.getRecentEffects())
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
            case "status" -> desc.append("Aplica estado ").append(action.status()).append(" a ").append(target);
            case "reveal_random_cards" -> desc.append("Revela ").append(action.count()).append(" carta(s) de ").append(target);
            case "steal_random_card_from_hand" -> desc.append("Roba ").append(action.count()).append(" carta(s) de la mano de ").append(target);
            case "destroy_random_cards_in_tableau" -> desc.append("Destruye ").append(action.count()).append(" carta(s) del tableau de ").append(target);
            case "modify_max_hand_size" -> desc.append("Modifica el tamaño de mano de ").append(target).append(" en ").append(action.delta());
            case "grant_extra_face_down_play" -> desc.append("Permite que ").append(target).append(" juegue ").append(action.count()).append(" carta(s) boca abajo extra");
            case "win_if_condition" -> desc.append("Gana si ").append(target).append(" cumple la condición");
            case "copy_last_card_effect" -> desc.append("Copia el último efecto ").append(action.times()).append(" vez(es) para ").append(target);
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
                effect.lpChangeA(),
                effect.lpChangeB(),
                effect.timestamp()
            ))
            .toList();
    }
    
    @PostMapping("/{id}/join")
    public ResponseEntity<JoinMatchResponse> join(@PathVariable String id, @RequestBody JoinMatchBody body) {
        if (body == null || body.name() == null || body.name().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        int seat = registry.joinMatch(id, body.name().trim());
        if (seat == -1) return ResponseEntity.notFound().build();
        if (seat == -2) return ResponseEntity.status(409).body(new JoinMatchResponse(-1)); // Conflict - match full
        
        return ResponseEntity.ok(new JoinMatchResponse(seat));
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
    
    @PostMapping("/{id}/bot-turn")
    public ResponseEntity<MatchStateDto> botTurn(@PathVariable String id, @RequestParam(required = false) Integer seat) {
        registry.autoPlayBotTurn(id);
        return get(id, seat);
    }
}


