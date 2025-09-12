Office Duel Engine (POC)

Run
- Single match: `./gradlew run`
- Seeded N matches: `./gradlew run --args="123 1000"`
- Tests: `./gradlew test`

Files
- `gameplay cards definition.txt`: card set JSON
- `AIImplementationPlan.txt`: plan

Notes
- Deterministic RNG per match; all random ops derive from seed
- Tie rule: if both LP ≤ 0 at end step, active player wins

# smarter
