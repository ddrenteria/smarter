package com.officeduel.service;

import com.officeduel.engine.cards.CardDefinitionSet;
import com.officeduel.engine.core.DeterministicRng;
import com.officeduel.engine.engine.CardIndex;
import com.officeduel.engine.engine.TurnEngine;
import com.officeduel.engine.loader.CardDefinitionLoader;
import com.officeduel.engine.model.Cards;
import com.officeduel.engine.model.GameState;
import com.officeduel.engine.model.PlayerState;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MatchRegistry {
    private final Map<String, Entry> matches = new ConcurrentHashMap<>();
    private final CardDefinitionSet defs;
    private final CardIndex index;

    public record Entry(GameState state, TurnEngine engine, String playerA, String playerB, boolean readyA, boolean readyB, boolean started, boolean playerAIsBot, boolean playerBIsBot) {}

    public MatchRegistry() throws Exception {
        Path cardsPath = Path.of("gameplay cards definition.txt");
        this.defs = CardDefinitionLoader.load(cardsPath);
        this.index = new CardIndex(defs);
    }

    public String createMatch(long seed) {
        DeterministicRng rng = new DeterministicRng(seed);
        GameState gs = new GameState(rng, defs.rulesAssumptions().max_lp());
        for (int i = 0; i < 50; i++) {
            int idxA = rng.nextInt(defs.cards().size());
            int idxB = rng.nextInt(defs.cards().size());
            gs.getPlayerA().getDeck().addFirst(new Cards(defs.cards().get(idxA).id()));
            gs.getPlayerB().getDeck().addFirst(new Cards(defs.cards().get(idxB).id()));
        }
        // Don't create engine or start match yet - wait for both players to be ready
        String id = UUID.randomUUID().toString();
        matches.put(id, new Entry(gs, null, null, null, false, false, false, false, false));
        return id;
    }

    public Entry get(String id) { return matches.get(id); }
    public CardIndex getCardIndex() { return index; }
    
    public synchronized int joinMatch(String matchId, String playerName) {
        Entry entry = matches.get(matchId);
        if (entry == null) return -1; // Match not found
        
        if (entry.playerA() == null) {
            // First player joins
            matches.put(matchId, new Entry(entry.state(), entry.engine(), playerName, entry.playerB(), false, entry.readyB(), entry.started(), entry.playerAIsBot(), entry.playerBIsBot()));
            return 0; // Player A
        } else if (entry.playerB() == null) {
            // Second player joins
            matches.put(matchId, new Entry(entry.state(), entry.engine(), entry.playerA(), playerName, entry.readyA(), false, entry.started(), entry.playerAIsBot(), entry.playerBIsBot()));
            return 1; // Player B
        } else {
            return -2; // Match full
        }
    }
    
    public synchronized boolean setReady(String matchId, int seat) {
        Entry entry = matches.get(matchId);
        if (entry == null) return false;
        
        boolean newReadyA = seat == 0 ? true : entry.readyA();
        boolean newReadyB = seat == 1 ? true : entry.readyB();
        
        // If both ready, start the match
        boolean newStarted = entry.started();
        TurnEngine newEngine = entry.engine();
        
        if (newReadyA && newReadyB && !entry.started()) {
            newEngine = new TurnEngine(entry.state(), index);
            newEngine.startMatch();
            newStarted = true;
        }
        
        matches.put(matchId, new Entry(entry.state(), newEngine, entry.playerA(), entry.playerB(), newReadyA, newReadyB, newStarted, entry.playerAIsBot(), entry.playerBIsBot()));
        return true;
    }
    
    public String createMatchWithBot(long seed) {
        DeterministicRng rng = new DeterministicRng(seed);
        GameState gs = new GameState(rng, defs.rulesAssumptions().max_lp());
        for (int i = 0; i < 50; i++) {
            int idxA = rng.nextInt(defs.cards().size());
            int idxB = rng.nextInt(defs.cards().size());
            gs.getPlayerA().getDeck().addFirst(new Cards(defs.cards().get(idxA).id()));
            gs.getPlayerB().getDeck().addFirst(new Cards(defs.cards().get(idxB).id()));
        }
        
        String id = UUID.randomUUID().toString();
        // Create match with Player A as bot, Player B as human
        // Bot is ready, but match won't start until human player joins and is ready
        matches.put(id, new Entry(gs, null, "Bot", null, true, false, false, true, false));
        return id;
    }
    
    public synchronized void addBotToMatch(String matchId) {
        Entry entry = matches.get(matchId);
        if (entry == null || entry.started()) return;
        
        if (entry.playerA() == null) {
            // Add bot as Player A
            matches.put(matchId, new Entry(entry.state(), entry.engine(), "Bot", entry.playerB(), true, entry.readyB(), entry.started(), true, entry.playerBIsBot()));
        } else if (entry.playerB() == null) {
            // Add bot as Player B
            matches.put(matchId, new Entry(entry.state(), entry.engine(), entry.playerA(), "Bot", entry.readyA(), true, entry.started(), entry.playerAIsBot(), true));
        }
    }
    
    public synchronized void autoPlayBotTurn(String matchId) {
        Entry entry = matches.get(matchId);
        if (entry == null || entry.engine() == null || !entry.started()) {
            System.out.println("Bot auto-play skipped: entry=" + (entry != null) + ", engine=" + (entry != null && entry.engine() != null) + ", started=" + (entry != null && entry.started()));
            return;
        }
        
        TurnEngine engine = entry.engine();
        GameState gs = entry.state();
        
        // Check if it's a bot's turn
        boolean isPlayerABot = entry.playerAIsBot();
        boolean isPlayerBBot = entry.playerBIsBot();
        int activePlayer = gs.getActivePlayerIndex();
        int inactivePlayer = 1 - activePlayer;
        
        System.out.println("Bot auto-play check: activePlayer=" + activePlayer + ", isPlayerABot=" + isPlayerABot + ", isPlayerBBot=" + isPlayerBBot + ", phase=" + gs.getPhase());
        
        if (gs.getPhase() == com.officeduel.engine.model.GameState.Phase.PLAY_TWO_CARDS) {
            // Submit phase: only active player bot acts
            if ((activePlayer == 0 && isPlayerABot) || (activePlayer == 1 && isPlayerBBot)) {
                System.out.println("Bot (active) selecting two cards");
                autoSelectCards(engine, gs);
            }
        } else if (gs.getPhase() == com.officeduel.engine.model.GameState.Phase.OPPONENT_PICK) {
            // Choice phase: only inactive player bot acts
            if ((inactivePlayer == 0 && isPlayerABot) || (inactivePlayer == 1 && isPlayerBBot)) {
                System.out.println("Bot (inactive) choosing between cards");
                autoChooseCard(engine, gs);
            }
        }
    }
    
    private void autoSelectCards(TurnEngine engine, GameState gs) {
        PlayerState active = gs.getActivePlayer();
        
        if (active.isSkipNextTurn()) {
            active.setSkipNextTurn(false);
            // Use playTurnAuto() to handle the skip turn properly
            engine.playTurnAuto();
            return;
        }
        
        // Auto: pick two random distinct cards from hand if available
        int h = active.getHand().size();
        if (h == 0) { 
            // Use playTurnAuto() to handle empty hand properly
            engine.playTurnAuto();
            return; 
        }
        
        int idxUp = gs.getRng().nextInt(h);
        String faceUpId = active.getHand().get(idxUp).cardId();
        int idxDown = h > 1 ? (idxUp + 1 + gs.getRng().nextInt(h - 1)) % h : idxUp;
        String faceDownId = active.getHand().get(idxDown).cardId();
        
        // Set the cards and change phase to OPPONENT_PICK
        gs.setFaceUpCardId(faceUpId);
        gs.setFaceDownCardId(faceDownId);
        gs.setPhase(com.officeduel.engine.model.GameState.Phase.OPPONENT_PICK);
        
        System.out.println("Bot selected cards: " + faceUpId + " (up), " + faceDownId + " (down)");
    }
    
    private void autoChooseCard(TurnEngine engine, GameState gs) {
        // Bot randomly chooses between face up and face down
        boolean chooseFaceUp = gs.getRng().nextBoolean();
        String picked = chooseFaceUp ? gs.getFaceUpCardId() : gs.getFaceDownCardId();
        String remaining = chooseFaceUp ? gs.getFaceDownCardId() : gs.getFaceUpCardId();
        
        System.out.println("Bot chose: " + (chooseFaceUp ? "face up" : "face down") + " (" + picked + ")");
        
        // Use the existing choice method
        engine.playTurnWithChoice(picked, remaining);
    }
}


