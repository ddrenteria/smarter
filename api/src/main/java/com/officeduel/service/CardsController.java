package com.officeduel.service;

import com.officeduel.engine.cards.CardDefinitionSet;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cards")
public class CardsController {
    private final MatchRegistry registry;

    public CardsController(MatchRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> list() {
        var defs = registry;
        // reuse registry's loaded defs via reflection to avoid storage duplication
        try {
            var f = MatchRegistry.class.getDeclaredField("defs");
            f.setAccessible(true);
            CardDefinitionSet set = (CardDefinitionSet) f.get(registry);
            var map = set.cards().stream().collect(Collectors.toMap(CardDefinitionSet.CardDef::id, CardDefinitionSet.CardDef::name));
            return ResponseEntity.ok(map);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}


