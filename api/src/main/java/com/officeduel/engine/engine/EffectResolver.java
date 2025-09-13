package com.officeduel.engine.engine;

import com.officeduel.engine.cards.CardDefinitionSet.Action;
import com.officeduel.engine.model.GameState;
import com.officeduel.engine.model.MatchPlayer;
import com.officeduel.engine.model.StatusType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EffectResolver {
    private final GameState state;
    private final CardIndex index;

    public EffectResolver(GameState state, CardIndex index) {
        this.state = state;
        this.index = index;
    }

    public void applyActions(List<Action> actions, MatchPlayer source, MatchPlayer opponent) {
        for (Action action : actions) {
            if ("both".equals(action.target())) {
                apply(action, source, opponent);
                apply(action, opponent, source);
            } else {
                apply(action, source, opponent);
            }
        }
        state.setLastActionsAppliedFor(source.state(), actions);
    }

    private void apply(Action a, MatchPlayer self, MatchPlayer opp) {
        switch (a.type()) {
            case "push" -> applyPush(self, a.amount() == null ? 0 : a.amount(), a.target());
            case "damage" -> applyDamage(targetOf(a.target(), self, opp), a.amount() == null ? 0 : a.amount());
            case "heal" -> applyHeal(targetOf(a.target(), self, opp), a.amount() == null ? 0 : a.amount());
            case "draw" -> applyDraw(targetOf(a.target(), self, opp), a.count() == null ? 0 : a.count());
            case "skip_next_turn" -> targetOf(a.target(), self, opp).state().setSkipNextTurn(true);
            case "block_next_draw" -> targetOf(a.target(), self, opp).state().incrementBlockNextDraw(a.count() == null ? 1 : a.count());
            case "discard_random" -> discardRandom(targetOf(a.target(), self, opp), a.count() == null ? 1 : a.count());
            case "discard_hand" -> discardHand(targetOf(a.target(), self, opp));
            case "set_lp_to_full" -> targetOf(a.target(), self, opp).state().setLifePoints(index.maxLp());
            case "status" -> applyStatus(a, self, opp);
            case "reveal_random_cards" -> revealRandom(targetOf(a.target(), self, opp), a.count() == null ? 1 : a.count());
            case "steal_random_card_from_hand" -> stealRandom(self, targetOf(a.source(), self, opp), a.count() == null ? 1 : a.count());
            case "destroy_random_cards_in_tableau" -> destroyRandomTableau(targetOf(a.target(), self, opp), a.count() == null ? 1 : a.count());
            case "modify_max_hand_size" -> modifyMaxHandSize(targetOf(a.target(), self, opp), a.delta() == null ? 0 : a.delta());
            case "grant_extra_face_down_play" -> targetOf(a.target(), self, opp).state().addExtraFaceDownPlays(a.count() == null ? 1 : a.count());
            case "win_if_condition" -> checkWinCondition(self, a);
            case "copy_last_card_effect" -> copyLast(self, opp, a.times() == null ? 1 : a.times());
            case "steal_card_from_tableau_and_play" -> stealFromTableauAndPlay(self, opp, a);
            case "conditional_push_if_opponent_hand_empty" -> conditionalPushIfHandEmpty(self, opp, a.amount() == null ? 0 : a.amount());
            case "fallback_push_if_no_trigger" -> fallbackPush(self, a.amount() == null ? 0 : a.amount());
            case "lose_if_condition_when_blocked" -> checkLoseConditionWhenBlocked(self, a);
            case "grant_extra_turns" -> grantExtraTurns(targetOf(a.target(), self, opp), a.count() == null ? 1 : a.count());
            case "noop" -> {} // No operation
            default -> state.getHistory().add("Unknown action: " + a.type());
        }
    }

    private MatchPlayer targetOf(String target, MatchPlayer self, MatchPlayer opp) {
        if ("self".equals(target)) return self;
        if ("opponent".equals(target)) return opp;
        // both is handled by caller by splitting actions into two if needed; fallback to self
        return self;
    }

    private void applyPush(MatchPlayer source, int amount, String target) {
        // Push system: amount is relative to the player who plays the card
        // Positive amount = benefits the player (moves counter toward them)
        // Negative amount = hurts the player (moves counter away from them)
        // Target parameter is ignored for push actions
        
        // Determine direction based on which player is the source
        int adjustedAmount = amount;
        if (source.state() == state.getPlayerB()) {
            // Player B playing: positive amount moves counter toward Player B (negative direction)
            adjustedAmount = -amount;
        }
        // Player A playing: positive amount moves counter toward Player A (positive direction)
        
        state.addToDotCounter(adjustedAmount);
        state.getHistory().add("Push " + amount + " by " + (source.state() == state.getPlayerA() ? "Player A" : "Player B") + " -> adjusted: " + adjustedAmount);
        
        // Add effect feedback
        String playerName = source.state() == state.getPlayerA() ? "Player A" : "Player B";
        String effectDescription = "Push " + amount + " (adjusted: " + adjustedAmount + ")";
        System.out.println("DEBUG addEffectFeedback: Adding push effect for " + playerName + ": " + effectDescription);
        state.addEffectFeedback(playerName, "Push Effect", effectDescription);
    }

    private void applyDamage(MatchPlayer target, int amount) {
        // reflect/shield logic
        if (target.state().getBuffs().getStatuses().has(StatusType.REFLECT_ALL_DAMAGE)) {
            // reflect back to the other player
            MatchPlayer other = target == new MatchPlayer(state.getPlayerA()) ? new MatchPlayer(state.getPlayerB()) : new MatchPlayer(state.getPlayerA());
            // Remove the reflect status to prevent infinite loops
            target.state().getBuffs().getStatuses().remove(StatusType.REFLECT_ALL_DAMAGE);
            applyDamage(other, amount);
            return;
        }
        if (target.state().getBuffs().getStatuses().has(StatusType.SHIELD)) {
            target.state().getBuffs().getStatuses().consumeShieldIfAny();
            state.getHistory().add("Shield absorbed damage");
            return;
        }
        // thorns reflects fixed damage once
        if (target.state().getBuffs().getStatuses().has(StatusType.THORNS)) {
            int thorns = target.state().getBuffs().getStatuses().amount(StatusType.THORNS);
            // reflect to opponent using dot system
            boolean toA = target.state() == state.getPlayerB();
            if (toA) {
                state.addToDotCounter(-thorns); // A loses dots
            } else {
                state.addToDotCounter(thorns); // B loses dots (positive for A)
            }
            target.state().getBuffs().getStatuses().remove(StatusType.THORNS);
            state.getHistory().add("Thorns reflected:" + thorns);
        }
        // Apply damage to dot counter: damage to target = dots move away from target
        boolean toA = target.state() == state.getPlayerA();
        if (toA) {
            state.addToDotCounter(-amount); // A takes damage = dots go down
        } else {
            state.addToDotCounter(amount); // B takes damage = dots go up (favor A)
        }
        state.getHistory().add("Damage:" + amount + " (dots: " + state.getSharedDotCounter() + ")");
        
        // Add effect feedback
        String playerName = target.state() == state.getPlayerA() ? "Player A" : "Player B";
        System.out.println("DEBUG addEffectFeedback: Adding damage effect for " + playerName + ": Damage " + amount);
        state.addEffectFeedback(playerName, "Damage Effect", "Damage " + amount);
    }

    private void applyHeal(MatchPlayer target, int amount) {
        // Apply heal to dot counter: heal to target = dots move toward target
        boolean toA = target.state() == state.getPlayerA();
        if (toA) {
            state.addToDotCounter(amount); // A heals = dots go up
        } else {
            state.addToDotCounter(-amount); // B heals = dots go down (favor B)
        }
        state.getHistory().add("Heal:" + amount + " (dots: " + state.getSharedDotCounter() + ")");
    }

    private void applyDraw(MatchPlayer target, int count) {
        for (int i = 0; i < count; i++) {
            if (target.state().consumeBlockDrawIfAny()) {
                state.getHistory().add("Draw blocked");
                continue;
            }
            if (target.state().getHand().size() >= target.state().getMaxHandSize()) {
                break;
            }
            if (target.state().getDeck().isEmpty()) break;
            target.state().getHand().add(target.state().getDeck().pop());
        }
    }

    private void discardRandom(MatchPlayer target, int count) {
        for (int i = 0; i < count && !target.state().getHand().isEmpty(); i++) {
            int idx = state.getRng().nextInt(target.state().getHand().size());
            target.state().getDiscard().add(target.state().getHand().remove(idx));
        }
    }

    private void discardHand(MatchPlayer target) {
        if (target.state().getHand().isEmpty()) return;
        target.state().getDiscard().addAll(new ArrayList<>(target.state().getHand()));
        target.state().getHand().clear();
    }

    private void applyStatus(Action a, MatchPlayer self, MatchPlayer opp) {
        StatusType type = switch (String.valueOf(a.status()).toLowerCase()) {
            case "shield" -> StatusType.SHIELD;
            case "thorns" -> StatusType.THORNS;
            case "reflect_all_damage" -> StatusType.REFLECT_ALL_DAMAGE;
            case "shield_next_push_against_you" -> StatusType.SHIELD_NEXT_PUSH_AGAINST_YOU;
            case "reflect_next_push" -> StatusType.REFLECT_NEXT_PUSH;
            case "randomize_next_card_effect" -> StatusType.RANDOMIZE_NEXT_CARD_EFFECT;
            case "global_random_effects" -> StatusType.GLOBAL_RANDOM_EFFECTS;
            default -> null;
        };
        if (type == null) return;
        MatchPlayer target = targetOf(a.target(), self, opp);
        target.state().getBuffs().getStatuses().apply(type, a.amount() == null ? 0 : a.amount(), a.duration_turns() == null ? 1 : a.duration_turns());
    }

    private void revealRandom(MatchPlayer target, int count) {
        int n = Math.min(count, target.state().getHand().size());
        state.getHistory().add("Reveal " + n + " cards");
        
        // Actually reveal the cards by adding them to the revealed cards list
        for (int i = 0; i < n; i++) {
            if (!target.state().getHand().isEmpty()) {
                int randomIndex = state.getRng().nextInt(target.state().getHand().size());
                String cardId = target.state().getHand().get(randomIndex).cardId();
                
                // Add to revealed cards list based on which player is being targeted
                if (target.state() == state.getPlayerA()) {
                    state.addRevealedCardA(cardId);
                } else {
                    state.addRevealedCardB(cardId);
                }
                
                state.getHistory().add("Revealed card: " + cardId);
            }
        }
    }

    private void stealRandom(MatchPlayer thief, MatchPlayer victim, int count) {
        for (int i = 0; i < count && !victim.state().getHand().isEmpty(); i++) {
            int idx = state.getRng().nextInt(victim.state().getHand().size());
            var card = victim.state().getHand().remove(idx);
            thief.state().getHand().add(card);
        }
    }

    private void destroyRandomTableau(MatchPlayer target, int count) {
        for (int i = 0; i < count && !target.state().getTableau().isEmpty(); i++) {
            int idx = state.getRng().nextInt(target.state().getTableau().size());
            target.state().getDiscard().add(target.state().getTableau().remove(idx));
        }
    }

    private void modifyMaxHandSize(MatchPlayer target, int delta) {
        target.state().setMaxHandSize(Math.max(0, target.state().getMaxHandSize() + delta));
    }

    private void checkWinCondition(MatchPlayer self, Action a) {
        if (a.condition() != null && a.condition().copies_of_this_card_equals() != null) {
            String last = self.state().getTableau().isEmpty() ? null : self.state().getTableau().get(self.state().getTableau().size() - 1).cardId();
            if (last != null) {
                long copies = self.state().getTableau().stream().filter(c -> c.cardId().equals(last)).count();
                if (copies == a.condition().copies_of_this_card_equals()) {
                    // Mark opponent LP to 0 to end match
                    state.getInactivePlayer().setLifePoints(0);
                }
            }
        }
    }

    private void copyLast(MatchPlayer self, MatchPlayer opp, int times) {
        var actions = state.getLastActionsAppliedFor(self.state());
        if (actions == null || actions.isEmpty()) return;
        
        // Prevent infinite loops by filtering out copy_last_card_effect actions
        var filteredActions = actions.stream()
            .filter(action -> !"copy_last_card_effect".equals(action.type()))
            .toList();
        
        if (filteredActions.isEmpty()) return;
        
        for (int i = 0; i < times; i++) {
            applyActions(filteredActions, self, opp);
        }
    }

    private void stealFromTableauAndPlay(MatchPlayer thief, MatchPlayer victim, Action a) {
        if (victim.state().getTableau().isEmpty()) {
            // Handle empty tableau based on on_empty parameter
            if ("push_negative".equals(a.on_empty())) {
                int fallbackAmount = a.fallback_amount() != null ? a.fallback_amount() : -1;
                state.addToDotCounter(fallbackAmount);
                state.getHistory().add("Steal failed, fallback push: " + fallbackAmount);
            } else if ("noop".equals(a.on_empty())) {
                state.getHistory().add("Steal failed, no effect");
            }
            return;
        }
        
        // Steal a random card from opponent's tableau
        int idx = state.getRng().nextInt(victim.state().getTableau().size());
        var stolenCard = victim.state().getTableau().remove(idx);
        
        // Add to thief's tableau
        thief.state().getTableau().add(stolenCard);
        
        // Play the stolen card's effects (simplified - just log for now)
        state.getHistory().add("Stole and played: " + stolenCard.cardId());
    }

    private void conditionalPushIfHandEmpty(MatchPlayer source, MatchPlayer target, int amount) {
        if (target.state().getHand().isEmpty()) {
            state.addToDotCounter(amount);
            state.getHistory().add("Conditional push " + amount + " (opponent hand empty)");
        } else {
            state.getHistory().add("Conditional push skipped (opponent hand not empty)");
        }
    }

    private void fallbackPush(MatchPlayer source, int amount) {
        // This is a fallback when a status effect doesn't trigger
        state.addToDotCounter(amount);
        state.getHistory().add("Fallback push " + amount);
    }

    private void checkLoseConditionWhenBlocked(MatchPlayer source, Action a) {
        // This would check if a win condition was blocked and cause the player to lose
        // For now, just log it
        state.getHistory().add("Lose condition when blocked check");
    }

    private void grantExtraTurns(MatchPlayer target, int count) {
        // This would add extra turns to the target player
        // For now, just log it
        state.getHistory().add("Grant " + count + " extra turns");
    }
}


