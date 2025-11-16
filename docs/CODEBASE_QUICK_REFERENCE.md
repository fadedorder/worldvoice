# WorldVoice AI Implementation - Quick Reference

## Key Files at a Glance

### Core AI Components

| File | Lines | Purpose |
|------|-------|---------|
| `AIClient.java` | 195 | OpenRouter API client, handles HTTP requests and JSON parsing |
| `YggdrasilService.java` | 1316 | Central AI orchestrator, evaluation cycles, skill assignment decisions |
| `WishService.java` | 375 | Handles "I wish..." chat system, backlash calculations |
| `PlayerProfileSnapshot.java` | 261 | Captures player stats, derives metrics, generates profile tags |
| `SkillDictionary.java` | 261 | Loads/manages skill metadata from JSON5 dictionaries |

### Context & Configuration

| File | Purpose |
|------|---------|
| `WVConfig.java` | All configuration options (model, API key, dictionary paths, behavior settings) |
| `TensuraBridge.java` | Safe wrapper around Tensura mod capabilities (EP, magicules, aura, race, awakening) |
| `SkillService.java` | Assigns/revokes skills, checks availability and ledger state |

### Data/Decision Types

| File | Purpose |
|------|---------|
| `WishDecision.java` | Data structure for wish outcomes (grant type, narrative, actions, commands) |
| `WishActionExecutor.java` | Executes typed wish actions (EP/magicules/aura adjustments, skill grants, evolution, awakening) |

### Missing Components (TODO)

| File | Status | Purpose |
|------|--------|---------|
| `ItemDictionary.java` | ❌ Missing | Would manage item metadata like SkillDictionary |
| `MobDictionary.java` | ❌ Missing | Would manage mob metadata like SkillDictionary |
| `yggdrasil-items.json5` | ❌ Missing | Would contain item metadata |
| `yggdrasil-mobs.json5` | ❌ Missing | Would contain mob metadata |

---

## Data Flow Diagram

### Yggdrasil Skill Evaluation

```
[Advancement/Trigger] 
    ↓
[YggdrasilService.queue()]
    ↓
[YggdrasilService.evaluate()]
    ├─→ Collect eligible candidates from SkillConfigManager
    ├─→ Capture PlayerProfileSnapshot (with stats, tags)
    ├─→ Load SkillDictionary
    └─→ Build prompt with candidates + profile
    ↓
[AIClient.requestDecision()]
    ├─→ POST to OpenRouter
    ├─→ Parse JSON response
    └─→ Return DecisionResponse (skillId, rationale, deny)
    ↓
[Validate + Assign or Fallback]
    ├─→ If valid: SkillService.assign()
    └─→ If invalid: pickHeuristic() fallback
```

### Wish Evaluation

```
["I wish ..." chat message]
    ↓
[WishService.onChat()]
    ├─→ Check cooldown
    ├─→ Calculate baseline backlash (WishBacklashCalculator)
    └─→ Build WishContext
    ↓
[YggdrasilService.requestWishDecision()]
    ├─→ Extract tokens from wish text
    ├─→ Sample relevant skills/items/mobs from dictionaries
    ├─→ Build complex prompt
    └─→ POST to OpenRouter
    ↓
[Parse WishDecision JSON]
    ├─→ Extract: grant type, skill/item IDs, actions, commands
    └─→ Validate against registries
    ↓
[Apply Decision]
    ├─→ Execute grantCommands (give, effect, etc.)
    ├─→ Execute typed actions (EP, magicules, skills)
    ├─→ Apply narrative/backlash
    └─→ Log outcome
```

---

## Reference ID Format

### ResourceLocation Pattern
```
namespace:path

Examples:
minecraft:diamond          # Base Minecraft item
minecraft:zombie           # Base Minecraft mob
tensura:absolute_severance # Modded (Tensura) skill
trnightmare:camael        # Evolution/evolution mod
```

All content (items, mobs, skills) uses this format. No special handling for modded vs vanilla.

---

## Key Methods & What They Do

### AIClient
- `requestDecision(AIRequest)` → Sends skill assignment prompt → Returns `DecisionResponse`
- `requestJsonResponse(AIRequest)` → Generic JSON response from OpenRouter

### YggdrasilService
- `evaluate(server, player, entry, now)` → Main evaluation logic, calls AI or heuristic
- `buildPrompt(...)` → Creates user prompt with player profile + eligible candidates
- `buildSystemPrompt()` → Creates system prompt (Yggdrasil persona)
- `requestWishDecision(...)` → Evaluates wishes, returns `WishDecision`
- `pickHeuristic(...)` → Fallback skill selection (no AI needed)
- `pickSkillSamples(...)` → Token-matches and samples relevant skills
- `pickItemSamples(...)` → Would token-match items (currently unused)
- `pickMobSamples(...)` → Would token-match mobs (currently unused)

### PlayerProfileSnapshot
- `capture(player, advancement, extras)` → Snapshots current player state
- `playHours()` → Normalized playtime
- `killsPerHour()` → Combat intensity
- `profileTags()` → Auto-derived playstyle tags (builder, slayer, support, etc.)

### WishService
- `onChat(ServerChatEvent)` → Intercepts "I wish..." messages
- `dispatch(player, context)` → Queues async AI evaluation
- `finalize(server, context, aiDecision)` → Applies decision to player

### WishActionExecutor
- `execute(player, actions, allowSkillGrants)` → Executes all typed actions
- `handleEp()`, `handleMagicules()`, `handleAura()` → Stat adjustments
- `handleSkill()` → Grants or revokes skills
- `handleEvolution()` → Sets player race (via TensuraBridge)
- `handleAwakening()` → Sets awakening state (true_hero, true_demon_lord, blessing)

### SkillDictionary
- `load(customPath, defaultStream)` → Loads from JSON5 file
- `lookup(id)` → Get SkillMeta for ID
- `all()` → All entries
- `contains(id)` → Check if registered

---

## Configuration Key Settings

```java
// API & Model
YGG_OPENROUTER_KEY            // API key (required for AI)
YGG_OPENROUTER_MODEL          // Model: "openrouter/anthropic/claude-3.5-sonnet"
YGG_OPENROUTER_MAX_TOKENS     // 600 (controls response length)
YGG_OPENROUTER_TEMPERATURE    // 0.2 (very deterministic, not random)

// Behavior
YGG_ENABLE                    // Turn Yggdrasil on/off
YGG_INTERVAL_MINUTES          // How often to evaluate (30 min default)
YGG_PER_PLAYER_UNIQUE_CAP     // Max unique skills per player (2 default)
YGG_REQUIRE_EVENT             // Only evaluate after advancements/awakening

// Dictionaries
YGG_DICTIONARY_PATH           // Skill dictionary file path
YGG_ITEM_DICTIONARY_PATH      // Item dictionary (unused - ItemDictionary missing)
YGG_MOB_DICTIONARY_PATH       // Mob dictionary (unused - MobDictionary missing)

// Wishes
YGG_WISH_ENABLE               // Enable wish system
YGG_WISH_STRICTNESS           // 0.0 (lenient) to 1.0 (harsh) - denial probability
YGG_WISH_REQUIRE_FAVOR        // Need favor window to grant wishes
YGG_WISH_ALLOW_SKILL_GRANTS   // Can wishes grant skills?
YGG_WISH_ALLOW_ITEM_GRANTS    // Can wishes grant items?
```

---

## Dictionary File Locations

**Skill Dictionary:**
- Custom: `config/worldvoice/yggdrasil-skills.json5`
- Default: `/src/main/resources/defaultconfigs/worldvoice/yggdrasil-skills.json5`

**Item Dictionary (Planned):**
- Custom: `config/worldvoice/yggdrasil-items.json5`
- Default: `/src/main/resources/defaultconfigs/worldvoice/yggdrasil-items.json5` (missing)

**Mob Dictionary (Planned):**
- Custom: `config/worldvoice/yggdrasil-mobs.json5`
- Default: `/src/main/resources/defaultconfigs/worldvoice/yggdrasil-mobs.json5` (missing)

---

## Where Registrations Come From

### Minecraft Base Content
```java
ForgeRegistries.ITEMS.getValues()         // All items
ForgeRegistries.BLOCKS.getValues()        // All blocks
ForgeRegistries.ENTITY_TYPES.getValues()  // All entity types
```

### Skill Content
```java
SkillAPI.getSkillRegistry().getValue(id)  // From ManasCore/Tensura
SkillAPI.getSkillsFrom(player)            // Player's learned skills
```

### Tensura-Specific
```java
TensuraBridge.getEp(player)               // Essence Points
TensuraBridge.getMagicules(player)        // Magicule count
TensuraBridge.getRaceId(player)           // Race (ResourceLocation)
```

---

## Validation Pipeline

When AI proposes something:

1. **Parse ID:** `ResourceLocation.tryParse(idString)` → null if invalid format
2. **Check Candidates:** Is it in the eligible list we presented?
3. **Check Registry:** Does it exist in Minecraft/mod registries?
4. **Check Availability:** Is there an open slot? (for skills)
5. **Fallback:** If any check fails, use heuristic selection instead

This ensures AI cannot:
- Invent fake mod IDs
- Reference removed/disabled content
- Violate slot capacity limits
- Execute unsafe commands

---

## Key Insights

### Token Budget
- System prompt + user prompt + metadata = ~300-400 tokens typical
- Max response: 600 tokens
- This is why sampling is used (can't show entire registry)

### Sampling Strategy
1. Extract keywords from player request
2. Match against dictionary entries (name, path, tags)
3. Fill up to limit with matching entries
4. If still space, add random entries
5. Limits: Skills (10), Items (14), Mobs (10)

### No Modded vs Vanilla Distinction
- All content treated identically
- Dictionary can track `mod` field if needed
- AI sees formatted lists, doesn't care about namespace

### Fallback Strategy
- If API key missing → Use heuristic scoring
- If API fails 3+ times → Circuit break for 5 minutes → Use heuristic
- If AI proposes invalid skill → Use heuristic
- Heuristic uses normalized metrics + dictionary tier info

