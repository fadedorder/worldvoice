# WorldVoice AI Implementation Analysis

## Overview
WorldVoice uses an "Yggdrasil" system - an AI orchestrator that assigns unique skills to players and handles player wishes. The system integrates with OpenRouter AI (via Claude) and Tensura mod capabilities.

---

## 1. How the AI Handles Item/Mob/Skill References

### A. Skill References

**Format:** ResourceLocation format - `namespace:path`
- Example: `tensura:absolute_severance` (modded) or `minecraft:diamond` (base content)

**Handling in Code:**
- Skills are referenced as `ResourceLocation` objects throughout
- Parsed using `ResourceLocation.tryParse(rawSkill)` 
- Example from `AIClient.java` (line 159):
  ```java
  ResourceLocation skillId = ResourceLocation.tryParse(rawSkill.trim());
  ```

**Skill Dictionary System** (`SkillDictionary.java`):
- Maintains a map of `ResourceLocation -> SkillMeta`
- Stores rich metadata:
  - `id`: ResourceLocation
  - `name`: Human-readable name
  - `tier`: 1-5 scale
  - `tags`: Array of descriptive tags (e.g., "Offensive", "Severance", "Haki")
  - `notes`: Lore/constraint text
  - `evolution`: Optional evolution path (ResourceLocation)
  - `combines`: List of skills that can be combined

**Dictionary Loading:**
- Loads from JSON5 file: `config/worldvoice/yggdrasil-skills.json5`
- Falls back to embedded `/defaultconfigs/worldvoice/yggdrasil-skills.json5`
- Auto-reloads if stale (every 5 minutes)

### B. Item and Mob References (CURRENTLY NOT IMPLEMENTED)

**Status:** ItemDictionary and MobDictionary are REFERENCED in `YggdrasilService.java` but **DO NOT EXIST YET**

- `itemDictionary()` called at lines 589, 1190, etc.
- `mobDictionary()` called at lines 590, 1228, etc.
- Classes would follow same pattern as `SkillDictionary`

**Planned Format (from AI Expansion Plan):**

Item references:
```json5
{
  "items": [
    {
      "id": "modid:item_name",
      "name": "Pretty Name",
      "mod": "modid",
      "role": "weapon|armor|tool|food|utility|material|quest|other",
      "rarity": "common|uncommon|rare|epic|legendary|mythic",
      "tags": ["sharp", "fire", "healing"],
      "notes": "Lore or constraints"
    }
  ]
}
```

Mob references:
```json5
{
  "mobs": [
    {
      "id": "modid:mob_name",
      "name": "Pretty Name",
      "mod": "modid",
      "category": "monster|creature|ambient|water_creature|boss|npc",
      "difficulty": 1.0,  // 0.0-10.0
      "tags": ["undead", "flying", "fire_immune"],
      "notes": "Lore/behavior hints"
    }
  ]
}
```

---

## 2. Where and How Minecraft Registrations Are Accessed

### A. Primary Access Point: ForgeRegistries

Location: `net.minecraftforge.registries.ForgeRegistries`

**Used in:**
- `PlayerProfileSnapshot.java`: Lines 202-243
- `WishService.java`: Lines 15, 218
- Other services

**Access Patterns:**

1. **Get all values of a type:**
   ```java
   for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES.getValues()) { ... }
   for (Block block : ForgeRegistries.BLOCKS.getValues()) { ... }
   for (Item item : ForgeRegistries.ITEMS.getValues()) { ... }
   ```

2. **Get specific entry by ID:**
   ```java
   var item = ForgeRegistries.ITEMS.getValue(resourceLocation);
   EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(bossId);
   ```

### B. Skill Registration Access

**Via ManasCore SkillAPI:**
```java
ManasSkill skill = SkillAPI.getSkillRegistry().getValue(skillId);
```
- See: `SkillService.java` lines 44, 78

**Storage Access:**
```java
SkillStorage storage = SkillAPI.getSkillsFrom(player);
```

### C. Tensura-Specific Access

**Via TensuraBridge:**
- Wraps direct Tensura capability access
- Safe null-checked access to Tensura data
- Example operations:
  - `TensuraBridge.getEp(player)` - Get Essence Points
  - `TensuraBridge.getRaceId(player)` - Get player's current race
  - `TensuraBridge.isTrueHero(player)` - Check awakening status

---

## 3. Configuration and Context Provided to the AI

### A. Configuration Sources

**Main Config File:** `WVConfig.java`
- Path: Minecraft's Forge config system
- Managed by: `ForgeConfigSpec`

**Key AI Settings:**

```java
// Model/API Configuration
YGG_OPENROUTER_MODEL          // "openrouter/anthropic/claude-3.5-sonnet"
YGG_OPENROUTER_KEY            // API key (can be empty)
YGG_OPENROUTER_MAX_TOKENS     // 600 tokens
YGG_OPENROUTER_TEMPERATURE    // 0.2 (very deterministic)
YGG_OPENROUTER_REFERER        // Optional HTTP header
YGG_OPENROUTER_TITLE          // Optional HTTP header

// Dictionaries
YGG_DICTIONARY_PATH           // config/worldvoice/yggdrasil-skills.json5
YGG_ITEM_DICTIONARY_PATH      // config/worldvoice/yggdrasil-items.json5 (unused)
YGG_MOB_DICTIONARY_PATH       // config/worldvoice/yggdrasil-mobs.json5 (unused)

// Behavior
YGG_ENABLE                    // Master on/off
YGG_INTERVAL_MINUTES          // Evaluation frequency (30 min default)
YGG_PER_PLAYER_UNIQUE_CAP     // Skill cap per player (2 default)
YGG_REQUIRE_EVENT             // Require advancement/awakening (true)
YGG_LOG_DECISIONS             // Log all AI decisions

// Wish System
YGG_WISH_ENABLE               // "I wish..." chat feature
YGG_WISH_STRICTNESS           // 0.0-1.0 denial probability (0.85)
YGG_WISH_REQUIRE_FAVOR        // Need active favor to grant (true)
```

### B. Player Profile Context: PlayerProfileSnapshot

**Captured Data:**

```java
record PlayerProfileSnapshot(
  String name,                    // Player name
  int experienceLevel,            // XP level (0-∞)
  long playTicks,                 // Total play time in ticks
  long distanceTravelled,         // Total distance in cm
  int totalDeaths,                // Death count
  boolean hasTeam,                // Is on a team
  ResourceLocation lastAdvancement, // Last advancement earned
  
  // Tensura Integration
  double ep,                      // Essence Points
  double magicules,               // Magicule count
  double aura,                    // Aura amount
  String raceId,                  // Current race
  boolean trueHero,               // Awakening status
  boolean trueDemonLord,          // Awakening status
  
  // Statistics
  int recentAdvancements,         // Count in last hour
  long lastEvaluationMs,          // Timestamp of last AI eval
  int uniqueOwned,                // Unique skills owned
  long mobsKilled,                // Total mob kills
  long bossKills,                 // Boss-only kills
  long damageDealt,               // Total damage dealt
  long damageTaken,               // Total damage taken
  long blocksMined,               // Total blocks mined
  long blocksPlaced,              // Total blocks placed
  long itemsCrafted,              // Total items crafted
  long itemsUsed                  // Total items used
)
```

**Derived Metrics:**
- `playHours()` - Normalized playtime
- `killsPerHour()` - Combat intensity
- `deathsPerHour()` - Vulnerability
- `blocksPlacedPerHour()` - Building intensity
- `craftsPerHour()` - Crafting activity
- `damageTakenPerHour()` - Risk exposure
- `profileTags()` - Playstyle classification

**Profile Tags (Auto-Derived):**
```
"builder"      - blocksPlacedPerHour >= 160
"slayer"       - killsPerHour >= 15 OR bossKills >= 5
"combatant"    - killsPerHour >= 5
"wanderer"     - distanceKmPerHour >= 2.0
"conqueror"    - bossKillsPerHour > 0.2
"artisan"      - craftsPerHour >= 45
"support"      - itemsUsedPerHour >= 80
"survivor"     - deathsPerHour <= 0.1 AND damageTakenPerHour < 80
"reckless"     - deathsPerHour >= 0.6
"ally"         - hasTeam == true
"balanced"     - default (no tags)
```

### C. Yggdrasil Evaluation Prompt Structure

**System Prompt:** (From `buildSystemPrompt()`)
```
You are Yggdrasil, the Voice of the World from Tensura.
Speak as an ancient, impartial oracle that weighs destiny, deeds, and balance.
Narratives must be short (<=2 sentences) and ethereal.
When judging a player, choose at most one unique skill from the candidates or deny them.
Respond in JSON: {skillId, rationale, deny, denyReason}
```

**User Prompt Content:** (From `buildPrompt()`)
1. Player Name
2. Stats: level, deaths, play ticks, distance, team status
3. Profile Metrics:
   - playHours
   - killsPerHour / deathsPerHour
   - blocksPlacedPerHour / craftsPerHour
   - damageTakenPerHour
4. ProfileTags=[...]
5. Recent Advancements
6. Essence Snapshot: EP, magicules, aura, race, hero/demon status
7. Trigger Type (ADVANCEMENT, MANUAL, PASSIVE, etc.)
8. Favor Multiplier (0-10)
9. Last Advancement ID
10. **Eligible Skills List:**
    - Format: `id` (namespace:path)
    - With capacity info: `slots(occupied/total)`
    - Enriched with dictionary metadata:
      - name, tier, evolution, tags, notes

### D. Wish Evaluation Prompt Structure

**System Prompt:**
```
You are Yggdrasil, the Voice of the World from Tensura.
Judge the wish with divine neutrality. Consider the player's history, 
favor, strictness, and intensity.
Narratives must be short (<=2 sentences) and ethereal.
Output JSON ONLY with: grant, grantType, skillId, itemId, itemCount, 
boonSeconds, backlash, narrative, backlashNarrative, grantCommands, 
penaltyCommands, actions
```

**User Prompt Content:**
1. Player stats (compact version)
2. PlayHours, killsPerHour, craftsPerHour, etc.
3. EP/magicules/aura/race/awakening status
4. favorScore, strictness, intensity
5. hasFavor, requiresFavor, allowSkills, allowItems
6. Wish text
7. ProfileTags
8. **SampleUniqueSkills** - Up to 10 relevant skills from dictionary
9. **SampleItems** - Up to 14 relevant items (if items allowed)
10. **SampleMobs** - Up to 10 relevant mobs from dictionary
11. Allowed commands (give, effect, worldvoice, xp, experience, playsound, attribute, data modify entity, title)
12. Typed Actions reference

---

## 4. How Modded Content vs Base Minecraft Is Referenced

### A. ResourceLocation Format

**Universal Format:** All content uses `namespace:path`

**Base Minecraft:**
- Namespace: `minecraft`
- Path: lowercase snake_case
- Examples:
  - `minecraft:diamond`
  - `minecraft:zombie`
  - `minecraft:ender_dragon`

**Modded Content:**
- Namespace: mod identifier
- Path: defined by mod
- Examples:
  - `tensura:absolute_severance` (Tensura mod skill)
  - `trnightmare:camael` (evolution from Tensura mod)
  - `virtuoso:root` (another mod)

### B. Namespace Handling in Code

**No Special Treatment for Base vs Modded:**
- All content treated identically via ResourceLocation
- `ForgeRegistries` includes both vanilla and modded entries
- Dictionary system agnostic to namespace

**Example from SkillDictionary:**
```java
ResourceLocation id = ResourceLocation.tryParse(rawId);
// Works for both "minecraft:diamond" and "tensura:skill_name"
entries.put(id, meta);
```

### C. Filtering by Mod in Dictionaries

**Dictionary Awareness:**
- ItemDictionary planned to include `mod` field (namespace extraction)
- MobDictionary planned to include `mod` field
- Allows future filtering: "show only modded items" or "show only vanilla"

**Example (from plan):**
```json5
{
  "id": "tensura:spatial_blade",  // modded
  "name": "Spatial Blade",
  "mod": "tensura",
  "role": "weapon"
}
```

### D. AI Sampling Strategy

**Token Budget Awareness:**
- Cannot feed entire registry to AI (too many tokens)
- Uses sampling/filtering approach

**Sampling Logic (lines 1153-1252):**

1. **Extract tokens from player's request** (wishes only)
   - Parse wish text for keywords
   - Filter out <3 char tokens

2. **Token-Based Matching:**
   ```java
   for (String token : tokens) {
     for (SkillMeta meta : dict.all()) {
       if (matchesSkill(meta, token)) {
         // Add to sample
       }
     }
   }
   ```

3. **Matching Criteria:**
   - Token in skill path: `absolute_severance` matches `absolute`
   - Token in skill name: "Absolute Severance" matches `absolute`
   - Token in tags: ["Offensive", "Severance"] match `offensive`

4. **Fallback if needed:**
   - If token matching insufficient, add random entries up to limit
   - Limits: Skills (10), Items (14), Mobs (10)

5. **Sample Format in Prompt:**
   ```
   SampleUniqueSkills: 
   tensura:absolute_severance|Absolute Severance|tier=3|tags=[Offensive,Severance]
   ```

### E. Registration Validation

**AI Output Validation:**

1. **Parse JSON from AI response:**
   ```java
   ResourceLocation skillId = ResourceLocation.tryParse(json.get("skillId").getAsString());
   ```

2. **Check against candidates:**
   ```java
   if (!containsCandidate(candidates, decisionId)) {
     LOGGER.debug("AI suggested {}, which is not eligible; falling back.", decisionId);
     decisionId = pickHeuristic(...);
   }
   ```

3. **Check registry before execution:**
   ```java
   var item = ForgeRegistries.ITEMS.getValue(id);
   if (item == null) return false;
   ```

4. **Command validation:**
   - Only safe commands allowed (give, effect, xp, etc.)
   - Uses `%player%` placeholder, no arbitrary injection

---

## 5. How References Are Provided to the AI

### A. Skill References in Prompts

**Yggdrasil Evaluation:**
- Full candidate list with metadata
- Example prompt content:
  ```
  Eligible skills:
  - tensura:absolute_severance slots(0/2) name=Absolute Severance tier=3 
    evolution=trnightmare:camael tags=[Offensive,Severance,Ranged,Haki] 
    note=Coat strikes with severance...
  - tensura:anti_skill slots(1/1) name=Anti-Skill tier=4 
    tags=[Counter,Nullification,Defense]
  ```

**Wish Evaluation:**
- Curated samples only (up to 10 skills)
- Format: `id|name|tier=X|tags=[...]`
- Token-matched or randomly sampled

### B. Item References (Currently Missing)

**Will be similar to skills:**
- Dictionary provides: id, name, role, rarity, tags, notes
- Wishes will get samples: up to 14 items
- Sampling by token-match from wish text

### C. Mob References (Currently Missing)

**Will be similar to items:**
- Dictionary provides: id, name, category, difficulty, tags, notes
- Wishes will get samples: up to 10 mobs
- Future use: wish outcomes can summon or target mobs

---

## 6. Key Implementation Details

### A. AI Request Structure (AIClient.java)

```java
record AIRequest(
  String model,           // Model name
  String apiKey,          // OpenRouter key
  int maxTokens,          // 600 default
  double temperature,     // 0.2 (low randomness)
  String systemPrompt,    // Behavior instructions
  String userPrompt,      // Context + question
  String referer,         // Optional HTTP header
  String title            // Optional HTTP header
)
```

### B. Decision Response Parsing

```java
record DecisionResponse(
  ResourceLocation skillId,  // Chosen skill
  String rationale,          // Why this skill
  boolean deny,              // Grant or deny
  String denyReason          // If denied, why
)
```

### C. Wish Decision Response (More Complex)

```java
{
  "grant": true|false,
  "grantType": "skill|item|boon|deny",
  "skillId": "namespace:path",
  "itemId": "namespace:path",
  "itemCount": 1,
  "boonSeconds": 60,
  "backlash": 0..1,
  "narrative": "prose",
  "backlashNarrative": "consequence",
  "grantCommands": ["give %player% minecraft:diamond 1", ...],
  "penaltyCommands": ["effect give %player% minecraft:weakness 60 1", ...],
  "actions": [
    {
      "type": "ep|magicules|aura|skill|evolution|awakening",
      "op": "add|set|grant|revoke|form|toggle",
      "value": number,
      "skillId": "namespace:path",  // for skill/evolution
      "payload": "context"  // for awakening
    }
  ]
}
```

---

## 7. Gaps and TODO Items

**Missing Components:**
1. ❌ `ItemDictionary.java` - Not implemented
2. ❌ `MobDictionary.java` - Not implemented
3. ❌ Default dictionary files for items/mobs - Only skills.json5 exists
4. ❌ Registration commands - Planned but not implemented
5. ❌ Item/Mob sampling in wishes - Calls methods that don't exist

**These are referenced but return empty/null:**
- `ensureItemDictionary()` (line 107)
- `ensureMobDictionary()` (line 114)
- `formatItemSamples()` (line 1281)
- `formatMobSamples()` (line 1298)

---

## Summary

The AI implementation uses a **dictionary-driven approach** where:

1. **ResourceLocation** format (`namespace:path`) represents all content
2. **Dictionaries** provide rich metadata from JSON5 files
3. **ForgeRegistries** gives access to actual Minecraft registrations
4. **TensuraBridge** provides Tensura-specific capability access
5. **Token sampling** keeps prompts concise while covering relevant content
6. **Validation** ensures AI can only propose registered, eligible content
7. **Modded vs vanilla** handled transparently via namespace lookup

The system is **mostly complete for skills**, but **ItemDictionary and MobDictionary remain unimplemented**.

