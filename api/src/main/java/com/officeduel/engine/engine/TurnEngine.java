package com.officeduel.engine.engine;

import com.officeduel.engine.cards.CardDefinitionSet;
import com.officeduel.engine.model.GameState;
import com.officeduel.engine.model.MatchPlayer;
import com.officeduel.engine.model.PlayerState;
import com.officeduel.engine.telemetry.Telemetry;

import java.util.List;

public final class TurnEngine {
    private final GameState state;
    private final CardIndex index;
    private final EffectResolver effects;
    private final Telemetry telemetry;
    private String playerAName = "Jugador A";
    private String playerBName = "Jugador B";

    public TurnEngine(GameState state, CardIndex index) {
        this.state = state;
        this.index = index;
        this.effects = new EffectResolver(state, index);
        this.telemetry = null;
    }

    public TurnEngine(GameState state, CardIndex index, Telemetry telemetry) {
        this.state = state;
        this.index = index;
        this.effects = new EffectResolver(state, index);
        this.telemetry = telemetry;
    }

    public void setPlayerNames(String playerAName, String playerBName) {
        this.playerAName = playerAName != null ? playerAName : "Jugador A";
        this.playerBName = playerBName != null ? playerBName : "Jugador B";
    }

    public void startMatch() {
        // Initialize LP
        state.getPlayerA().setLifePoints(index.maxLp());
        state.getPlayerB().setLifePoints(index.maxLp());
        // TODO: init decks from default pool; for now keep empty
        drawUpTo(state.getPlayerA());
        drawUpTo(state.getPlayerB());
        if (telemetry != null) telemetry.emit(new Telemetry.MatchStart(state.getRng().seed()));
    }

    public void playTurnAuto() {
        requirePhase(com.officeduel.engine.model.GameState.Phase.PLAY_TWO_CARDS);
        
        // Clear recently added cards from previous turn at the start of new turn
        // Keep effects recent for persistent log view
        state.clearRecentlyAddedCardsA();
        state.clearRecentlyAddedCardsB();
        
        MatchPlayer active = new MatchPlayer(state.getActivePlayer());
        MatchPlayer opponent = new MatchPlayer(state.getInactivePlayer());

        if (active.state().isSkipNextTurn()) {
            active.state().setSkipNextTurn(false);
            endStep();
            return;
        }

        // Auto: pick two random distinct cards from hand if available
        int h = active.state().getHand().size();
        if (h == 0) { endStep(); return; }
        int idxUp = state.getRng().nextInt(h);
        String faceUpId = active.state().getHand().get(idxUp).cardId();
        int idxDown = h > 1 ? (idxUp + 1 + state.getRng().nextInt(h - 1)) % h : idxUp;
        String faceDownId = active.state().getHand().get(idxDown).cardId();
        state.setFaceUpCardId(faceUpId);
        state.setFaceDownCardId(faceDownId);

        // Opponent picks one randomly
        boolean pickFaceUp = state.getRng().nextBoolean();
        String picked = pickFaceUp ? faceUpId : faceDownId;
        String remaining = pickFaceUp ? faceDownId : faceUpId;

        resolveRecruit(picked, opponent, active, opponent); // opponent gets picked effect and plays it
        resolveRecruit(remaining, active, opponent, active); // active gets remaining effect and plays it

        // Move cards to tableau: opponent gets picked, active gets remaining
        opponent.state().getTableau().add(new com.officeduel.engine.model.Cards(picked));
        active.state().getTableau().add(new com.officeduel.engine.model.Cards(remaining));

        // Remove from hand (by id, first occurrence)
        removeFirstById(active.state(), faceUpId);
        removeFirstById(active.state(), faceDownId);

        // Refill
        drawUpTo(active.state());

        endStep();
    }

    public void playTurnManual(String faceUpId, String faceDownId) {
        requirePhase(com.officeduel.engine.model.GameState.Phase.PLAY_TWO_CARDS);
        
        // Clear recently added cards from previous turn at the start of new turn
        // Keep effects recent for persistent log view
        state.clearRecentlyAddedCardsA();
        state.clearRecentlyAddedCardsB();
        
        MatchPlayer active = new MatchPlayer(state.getActivePlayer());
        MatchPlayer opponent = new MatchPlayer(state.getInactivePlayer());

        if (active.state().isSkipNextTurn()) {
            active.state().setSkipNextTurn(false);
            endStep();
            return;
        }

        state.setFaceUpCardId(faceUpId);
        state.setFaceDownCardId(faceDownId);

        boolean pickFaceUp = state.getRng().nextBoolean();
        String picked = pickFaceUp ? faceUpId : faceDownId;
        String remaining = pickFaceUp ? faceDownId : faceUpId;

        resolveRecruit(picked, opponent, active, opponent); // opponent gets picked effect and plays it
        resolveRecruit(remaining, active, opponent, active); // active gets remaining effect and plays it

        // Distribute cards correctly: opponent gets picked, active gets remaining
        opponent.state().getTableau().add(new com.officeduel.engine.model.Cards(picked));
        active.state().getTableau().add(new com.officeduel.engine.model.Cards(remaining));

        removeFirstById(active.state(), faceUpId);
        removeFirstById(active.state(), faceDownId);

        drawUpTo(active.state());

        endStep();
    }

    public void playTurnWithChoice(String pickedCardId, String remainingCardId) {
        requirePhase(com.officeduel.engine.model.GameState.Phase.OPPONENT_PICK);
        
        // Clear recently added cards from previous turn at the start of new turn
        // Keep effects recent for persistent log view
        System.out.println("DEBUG playTurnWithChoice: Clearing recently added cards (keeping effects for log)");
        state.clearRecentlyAddedCardsA();
        state.clearRecentlyAddedCardsB();
        
        MatchPlayer active = new MatchPlayer(state.getActivePlayer());
        MatchPlayer opponent = new MatchPlayer(state.getInactivePlayer());

        System.out.println("DEBUG playTurnWithChoice:");
        System.out.println("  Picked card: " + pickedCardId);
        System.out.println("  Remaining card: " + remainingCardId);
        System.out.println("  Active player tableau before: " + active.state().getTableau().stream().map(c -> c.cardId()).toList());
        System.out.println("  Opponent tableau before: " + opponent.state().getTableau().stream().map(c -> c.cardId()).toList());

        if (active.state().isSkipNextTurn()) {
            active.state().setSkipNextTurn(false);
            endStep();
            return;
        }

        // Resolve effects: remaining card first (submitter), then picked card (opponent)
        System.out.println("  Resolving remaining card effects...");
        resolveRecruit(remainingCardId, active, opponent, active); // active plays remaining card
        System.out.println("  Resolving picked card effects...");
        resolveRecruit(pickedCardId, opponent, active, opponent); // opponent plays picked card

        // Move cards to tableau: active gets remaining card, opponent gets picked card
        System.out.println("  Adding cards to tableau...");
        active.state().getTableau().add(new com.officeduel.engine.model.Cards(remainingCardId));
        opponent.state().getTableau().add(new com.officeduel.engine.model.Cards(pickedCardId));
        
        // Track recently added cards
        System.out.println("DEBUG: Tracking recently added cards - remaining: " + remainingCardId + ", picked: " + pickedCardId);
        if (active.state() == state.getPlayerA()) {
            state.addRecentlyAddedCardA(remainingCardId);
            state.addRecentlyAddedCardB(pickedCardId);
            System.out.println("DEBUG: Added to A: " + remainingCardId + ", Added to B: " + pickedCardId);
        } else {
            state.addRecentlyAddedCardB(remainingCardId);
            state.addRecentlyAddedCardA(pickedCardId);
            System.out.println("DEBUG: Added to B: " + remainingCardId + ", Added to A: " + pickedCardId);
        }
        System.out.println("DEBUG: Recently added cards A: " + state.getRecentlyAddedCardsA());
        System.out.println("DEBUG: Recently added cards B: " + state.getRecentlyAddedCardsB());
        
        System.out.println("  Active player tableau after: " + active.state().getTableau().stream().map(c -> c.cardId()).toList());
        System.out.println("  Opponent tableau after: " + opponent.state().getTableau().stream().map(c -> c.cardId()).toList());

        // Remove from hand
        removeFirstById(active.state(), state.getFaceUpCardId());
        removeFirstById(active.state(), state.getFaceDownCardId());

        // Refill hand
        drawUpTo(active.state());

        // Reset phase and clear card IDs
        state.setPhase(com.officeduel.engine.model.GameState.Phase.PLAY_TWO_CARDS);
        state.setFaceUpCardId(null);
        state.setFaceDownCardId(null);

        // End step (this will swap active player)
        System.out.println("DEBUG playTurnWithChoice: About to call endStep()");
        endStep();
        System.out.println("DEBUG playTurnWithChoice: endStep() completed");
    }

    private void resolveRecruit(String cardId, MatchPlayer recipient, MatchPlayer other, MatchPlayer player) {
        CardDefinitionSet.CardDef def = index.get(cardId);
        
        // Debug logging
        int existingCopies = (int) recipient.state().getTableau().stream().filter(c -> c.cardId().equals(cardId)).count();
        System.out.println("DEBUG resolveRecruit:");
        System.out.println("  Card ID: " + cardId);
        System.out.println("  Card Name: " + def.name());
        System.out.println("  Recipient: " + (recipient.state() == state.getPlayerA() ? "Player A" : "Player B"));
        System.out.println("  Existing copies in tableau: " + existingCopies);
        System.out.println("  Tableau contents: " + recipient.state().getTableau().stream().map(c -> c.cardId()).toList());
        
        // Count existing copies in tableau + 1 for the card being played
        int copies = existingCopies + 1;
        int tierIdx = Math.min(def.tiers().size(), Math.max(1, copies)) - 1;
        List<CardDefinitionSet.Action> actions = def.tiers().get(tierIdx).actions();
        
        System.out.println("  Calculated copies: " + copies);
        System.out.println("  Tier index: " + tierIdx);
        System.out.println("  Actions count: " + actions.size());
        
        // Generate effect description before applying
        String effectDescription = generateEffectDescription(def.name(), actions, recipient, other);
        
        effects.applyActions(actions, player, other);
        
        // Add effect feedback after applying
        String playerName = recipient.state() == state.getPlayerA() ? playerAName : playerBName;
        state.addEffectFeedback(playerName, def.name(), effectDescription);
        
        // After applying the card effects, immediately check if the match has been decided
        checkWinnerMidTurn();
        if (telemetry != null) telemetry.emit(new Telemetry.CardPlayed(recipient.state() == state.getPlayerA() ? 0 : 1, cardId, tierIdx + 1));
    }

    private void drawUpTo(PlayerState ps) {
        while (ps.getHand().size() < ps.getMaxHandSize() && !ps.getDeck().isEmpty()) {
            if (ps.consumeBlockDrawIfAny()) continue;
            ps.getHand().add(ps.getDeck().pop());
        }
    }

    private void endStep() {
        System.out.println("DEBUG endStep: Starting endStep(), current activePlayerIndex = " + state.getActivePlayerIndex());
        // Check dot system win conditions first
        int winner = state.winnerIndexOrMinusOne();
        if (winner != -1) {
            // Someone won by dots (5 or -5)
            System.out.println("DEBUG endStep: Winner found, exiting early");
            if (telemetry != null) telemetry.emit(new Telemetry.MatchEnd(winner, state.getPlayerA().getLifePoints(), state.getPlayerB().getLifePoints()));
            return;
        }
        
        // LP system is deprecated - only use dot system for win conditions
        System.out.println("DEBUG endStep: LP checks removed, using only dot system");
        
        // Tick statuses
        state.getPlayerA().getBuffs().getStatuses().tickEndOfTurn();
        state.getPlayerB().getBuffs().getStatuses().tickEndOfTurn();
        
        // Keep recent effects and recently added cards until next turn so frontend can see them
        System.out.println("DEBUG endStep: Keeping recent effects and recently added cards for frontend");
        
        // Next turn
        int previousActivePlayer = state.getActivePlayerIndex();
        state.swapActive();
        int newActivePlayer = state.getActivePlayerIndex();
        System.out.println("DEBUG endStep: Turn swapped from Player " + (previousActivePlayer == 0 ? "A" : "B") + " to Player " + (newActivePlayer == 0 ? "A" : "B"));
    }

    private void removeFirstById(PlayerState ps, String cardId) {
        for (int i = 0; i < ps.getHand().size(); i++) {
            if (ps.getHand().get(i).cardId().equals(cardId)) {
                ps.getHand().remove(i);
                return;
            }
        }
    }

    private void requirePhase(com.officeduel.engine.model.GameState.Phase expectedPhase) {
        if (state.getPhase() != expectedPhase) {
            throw new IllegalStateException("Invalid phase: expected " + expectedPhase + " but was " + state.getPhase());
        }
    }
    
    private String generateEffectDescription(String cardName, List<CardDefinitionSet.Action> actions, MatchPlayer recipient, MatchPlayer other) {
        if (actions.isEmpty()) return cardName + " no tiene efectos";
        
        StringBuilder desc = new StringBuilder();
        desc.append(cardName).append(": ");
        
        for (int i = 0; i < actions.size(); i++) {
            if (i > 0) desc.append(", ");
            
            CardDefinitionSet.Action action = actions.get(i);
            String target = getTargetDescription(action.target());
            
            switch (action.type()) {
                case "damage" -> desc.append("inflige ").append(action.amount()).append(" de daño a ").append(target);
                case "heal" -> desc.append("cura ").append(action.amount()).append(" LP a ").append(target);
                case "draw" -> desc.append("hace que ").append(target).append(" robe ").append(action.count()).append(" carta(s)");
                case "skip_next_turn" -> desc.append("hace que ").append(target).append(" salte el siguiente turno");
                case "block_next_draw" -> desc.append("bloquea ").append(action.count()).append(" robo(s) de ").append(target);
                case "discard_random" -> desc.append("hace que ").append(target).append(" descarte ").append(action.count()).append(" carta(s) al azar");
                case "discard_hand" -> desc.append("hace que ").append(target).append(" descarte toda la mano");
                case "set_lp_to_full" -> desc.append("restaura los LP de ").append(target).append(" al máximo");
                case "status" -> desc.append("aplica estado ").append(action.status()).append(" a ").append(target);
                case "reveal_random_cards" -> desc.append("revela ").append(action.count()).append(" carta(s) de ").append(target);
                case "steal_random_card_from_hand" -> desc.append("roba ").append(action.count()).append(" carta(s) de la mano de ").append(target);
                case "destroy_random_cards_in_tableau" -> desc.append("destruye ").append(action.count()).append(" carta(s) del tableau de ").append(target);
                case "modify_max_hand_size" -> desc.append("modifica el tamaño de mano de ").append(target).append(" en ").append(action.delta());
                case "grant_extra_face_down_play" -> desc.append("permite que ").append(target).append(" juegue ").append(action.count()).append(" carta(s) boca abajo extra");
                case "win_if_condition" -> desc.append("gana si ").append(target).append(" cumple la condición");
                case "copy_last_card_effect" -> desc.append("copia el último efecto ").append(action.times()).append(" vez(es) para ").append(target);
                case "push" -> {
                    int amount = action.amount() != null ? action.amount() : 0;
                    if (amount > 0) {
                        desc.append("empuja <span class='push-positive'>+").append(amount).append("</span> puntos");
                    } else if (amount < 0) {
                        desc.append("empuja <span class='push-negative'>").append(amount).append("</span> puntos");
                    } else {
                        desc.append("empuja 0 puntos");
                    }
                }
                default -> desc.append(action.type()).append(" a ").append(target);
            }
        }
        
        return desc.toString();
    }
    
    private String getTargetDescription(String target) {
        if (target == null) {
            return "objetivo desconocido";
        }
        return switch (target) {
            case "self" -> "sí mismo";
            case "opponent" -> "el oponente";
            case "both" -> "ambos jugadores";
            default -> target;
        };
    }

    /**
     * Checks for a winner right after an effect has been resolved during the turn. This mirrors the logic in
     * {@link #endStep()} but without advancing the game phase or ticking statuses. It allows the engine to detect
     * a victory condition immediately after an effect is applied (for example, when a dot threshold is reached or
     * a player’s life points drop to zero) rather than waiting until the end of the turn.
     */
    private void checkWinnerMidTurn() {
        // This method only checks for winners but doesn't end the turn
        // The actual turn ending and swapping happens in endStep()
        int winner = state.winnerIndexOrMinusOne();
        if (winner != -1) {
            if (telemetry != null) telemetry.emit(new Telemetry.MatchEnd(winner, state.getPlayerA().getLifePoints(), state.getPlayerB().getLifePoints()));
            // Don't return here - let the turn continue to endStep() for proper cleanup
        }

        // LP system is deprecated - only use dot system for win conditions
        System.out.println("DEBUG checkWinnerMidTurn: LP checks removed, using only dot system");
    }
}


