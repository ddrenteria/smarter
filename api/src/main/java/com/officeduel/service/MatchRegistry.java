package com.officeduel.service;

import com.officeduel.engine.cards.CardDefinitionSet;
import com.officeduel.engine.core.DeterministicRng;
import com.officeduel.engine.engine.CardIndex;
import com.officeduel.engine.engine.TurnEngine;
import com.officeduel.engine.loader.CardDefinitionLoader;
import com.officeduel.engine.model.Cards;
import com.officeduel.engine.model.GameState;
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

    public record Entry(GameState state, TurnEngine engine, String playerA, String playerB, 
                       String tokenA, String tokenB, boolean readyA, boolean readyB, boolean started, 
                       boolean playerAIsBot, boolean playerBIsBot, int playTwoCardsTimeSeconds, int opponentPickTimeSeconds, int winPointsToReach) {}

    public MatchRegistry() throws Exception {
        Path cardsPath = Path.of("gameplay cards definition.txt");
        this.defs = CardDefinitionLoader.load(cardsPath);
        this.index = new CardIndex(defs);
    }

    public String createMatch(long seed) {
        DeterministicRng rng = new DeterministicRng(seed);
        GameState gs = new GameState(rng);
        for (int i = 0; i < 50; i++) {
            int idxA = rng.nextInt(defs.cards().size());
            int idxB = rng.nextInt(defs.cards().size());
            gs.getPlayerA().getDeck().addFirst(new Cards(defs.cards().get(idxA).id()));
            gs.getPlayerB().getDeck().addFirst(new Cards(defs.cards().get(idxB).id()));
        }
        // Don't create engine or start match yet - wait for both players to be ready
        String id = UUID.randomUUID().toString();
        matches.put(id, new Entry(gs, null, null, null, null, null, false, false, false, false, false, 30, 15, 5));
        return id;
    }

    public Entry get(String id) { return matches.get(id); }
    public CardIndex getCardIndex() { return index; }
    
    public synchronized JoinResult joinMatch(String matchId, String playerName) {
        Entry entry = matches.get(matchId);
        if (entry == null) return new JoinResult(-1, null); // Match not found
        
        if (entry.playerA() == null) {
            // First player joins
            String token = UUID.randomUUID().toString();
            matches.put(matchId, new Entry(entry.state(), entry.engine(), playerName, entry.playerB(), 
                                         token, entry.tokenB(), false, entry.readyB(), entry.started(), 
                                         entry.playerAIsBot(), entry.playerBIsBot(), entry.playTwoCardsTimeSeconds(), entry.opponentPickTimeSeconds(), entry.winPointsToReach()));
            return new JoinResult(0, token); // Player A
        } else if (entry.playerB() == null) {
            // Second player joins
            String token = UUID.randomUUID().toString();
            matches.put(matchId, new Entry(entry.state(), entry.engine(), entry.playerA(), playerName, 
                                         entry.tokenA(), token, entry.readyA(), false, entry.started(), 
                                         entry.playerAIsBot(), entry.playerBIsBot(), entry.playTwoCardsTimeSeconds(), entry.opponentPickTimeSeconds(), entry.winPointsToReach()));
            return new JoinResult(1, token); // Player B
        } else {
            return new JoinResult(-2, null); // Match full
        }
    }
    
    public record JoinResult(int seat, String token) {}
    
    /**
     * Get player seat from token. Returns -1 if token is invalid.
     */
    public int getPlayerSeat(String matchId, String token) {
        Entry entry = matches.get(matchId);
        if (entry == null || token == null) return -1;
        
        if (token.equals(entry.tokenA())) return 0;
        if (token.equals(entry.tokenB())) return 1;
        return -1;
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
            // Configure win points before starting the match
            entry.state().setWinPointsToReach(entry.winPointsToReach());
            newEngine = new TurnEngine(entry.state(), index);
            newEngine.setPlayerNames(entry.playerA(), entry.playerB());
            newEngine.startMatch();
            newStarted = true;
        }
        
        matches.put(matchId, new Entry(entry.state(), newEngine, entry.playerA(), entry.playerB(), 
                                     entry.tokenA(), entry.tokenB(), newReadyA, newReadyB, newStarted, 
                                     entry.playerAIsBot(), entry.playerBIsBot(), entry.playTwoCardsTimeSeconds(), entry.opponentPickTimeSeconds(), entry.winPointsToReach()));
        return true;
    }
    
    public String createMatchWithBot(long seed) {
        DeterministicRng rng = new DeterministicRng(seed);
        GameState gs = new GameState(rng);
        for (int i = 0; i < 50; i++) {
            int idxA = rng.nextInt(defs.cards().size());
            int idxB = rng.nextInt(defs.cards().size());
            gs.getPlayerA().getDeck().addFirst(new Cards(defs.cards().get(idxA).id()));
            gs.getPlayerB().getDeck().addFirst(new Cards(defs.cards().get(idxB).id()));
        }
        
        String id = UUID.randomUUID().toString();
        // Create match with Player A as bot, Player B as human
        // Bot is ready, but match won't start until human player joins and is ready
        matches.put(id, new Entry(gs, null, "Bot", null, "bot-token", null, true, false, false, true, false, 30, 15, 5));
        return id;
    }
    
    public synchronized void addBotToMatch(String matchId) {
        Entry entry = matches.get(matchId);
        if (entry == null || entry.started()) return;
        
        if (entry.playerA() == null) {
            // Add bot as Player A
            matches.put(matchId, new Entry(entry.state(), entry.engine(), "Bot", entry.playerB(), 
                                         "bot-token", entry.tokenB(), true, entry.readyB(), entry.started(), 
                                         true, entry.playerBIsBot(), entry.playTwoCardsTimeSeconds(), entry.opponentPickTimeSeconds(), entry.winPointsToReach()));
        } else if (entry.playerB() == null) {
            // Add bot as Player B
            matches.put(matchId, new Entry(entry.state(), entry.engine(), entry.playerA(), "Bot", 
                                         entry.tokenA(), "bot-token", entry.readyA(), true, entry.started(), 
                                         entry.playerAIsBot(), true, entry.playTwoCardsTimeSeconds(), entry.opponentPickTimeSeconds(), entry.winPointsToReach()));
        }
    }
    
    public synchronized boolean updateGameSettings(String matchId, int playTwoCardsTimeSeconds, int opponentPickTimeSeconds, int winPointsToReach) {
        Entry entry = matches.get(matchId);
        if (entry == null || entry.started()) return false; // Can't change settings after match starts
        
        matches.put(matchId, new Entry(entry.state(), entry.engine(), entry.playerA(), entry.playerB(), 
                                     entry.tokenA(), entry.tokenB(), entry.readyA(), entry.readyB(), entry.started(), 
                                     entry.playerAIsBot(), entry.playerBIsBot(), playTwoCardsTimeSeconds, opponentPickTimeSeconds, winPointsToReach));
        return true;
    }
    
    // Backward compatibility method
    public synchronized boolean updateTimerSettings(String matchId, int playTwoCardsTimeSeconds, int opponentPickTimeSeconds) {
        return updateGameSettings(matchId, playTwoCardsTimeSeconds, opponentPickTimeSeconds, 5); // Default to 5 points
    }
    
    
}


