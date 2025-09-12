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

        resolveRecruit(picked, opponent, active); // opponent gets picked effect
        resolveRecruit(remaining, active, opponent); // active gets remaining effect

        // Move cards to tableau
        active.state().getTableau().add(new com.officeduel.engine.model.Cards(faceUpId));
        active.state().getTableau().add(new com.officeduel.engine.model.Cards(faceDownId));

        // Remove from hand (by id, first occurrence)
        removeFirstById(active.state(), faceUpId);
        removeFirstById(active.state(), faceDownId);

        // Refill
        drawUpTo(active.state());

        endStep();
    }

    public void playTurnManual(String faceUpId, String faceDownId) {
        requirePhase(com.officeduel.engine.model.GameState.Phase.PLAY_TWO_CARDS);
        
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

        resolveRecruit(picked, opponent, active);
        resolveRecruit(remaining, active, opponent);

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
        
        MatchPlayer active = new MatchPlayer(state.getActivePlayer());
        MatchPlayer opponent = new MatchPlayer(state.getInactivePlayer());

        if (active.state().isSkipNextTurn()) {
            active.state().setSkipNextTurn(false);
            endStep();
            return;
        }

        // Resolve effects: remaining card first (submitter), then picked card (opponent)
        resolveRecruit(remainingCardId, active, opponent);
        resolveRecruit(pickedCardId, opponent, active);

        // Move cards to tableau: active gets remaining card, opponent gets picked card
        active.state().getTableau().add(new com.officeduel.engine.model.Cards(remainingCardId));
        opponent.state().getTableau().add(new com.officeduel.engine.model.Cards(pickedCardId));

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
        endStep();
    }

    private void resolveRecruit(String cardId, MatchPlayer recipient, MatchPlayer other) {
        CardDefinitionSet.CardDef def = index.get(cardId);
        int copies = (int) recipient.state().getTableau().stream().filter(c -> c.cardId().equals(cardId)).count() + 1;
        int tierIdx = Math.min(def.tiers().size(), Math.max(1, copies)) - 1;
        List<CardDefinitionSet.Action> actions = def.tiers().get(tierIdx).actions();
        effects.applyActions(actions, recipient, other);
        if (telemetry != null) telemetry.emit(new Telemetry.CardPlayed(recipient.state() == state.getPlayerA() ? 0 : 1, cardId, tierIdx + 1));
    }

    private void drawUpTo(PlayerState ps) {
        while (ps.getHand().size() < ps.getMaxHandSize() && !ps.getDeck().isEmpty()) {
            if (ps.consumeBlockDrawIfAny()) continue;
            ps.getHand().add(ps.getDeck().pop());
        }
    }

    private void endStep() {
        // Check dot system win conditions first
        int winner = state.winnerIndexOrMinusOne();
        if (winner != -1) {
            // Someone won by dots (5 or -5)
            if (telemetry != null) telemetry.emit(new Telemetry.MatchEnd(winner, state.getPlayerA().getLifePoints(), state.getPlayerB().getLifePoints()));
            return;
        }
        
        // Check legacy LP win conditions (for backward compatibility)
        if (state.getPlayerA().getLifePoints() <= 0 && state.getPlayerB().getLifePoints() <= 0) {
            // active player wins tie
            if (state.getActivePlayerIndex() == 0) {
                state.getPlayerB().setLifePoints(0);
                state.getPlayerA().setLifePoints(1);
            } else {
                state.getPlayerA().setLifePoints(0);
                state.getPlayerB().setLifePoints(1);
            }
            return;
        }
        if (state.getPlayerA().getLifePoints() <= 0 || state.getPlayerB().getLifePoints() <= 0) {
            if (telemetry != null) telemetry.emit(new Telemetry.MatchEnd(state.winnerIndexOrMinusOne(), state.getPlayerA().getLifePoints(), state.getPlayerB().getLifePoints()));
            return;
        }
        
        // Tick statuses
        state.getPlayerA().getBuffs().getStatuses().tickEndOfTurn();
        state.getPlayerB().getBuffs().getStatuses().tickEndOfTurn();
        // Next turn
        state.swapActive();
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
}


