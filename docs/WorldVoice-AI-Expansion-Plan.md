## WorldVoice AI Expansion Plan

### 1. Core Goals

- **Richer, fairer player profiling**  
  Move from raw playtime/distance metrics toward normalized, playstyle-oriented profiles that the AI can use without favoring no-lifers or AFK behavior.

- **Deep modpack awareness**  
  Give the AI structured knowledge of skills, items, and mobs from *any* mod list (not just `minecraft:` or Tensura), while keeping prompts concise and safe.

- **Configurable, data-driven AI behavior**  
  Represent world content and AI-relevant metadata in JSON5 “dictionaries” that can be regenerated per-modlist and extended by hand, without code changes.

- **Future quest integration**  
  Ensure dictionary formats and profiling abstractions are generic enough that the quest system can consume them later (e.g., for AI-authored quests, rewards, or objective selection) without breaking changes.

---

### 2. High-Level Features

1. **Expanded Player Profile Snapshot**
   - Extend `PlayerProfileSnapshot` to include:
     - Combat metrics: mobs killed, boss/elite kills, damage dealt/taken.
     - Progression metrics: advancements earned, unique skills owned (already present), time since last evaluation.
     - Activity metrics: blocks mined/placed, items crafted/used, deaths, playtime in dimensions (Overworld/Nether/End).
   - Derive *normalized* values and *tags* (e.g., `builder`, `explorer`, `slayer`, `support`) that summarize playstyle rather than raw magnitude.

2. **Bias-Resistant Scoring and Prompts**
   - Update heuristic scoring in `YggdrasilService` to:
     - Downweight or remove direct dependence on total playtime and travel distance.
     - Use capped, normalized metrics (e.g., play hours, kills per hour, deaths per hour).
     - Incorporate derived tags to express playstyle in a compact way.
   - Enhance AI prompts (Yggdrasil evaluation and wishes) with high-level descriptors instead of only raw stats.

3. **Skill Dictionary Enhancements**
   - Keep the existing skill dictionary (`yggdrasil-skills.json5`) as the canonical source for rich skill metadata:
     - `id`, `name`, `tier`, `tags`, `notes`, `evolution`, `combines`.
   - Add support tools to:
     - Auto-discover skills from `SkillAPI` and append missing entries.
     - Optionally suggest or infer basic tags/tier from skill type and naming.
   - Keep schema stable and extendable so that quests can later reference the same entries for reward/requirement selection.

4. **New Item and Mob Dictionaries**
   - Introduce JSON5 dictionaries:
     - `yggdrasil-items.json5`
     - `yggdrasil-mobs.json5`
   - Represent both vanilla and modded content via `ResourceLocation` IDs.
   - Store auto-inferred metadata plus optional hand-written notes/tags.
   - Keep schemas intentionally general-purpose so they can be re-used by quests and other systems:
     - Items: combat role, utility role, rarity, tags, notes.
     - Mobs: category, difficulty, biome/theme tags, notes.

5. **Dictionary-Aware AI Prompts**
   - Yggdrasil (unique skill assignment):
     - Continue using the skill dictionary to describe eligible candidates.
     - Keep prompts focused on skills; do not flood with items/mobs to preserve token budget.
   - Wishes:
     - Provide curated “sample palettes” of skills, items, and (later) mobs drawn from the dictionaries.
     - Explicitly instruct the AI to only propose IDs that exist in these samples or known base content.
   - Future quests:
     - Allow quest generation to consume the same dictionaries to choose goals, rewards, and enemies.

6. **Auto-Registration Commands**
   - Extend `/worldvoice yggdrasil` with subcommands:
     - `/worldvoice yggdrasil register skills`
     - `/worldvoice yggdrasil register items`
     - `/worldvoice yggdrasil register mobs`
     - `/worldvoice yggdrasil register all`
   - Each command scans the relevant registries, merges new entries into the dictionaries, and writes them to disk.
   - Designed for per-modlist adaptation and minimal manual maintenance.

7. **Safety, Validation, and Telemetry**
   - Strictly validate AI output IDs against local registries and/or dictionaries.
   - Maintain logs that include key normalized stats and decisions for later auditing.
   - Keep command execution tightly constrained to safe verbs and `%player%` placeholders, as in the existing wish system.

---

### 3. Implementation Plan (Step-by-Step)

#### 3.1 Player Profiling and Bias Reduction

**What**  
Expand `PlayerProfileSnapshot` and adjust `YggdrasilService` heuristics and prompts to use richer, normalized player data and avoid trivial playtime/travel bias.

**Why**  
AI decisions should reflect *how* a player engages with the world (role/playstyle), not just *how long* they have been online or how far they have traveled.

**How**

1. **Extend `PlayerProfileSnapshot`**
   - Add fields:
     - Combat: `mobsKilled`, `bossesKilled`, `damageDealt`, `damageTaken`.
     - Blocks: `blocksMined`, `blocksPlaced`.
     - Items: `itemsCrafted`, `itemsUsed`.
     - Dimensions: `timeOverworldTicks`, `timeNetherTicks`, `timeEndTicks` (or similar).
   - Populate in `PlayerProfileSnapshot.capture(ServerPlayer, ResourceLocation, Extras)` using `ServerStatsCounter` and `Stats.*` APIs.

2. **Derived Metrics and Tags**
   - Add helper methods or a nested helper to compute:
     - `double playHours()`
     - Ratios such as kills per hour, deaths per hour, blocks placed per hour, etc.
   - Define simple tag derivation rules, e.g.:
     - `builder`: blocks placed per hour above threshold.
     - `explorer`: high dimension travel time, moderate combat.
     - `slayer`: high kill rate, low deaths.
     - `support`: low combat but high team play / assists (if measurable).
   - Represent tags as a list or a compact string for prompts.

3. **Update Heuristic Scoring (`YggdrasilService.computeScore`)**
   - Replace `playTicks`-based weight with capped, normalized contributions:
     - Use `playHours()` with a small cap (e.g., +0.0 to +1.0).
   - Optionally remove `distanceTravelled` from scoring, or limit it to a small contribution if it adds clear value.
   - Factor in tags instead of raw stats where possible (e.g., slight boost for `builder` for certain skill types if you later encode skill tags).

4. **Enhance Yggdrasil Evaluation Prompt**
   - In `buildPrompt(...)`, add:
     - `playHours`, a concise summary of key ratios, and `ProfileTags=[...]`.
   - Keep raw stats present for fallback but treat derived tags as the primary semantic signal for the AI.

5. **Enhance Wish Prompt**
   - In `requestWishDecision(...)`, append to the user message:
     - `playHours`, `ProfileTags`, and any other high-signal normalized metrics.
   - Ensure the system prompt hints that these tags represent the player’s “fate” or “path” to keep the flavor consistent.

---

#### 3.2 Dictionary Framework (Skills, Items, Mobs)

**What**  
Generalize the existing skill dictionary pattern into three parallel dictionaries: skills, items, mobs. Design schemas and loaders with future quest integration in mind.

**Why**  
AI (and future quest logic) needs a structured, mod-aware view of content to make coherent and safe choices. This also provides a stable contract between content and AI behavior.

**How**

1. **Skill Dictionary Enhancements (Minimal)**
   - Keep the current schema:
     - `id`, `name`, `tier`, `tags`, `notes`, `evolution`, `combines`.
   - Optionally:
     - Add optional `role` or `archetype` fields (e.g., `offense`, `defense`, `support`, `utility`) to help AI align skills with player tags.
     - These must be optional and defaultable for backward compatibility.

2. **Item Dictionary**
   - Create `ItemDictionary` with:
     - Internal map `ResourceLocation -> ItemMeta`.
     - Static `load(Path customPath, InputStream defaultStream)`, modeled on `SkillDictionary.load()`.
   - Proposed JSON5 schema:
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
           "notes": "Lore or constraints (e.g., dangerous, boss-only, quest-gated)."
         }
       ]
     }
     ```
   - Keep all fields optional except `id`; `name` can be prettified from the path if omitted.
   - Ensure it can be extended later with more quest-specific hints (e.g., recommended minimum level, biome affinity) without breaking existing entries.

3. **Mob Dictionary**
   - Create `MobDictionary` with a similar pattern:
     - Internal map `ResourceLocation -> MobMeta`.
   - Proposed JSON5 schema:
     ```json5
     {
       "mobs": [
         {
           "id": "modid:mob_name",
           "name": "Pretty Name",
           "mod": "modid",
           "category": "monster|creature|ambient|water_creature|boss|npc",
           "difficulty": 1.0, // 0.0–10.0 heuristic rating
           "tags": ["undead", "flying", "fire_immune"],
           "notes": "Lore, behavior hints, quest usage notes."
         }
       ]
     }
     ```
   - As with items, keep fields optional and forward-compatible. Future quest logic can use `category`, `difficulty`, and `tags` to select appropriate enemies.

4. **Config Hook-Up**
   - In `WVConfig`, under `yggdrasil`, add:
     - `YGG_ITEM_DICTIONARY_PATH` (default: `config/worldvoice/yggdrasil-items.json5`).
     - `YGG_MOB_DICTIONARY_PATH` (default: `config/worldvoice/yggdrasil-mobs.json5`).
   - Use the same load strategy as the skill dictionary: custom path first, then fallback `defaultconfigs` resource.

5. **Caching and Reloading**
   - In `YggdrasilService`, mirror the existing `SkillDictionary` handling:
     - Maintain `ItemDictionary` and `MobDictionary` references and timestamps.
     - Implement `ensureItemDictionary()` / `ensureMobDictionary()` with stale checks and reload logic.
     - Provide a public `reloadDictionaries()` method for the command system to call.

---

#### 3.3 Auto-Registration Commands

**What**  
Add `/worldvoice yggdrasil register ...` commands to scan registries and update dictionary files for skills, items, and mobs.

**Why**  
Automates adaptation to different modlists, reduces manual dictionary maintenance, and ensures the AI can see all relevant content for a given instance.

**How**

1. **Command Structure**
   - Extend `buildYggdrasilCommand()` to include:
     - `register` literal with sub-literals:
       - `skills`
       - `items`
       - `mobs`
       - `all`
   - Restrict to appropriate permission level (same as other administrative Yggdrasil commands).

2. **Skill Registration Flow**
   - Load existing skill dictionary from `YGG_DICTIONARY_PATH`.
   - Build a set of known IDs.
   - Iterate `SkillAPI.getSkillRegistry().getValues()`:
     - For each `ManasSkill`:
       - Get `ResourceLocation` ID.
       - If not in dictionary, create a new `SkillMeta`:
         - `name`: prettified ID.
         - `tier`: inferred from `SkillConfigManager.detectCategory(...)` and/or Tensura `SkillType` if available (fallback to a neutral default).
         - `tags`/`notes`: empty; can be edited manually later.
   - Save the updated dictionary back to disk (preserving any existing, hand-edited entries).
   - Inform the command sender of how many entries were added.

3. **Item Registration Flow**
   - Load existing item dictionary from `YGG_ITEM_DICTIONARY_PATH`.
   - Build a set of known item IDs.
   - Iterate `ForgeRegistries.ITEMS.getValues()`:
     - For each item:
       - Resolve its `ResourceLocation` ID.
       - If new, create `ItemMeta` with:
         - `mod` from `id.getNamespace()`.
         - `name` from prettified ID.
         - `role` inferred from class hierarchy (`SwordItem`, `ArmorItem`, `FoodProperties`, tool interfaces) and/or ID path.
         - Optional `tags` inferred from item tags and naming heuristics (e.g., `_sword`, `_pickaxe`, `apple`).
   - Save to `YGG_ITEM_DICTIONARY_PATH` with JSON5 output.

4. **Mob Registration Flow**
   - Load existing mob dictionary from `YGG_MOB_DICTIONARY_PATH`.
   - Build a set of known mob IDs.
   - Iterate `ForgeRegistries.ENTITY_TYPES.getValues()`:
     - Filter to living entities / mobs (category checks).
     - For each entity type:
       - Resolve its `ResourceLocation` ID.
       - If new, create `MobMeta` with:
         - `mod` from namespace.
         - `name` from prettified ID.
         - `category` from entity type category and/or heuristics.
         - `difficulty` derived from attributes (max health, attack damage) with a simple heuristic.
         - `tags` for key characteristics (e.g., `undead`, `flying`), using tags and class checks.
   - Save to `YGG_MOB_DICTIONARY_PATH`.

5. **`all` Subcommand**
   - Invoke `skills`, `items`, and `mobs` flows in sequence.
   - Summarize results (entries added per dictionary) to the caller.

6. **Reload Subcommand**
   - Optionally add `/worldvoice yggdrasil dictionary reload`:
     - Calls `YggdrasilService.reloadDictionary()` (skills) and new reload methods for items/mobs.
     - Confirms dictionary sizes after reload.

---

#### 3.4 AI Prompt Integration

**What**  
Integrate the new profiling and dictionaries into AI prompts, while maintaining safety and controlling token usage.

**Why**  
The AI needs structured, relevant context to make good decisions, but cannot be overwhelmed with entire registries. We must feed it curated, validated slices.

**How**

1. **Yggdrasil Unique Skill Evaluation**
   - Continue to:
     - Use `SkillDictionary` to enrich candidate descriptions in the prompt (already implemented).
   - Enhance with new profile fields:
     - Include `playHours`, derived ratios, and `ProfileTags` in the prompt.
   - Do **not** inject full item/mob dictionaries; they are not needed for this decision path.

2. **Wish Evaluation**
   - After constructing the player summary (with new metrics and tags), append:
     - `SampleUniqueSkills`: limited list of dictionary entries relevant to the player (e.g., based on tags or random subset).
     - `SampleItems`: a small curated subset drawn from `ItemDictionary` (e.g., lore-friendly, mid-power items, biased toward the player’s current tier).
     - (Future) `SampleMobs`: small subset from `MobDictionary` if/when wishes can summon mobs or target entities.
   - Update the system prompt to:
     - Explicitly state that the AI must only use IDs present in the `Sample*` lists or known base Minecraft content.
     - Forbid inventing arbitrary `modid:foo` values.

3. **Validation**
   - Skills: keep current `containsCandidate` check before granting.
   - Items:
     - Validate `itemId` against `ForgeRegistries.ITEMS`.
     - Optionally also require membership in `ItemDictionary` to be strict.
   - Mobs (future):
     - Validate `mobId` against `ForgeRegistries.ENTITY_TYPES` and `MobDictionary`.
   - If validation fails:
     - Fallback to a safe boon/denial.
     - Log the anomaly for debugging but do not apply unsafe decisions.

4. **Quest System Readiness (Future)**
   - Design dictionary schemas with extra, optional fields that quests can leverage, such as:
     - Items: `questWeight`, `minLevel`, `biome`, `theme`.
     - Mobs: `questWeight`, `biome`, `faction`, `respawnRules`.
   - Keep all such fields optional and non-breaking so that current AI logic does not depend on them but can start using them gradually as they appear.

---

### 4. Future Quest Integration (Design Notes Only)

> **Note:** Out of scope for immediate implementation; this section documents how the current design remains open-ended for quests.

- **Shared dictionaries:**  
  The skill, item, and mob dictionaries are intentionally generic and mod-aware, making them suitable for:
  - Quest reward selection (skills/items).
  - Quest target/encounter selection (mobs).
  - Quest flavor text (#tags and `notes` fields).

- **Profile-driven quest hooks:**  
  The expanded `PlayerProfileSnapshot` and tags can later drive:
  - “Path of the Builder/Slayer/Explorer” quest arcs.
  - Dynamic difficulty scaling based on `difficulty` in `MobMeta` and player normalized metrics.

- **AI-backed quest authoring:**  
  The same AI integration route used for wishes and unique skill assignment can later be extended to propose:
  - Quest objectives (kill X, fetch Y, visit Z) by referencing dictionaries.
  - Quest rewards from the same skill/item dictionaries, constrained by difficulty and player tags.

Nothing in the above requires altering the core dictionary schemas; it only adds new consumers and optional fields over time.

---

### 5. Work Log

This section is a running log for future maintainers and AI instances. Append new entries at the end with the most recent first.

#### Entry 2025-11-15 – Initial Plan Draft

- Created this `WorldVoice-AI-Expansion-Plan.md` document.
- Defined core goals: richer profiling, mod-aware dictionaries, configurable AI behavior, and future quest integration.
- Outlined high-level features, including:
  - Expanded `PlayerProfileSnapshot` and bias-resistant scoring.
  - Item and mob dictionaries parallel to the existing skill dictionary.
  - Auto-registration commands for skills, items, and mobs.
  - AI prompt integration and validation strategy.
- Documented how dictionary schemas remain forward-compatible for future quest integration.

