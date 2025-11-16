# AI Modded Content Analysis: Why the AI Can't Reference Modded Items/Mobs

## Executive Summary

**ROOT CAUSE:** The AI is completely blind to modded (and vanilla) items and mobs because `ItemDictionary.java` and `MobDictionary.java` **do not exist**. The code references these classes but they haven't been implemented yet.

**Impact:** When the AI processes wishes, it has:
- ✅ Full knowledge of skills (via SkillDictionary)
- ❌ **ZERO knowledge of items** (ItemDictionary missing)
- ❌ **ZERO knowledge of mobs** (MobDictionary missing)

**Solution:** Implement ItemDictionary and MobDictionary following the SkillDictionary pattern.

---

## Detailed Analysis

### 1. Current State

#### What Works (Skills)
The skill system is **fully functional**:
- `SkillDictionary.java` exists at: `src/main/java/org/faded/worldvoice/ai/SkillDictionary.java`
- Loads from: `config/worldvoice/yggdrasil-skills.json5`
- Provides rich metadata: id, name, tier, tags, notes, evolution, combines
- **AI receives up to 10 relevant skills** in wish prompts (line 592 of YggdrasilService.java)

Example skill dictionary entry:
```json5
{
  "id": "tensura:absolute_severance",
  "name": "Absolute Severance",
  "tier": 3,
  "tags": ["Offensive", "Severance", "Ranged", "Haki"],
  "notes": "Coat strikes with severance...",
  "evolution": "trnightmare:camael"
}
```

#### What's Broken (Items & Mobs)
The item and mob systems are **completely non-functional**:

**Missing Classes:**
- `ItemDictionary.java` - **DOES NOT EXIST**
- `MobDictionary.java` - **DOES NOT EXIST**

**Evidence from YggdrasilService.java:**
```java
// Line 79-80: Declared but never initialized properly
private ItemDictionary itemDictionary;
private MobDictionary mobDictionary;

// Line 107-118: Methods that call non-existent classes
private ItemDictionary ensureItemDictionary() {
    if (itemDictionaryLoaded.compareAndSet(false, true) || staleItemDictionary()) {
        reloadItemDictionary();
    }
    return itemDictionary;  // Returns null - ItemDictionary.load() doesn't exist
}

// Line 133-137: Tries to load non-existent ItemDictionary class
public void reloadItemDictionary() {
    itemDictionaryLastAttempt = System.currentTimeMillis();
    Path configured = Path.of(WVConfig.YGG_ITEM_DICTIONARY_PATH.get());
    InputStream fallback = YggdrasilService.class.getResourceAsStream("/defaultconfigs/worldvoice/yggdrasil-items.json5");
    itemDictionary = ItemDictionary.load(configured, fallback);  // COMPILATION ERROR - class doesn't exist
}

// Line 589-594: Wish system tries to use these dictionaries
ItemDictionary itemDict = itemDictionary();  // Returns null
MobDictionary mobDict = mobDictionary();     // Returns null
List<ItemDictionary.ItemMeta> sampleItems = allowItems ? pickItemSamples(itemDict, tokens, 14) : List.of();  // Empty
List<MobDictionary.MobMeta> sampleMobs = pickMobSamples(mobDict, tokens, 10);  // Empty

// Line 1190-1214: pickItemSamples returns empty list when dict is null
private List<ItemDictionary.ItemMeta> pickItemSamples(ItemDictionary dict, List<String> tokens, int limit) {
    if (dict == null || limit <= 0) return List.of();  // Always returns empty!
    // ... rest of code never executes
}
```

### 2. How This Breaks Modded Content References

When the AI evaluates a wish like "I wish for a spatial blade", here's what happens:

**Prompt Building (requestWishDecision, line 580-685):**

```java
// Line 592-594: Samples are collected
List<SkillDictionary.SkillMeta> sampleSkills = pickSkillSamples(dict, tokens, 10);  // ✅ Works - gets 10 skills
List<ItemDictionary.ItemMeta> sampleItems = allowItems ? pickItemSamples(itemDict, tokens, 14) : List.of();  // ❌ Empty
List<MobDictionary.MobMeta> sampleMobs = pickMobSamples(mobDict, tokens, 10);  // ❌ Empty
```

**Prompt Sent to AI (line 649-657):**

```
SampleUniqueSkills: tensura:absolute_severance|Absolute Severance|tier=3|tags=[Offensive,Severance]; ...
SampleItems:   <-- EMPTY, NOTHING HERE
SampleMobs:    <-- EMPTY, NOTHING HERE
```

**AI System Prompt (line 619):**
```
Use only identifiers drawn from SampleUniqueSkills, SampleItems, or SampleMobs (or obvious vanilla defaults). Never invent ids.
```

**Result:** The AI has:
- ✅ Knowledge of 10 relevant skills
- ❌ **ZERO knowledge of any items** (not even vanilla minecraft:diamond)
- ❌ **ZERO knowledge of any mobs**
- ❌ **Cannot suggest tensura:spatial_blade** because it was never provided as an option

The AI can only work with what's in the prompt. Since items/mobs are completely missing, it either:
1. Refuses to grant items (follows instruction "Never invent ids")
2. Only uses "obvious vanilla defaults" like minecraft:diamond (guessing)
3. Grants skills instead (the only thing it has knowledge of)

---

## 3. Why Skills Work But Items Don't

### SkillDictionary Implementation (COMPLETE)

**File:** `src/main/java/org/faded/worldvoice/ai/SkillDictionary.java` (261 lines)

**Key Features:**
```java
public final class SkillDictionary {
    private final Map<ResourceLocation, SkillMeta> entries = new LinkedHashMap<>();

    // Loads from JSON5 file
    public static SkillDictionary load(Path customPath, InputStream defaultStreamSupplier) {
        SkillDictionary dict = new SkillDictionary();
        // ... loading logic
        return dict;
    }

    // Lookup by ID
    public Optional<SkillMeta> lookup(ResourceLocation id) {
        return Optional.ofNullable(entries.get(id));
    }

    // Get all entries
    public Collection<SkillMeta> all() {
        return Collections.unmodifiableCollection(entries.values());
    }

    // Rich metadata
    public record SkillMeta(
        ResourceLocation id,
        String name,
        int tier,
        String notes,
        List<String> tags,
        ResourceLocation evolution,
        List<ResourceLocation> combines
    ) { }
}
```

**Data File:** `src/main/resources/defaultconfigs/worldvoice/yggdrasil-skills.json5`

### ItemDictionary Implementation (MISSING)

**Expected File:** `src/main/java/org/faded/worldvoice/ai/ItemDictionary.java` - **DOES NOT EXIST**

**Expected Pattern (from design docs):**
```java
public final class ItemDictionary {
    private final Map<ResourceLocation, ItemMeta> entries = new LinkedHashMap<>();

    public static ItemDictionary load(Path customPath, InputStream defaultStreamSupplier) {
        // Same pattern as SkillDictionary
    }

    public Optional<ItemMeta> lookup(ResourceLocation id) {
        return Optional.ofNullable(entries.get(id));
    }

    public Collection<ItemMeta> all() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public record ItemMeta(
        ResourceLocation id,
        String name,
        String mod,
        String role,     // weapon|armor|tool|food|utility|material|quest|other
        String rarity,   // common|uncommon|rare|epic|legendary|mythic
        List<String> tags,
        String notes
    ) { }
}
```

**Expected Data File:** `config/worldvoice/yggdrasil-items.json5` - **DOES NOT EXIST**

**Expected Format:**
```json5
{
  "items": [
    {
      "id": "tensura:spatial_blade",
      "name": "Spatial Blade",
      "mod": "tensura",
      "role": "weapon",
      "rarity": "epic",
      "tags": ["spatial", "sword", "magic"],
      "notes": "A blade that cuts through space itself"
    },
    {
      "id": "minecraft:diamond_sword",
      "name": "Diamond Sword",
      "mod": "minecraft",
      "role": "weapon",
      "rarity": "common",
      "tags": ["sword", "melee"],
      "notes": "Standard diamond weapon"
    }
  ]
}
```

### MobDictionary Implementation (MISSING)

**Expected File:** `src/main/java/org/faded/worldvoice/ai/MobDictionary.java` - **DOES NOT EXIST**

**Expected Pattern:**
```java
public final class MobDictionary {
    private final Map<ResourceLocation, MobMeta> entries = new LinkedHashMap<>();

    // Same load/lookup/all pattern as SkillDictionary

    public record MobMeta(
        ResourceLocation id,
        String name,
        String mod,
        String category,  // monster|creature|ambient|water_creature|boss|npc
        double difficulty,  // 0.0-10.0
        List<String> tags,
        String notes
    ) { }
}
```

**Expected Data File:** `config/worldvoice/yggdrasil-mobs.json5` - **DOES NOT EXIST**

---

## 4. Impact on AI Behavior

### Current AI Prompt for Wishes (WITHOUT Items/Mobs)

```
Player=Steve level=20 deaths=5 playTicks=144000 ...
Wish: I want a powerful spatial blade

ProfileTags=combatant,slayer
SampleUniqueSkills: tensura:absolute_severance|Absolute Severance|tier=3|tags=[Offensive,Severance]; tensura:spatial_motion|Spatial Motion|tier=2|tags=[Utility,Spatial]
SampleItems:    <-- COMPLETELY EMPTY
SampleMobs:     <-- COMPLETELY EMPTY

AllowedCommands=give,effect,worldvoice,xp,experience,playsound,attribute,data modify entity,title. Use %player% placeholder.
```

**AI's Perspective:**
- "I can see 2 skills related to 'spatial'"
- "I have NO information about any items whatsoever"
- "I'm told to never invent IDs"
- "I can only suggest: tensura:spatial_motion skill OR deny the wish OR guess minecraft:diamond_sword"

### Ideal AI Prompt (WITH Items/Mobs)

```
Player=Steve level=20 deaths=5 playTicks=144000 ...
Wish: I want a powerful spatial blade

ProfileTags=combatant,slayer
SampleUniqueSkills: tensura:absolute_severance|Absolute Severance|tier=3|tags=[Offensive,Severance]; tensura:spatial_motion|Spatial Motion|tier=2|tags=[Utility,Spatial]
SampleItems: tensura:spatial_blade|Spatial Blade|role=weapon|rarity=epic|tags=[spatial,sword,magic]; tensura:demon_blade|Demon Blade|role=weapon|rarity=legendary|tags=[demon,sword]; minecraft:diamond_sword|Diamond Sword|role=weapon|tags=[sword,melee]
SampleMobs: tensura:spatial_dragon|Spatial Dragon|category=boss|difficulty=8.5|tags=[dragon,spatial,flying]

AllowedCommands=give,effect,worldvoice,xp,experience,playsound,attribute,data modify entity,title. Use %player% placeholder.
```

**AI's Perspective:**
- "I can see 2 skills related to 'spatial'"
- "I can see 3 items, including tensura:spatial_blade which matches perfectly!"
- "I can grant: tensura:spatial_blade with appropriate narrative"
- "Result: give %player% tensura:spatial_blade 1"

---

## 5. Solution: Implementation Plan

### Phase 1: Create ItemDictionary.java

**File:** `src/main/java/org/faded/worldvoice/ai/ItemDictionary.java`

**Implementation:**
1. Copy SkillDictionary.java as template
2. Replace SkillMeta with ItemMeta record
3. Update parsing to handle item-specific fields (role, rarity, mod)
4. Same load/lookup/all pattern

**Estimated Effort:** ~30 minutes (mostly copy-paste from SkillDictionary)

### Phase 2: Create MobDictionary.java

**File:** `src/main/java/org/faded/worldvoice/ai/MobDictionary.java`

**Implementation:**
1. Copy SkillDictionary.java as template
2. Replace SkillMeta with MobMeta record
3. Update parsing to handle mob-specific fields (category, difficulty, mod)
4. Same load/lookup/all pattern

**Estimated Effort:** ~30 minutes

### Phase 3: Create Default Data Files

**Files:**
1. `src/main/resources/defaultconfigs/worldvoice/yggdrasil-items.json5`
2. `src/main/resources/defaultconfigs/worldvoice/yggdrasil-mobs.json5`

**Content:**
- Populate with common Tensura mod items/mobs
- Include vanilla minecraft references
- Use token-friendly tags for AI matching

**Estimated Effort:** 1-2 hours (data entry)

### Phase 4: Test & Iterate

1. Test AI wishes with modded items
2. Verify token matching works correctly
3. Add more items/mobs based on actual mod content
4. Monitor AI decision quality

**Estimated Effort:** 1-2 hours

---

## 6. Technical Details: Token Matching

The AI uses **token-based sampling** to provide relevant items/mobs (lines 1138-1264):

### How It Works

**1. Extract tokens from wish:**
```java
private List<String> extractTokens(String text) {
    String lowered = text.toLowerCase(Locale.ROOT);
    String[] raw = lowered.split("[^a-z0-9:_]+");
    // Filter tokens < 3 chars, return unique list
}
```

Example: "I wish for a spatial blade" → `["wish", "for", "spatial", "blade"]` → `["wish", "spatial", "blade"]`

**2. Match tokens to dictionary entries:**
```java
private boolean matchesItem(ItemDictionary.ItemMeta meta, String token) {
    String path = meta.id().getPath().toLowerCase();
    if (path.contains(token)) return true;  // "spatial_blade" contains "spatial" ✓
    if (meta.name().toLowerCase().contains(token)) return true;  // "Spatial Blade" contains "spatial" ✓
    for (String tag : meta.tags()) {
        if (tag.toLowerCase().contains(token)) return true;  // tags=["spatial"] contains "spatial" ✓
    }
    if (meta.role() != null && meta.role().toLowerCase().contains(token)) return true;  // role="weapon" contains "blade"?
    return false;
}
```

**3. Build sample (up to 14 items):**
- First pass: Add all token-matched items
- Second pass: Fill remaining slots with random items
- Format: `tensura:spatial_blade|Spatial Blade|role=weapon|tags=[spatial,sword,magic]`

**Why This Matters:**
- Good tags = better AI matching
- `tensura:spatial_blade` with tags `["spatial", "blade", "sword"]` will match wish "spatial blade"
- `tensura:demon_sword` with tags `["demon", "weapon"]` will match wish "demon weapon"

---

## 7. Quick Wins & Workarounds

### Immediate Workaround (Until Dictionaries Are Implemented)

**Option 1: Let AI use vanilla items**
- Modify system prompt to allow "minecraft:*" items without dictionary
- AI can grant minecraft:diamond_sword, minecraft:golden_apple, etc.
- Still can't reference modded items

**Option 2: Hardcode common modded items in prompt**
- Add a static list to the system prompt: "Available Tensura items: tensura:spatial_blade, tensura:demon_blade, ..."
- Brittle, doesn't scale, but works for testing

**Option 3: Use commands instead of item grants**
- AI can use typed actions: `{"type":"skill","op":"grant","skillId":"tensura:spatial_motion"}`
- Works for skills, not items/mobs

### Long-Term Solution

**Implement the dictionaries** - this is the only proper fix.

---

## 8. Configuration References

### Current Config (WVConfig.java)

```java
// Line 27-28
public static final ForgeConfigSpec.ConfigValue<String> YGG_ITEM_DICTIONARY_PATH;
public static final ForgeConfigSpec.ConfigValue<String> YGG_MOB_DICTIONARY_PATH;

// Line 92-95
YGG_ITEM_DICTIONARY_PATH = b.comment("Path to the item dictionary file describing mod-aware item metadata.")
    .define("yggdrasilItemDictionaryPath", "config/worldvoice/yggdrasil-items.json5");
YGG_MOB_DICTIONARY_PATH = b.comment("Path to the mob dictionary file describing entity metadata.")
    .define("yggdrasilMobDictionaryPath", "config/worldvoice/yggdrasil-mobs.json5");
```

**Config paths are already set up** - just need to implement the classes and create the files.

---

## 9. Evidence Summary

### Files That Exist
- ✅ `SkillDictionary.java` (261 lines)
- ✅ `yggdrasil-skills.json5` (default data)
- ✅ Config entries for item/mob dictionary paths
- ✅ Methods to load/use item/mob dictionaries (but they fail)

### Files That Don't Exist
- ❌ `ItemDictionary.java`
- ❌ `MobDictionary.java`
- ❌ `yggdrasil-items.json5`
- ❌ `yggdrasil-mobs.json5`

### Code Locations That Fail
- `YggdrasilService.java:137` - `itemDictionary = ItemDictionary.load(...)` - **compilation error**
- `YggdrasilService.java:144` - `mobDictionary = MobDictionary.load(...)` - **compilation error**
- `YggdrasilService.java:593` - `pickItemSamples(itemDict, ...)` - **always returns empty**
- `YggdrasilService.java:594` - `pickMobSamples(mobDict, ...)` - **always returns empty**
- `YggdrasilService.java:1190-1214` - `pickItemSamples` returns empty when dict is null
- `YggdrasilService.java:1228-1252` - `pickMobSamples` returns empty when dict is null

---

## 10. Recommendations

### Priority 1: Implement ItemDictionary (HIGH IMPACT)
- Directly solves the modded item reference problem
- Enables AI to suggest tensura:spatial_blade, tensura:demon_blade, etc.
- Estimated effort: ~2 hours (code + data)

### Priority 2: Implement MobDictionary (MEDIUM IMPACT)
- Allows wishes involving mobs ("summon a dragon", "spawn spatial beast")
- Less critical than items but completes the system
- Estimated effort: ~2 hours (code + data)

### Priority 3: Populate Dictionaries (ONGOING)
- Start with ~20-30 common modded items
- Add items/mobs as needed
- Community can contribute via JSON5 edits
- Can be automated with registration scanning commands

### Priority 4: Add Generation Commands (NICE TO HAVE)
- Commands already referenced: `/worldvoice yggdrasil genitemdictionary`, `/worldvoice yggdrasil genmobdictionary`
- Auto-populate dictionaries from ForgeRegistries
- Generate baseline JSON5 files from actual loaded mods
- Estimated effort: ~1 hour (logic exists in CommandRegistrar.java:1180-1235)

---

## Conclusion

**The AI cannot reference modded items/mobs because it literally has no knowledge of them.** The code infrastructure is in place (config, loading logic, sampling, formatting), but the actual dictionary classes don't exist.

**Fix:** Implement `ItemDictionary.java` and `MobDictionary.java` following the `SkillDictionary.java` pattern. This is a straightforward copy-modify task that will immediately enable modded content references.

**Why skills work:** Because SkillDictionary is fully implemented and provides the AI with rich metadata.

**Why items/mobs don't work:** Because ItemDictionary and MobDictionary are missing, returning null/empty, so the AI has zero context.

Once implemented, a wish like "I want a spatial blade" will work because:
1. Token extraction: `["spatial", "blade"]`
2. Item matching: `tensura:spatial_blade` (path + tags contain "spatial" + "blade")
3. Sample sent to AI: `tensura:spatial_blade|Spatial Blade|role=weapon|tags=[spatial,sword,magic]`
4. AI decision: `{"grant":true,"grantType":"item","itemId":"tensura:spatial_blade","itemCount":1,...}`
5. Result: Player receives tensura:spatial_blade
