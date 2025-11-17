# Wishing System Path Mismatch Analysis

## Problem Hypothesis

The wishing system may be reading dictionary files from a different location than where they're being written.

## Key Issue: Config Type Mismatch

`WVConfig` is registered as `ModConfig.Type.COMMON` but contains server-only settings (AI integration, wishing system). This can cause path resolution issues.

**File:** `Worldvoice.java:43`
```java
ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, WVConfig.SPEC);
```

### Forge Config Locations

- **COMMON** configs → `config/modid-common.toml`
- **SERVER** configs → `serverconfig/modid-server.toml`

## Potential Path Mismatches

### Scenario 1: Config file has wrong paths
If `config/worldvoice-common.toml` contains:
```toml
[yggdrasil]
itemDictionaryPath = "serverconfig/worldvoice/yggdrasil-items.json5"
```

But the register command writes to the default path:
```java
Path.of(WVConfig.YGG_ITEM_DICTIONARY_PATH.get()) // Returns "serverconfig/worldvoice/..."
```

The files won't be found when loading because:
```java
// YggdrasilService.java:135-137
Path configured = Path.of(WVConfig.YGG_ITEM_DICTIONARY_PATH.get());
InputStream fallback = YggdrasilService.class.getResourceAsStream("/defaultconfigs/worldvoice/yggdrasil-items.json5");
itemDictionary = ItemDictionary.load(configured, fallback);
// If Files.exists(configured) is false, uses embedded fallback with 1 item!
```

### Scenario 2: Working directory mismatch
`Path.of("config/worldvoice/yggdrasil-items.json5")` is relative. If the server's working directory is different at runtime vs when the command runs, paths won't match.

## Diagnostic Steps

### 1. Check actual config file location and contents
```bash
# Find the actual config file
find . -name "*worldvoice*.toml" -type f | grep -v "src/main/resources"

# Check what the paths are set to
cat config/worldvoice-common.toml | grep -i "dictionary"
```

### 2. Check where files were actually written
```bash
# Find all yggdrasil JSON files
find . -name "yggdrasil-*.json5" | grep -v "src/main/resources"

# Check their sizes (should be >100KB if populated)
ls -lh config/worldvoice/*.json5
ls -lh serverconfig/worldvoice/*.json5
```

### 3. Check server logs for fallback warning
```bash
grep -i "Falling back to embedded" logs/latest.log
grep -i "Failed to load item dictionary" logs/latest.log
```

## Solutions

### Solution 1: Add explicit logging to show which path is being used

**File:** `YggdrasilService.java:133-138`

Add logging:
```java
public void reloadItemDictionary() {
    itemDictionaryLastAttempt = System.currentTimeMillis();
    Path configured = Path.of(WVConfig.YGG_ITEM_DICTIONARY_PATH.get());
    LOGGER.info("Attempting to load item dictionary from: {}", configured.toAbsolutePath());
    LOGGER.info("File exists: {}", Files.exists(configured));
    InputStream fallback = YggdrasilService.class.getResourceAsStream("/defaultconfigs/worldvoice/yggdrasil-items.json5");
    itemDictionary = ItemDictionary.load(configured, fallback);
    LOGGER.info("Item dictionary loaded with {} entries", itemDictionary.all().size());
}
```

### Solution 2: Fix config type (if appropriate)

If these settings should only exist on servers, change to SERVER config:

**File:** `Worldvoice.java:43`
```java
// Change from:
ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, WVConfig.SPEC);

// To:
ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, WVConfig.SPEC);
```

Then update default paths to use `serverconfig/`:

**File:** `WVConfig.java:91-95`
```java
YGG_DICTIONARY_PATH = b.comment("Path to the skill dictionary file...")
    .define("dictionaryPath", "serverconfig/worldvoice/yggdrasil-skills.json5");
YGG_ITEM_DICTIONARY_PATH = b.comment("Path to the item dictionary file...")
    .define("itemDictionaryPath", "serverconfig/worldvoice/yggdrasil-items.json5");
YGG_MOB_DICTIONARY_PATH = b.comment("Path to the mob dictionary file...")
    .define("mobDictionaryPath", "serverconfig/worldvoice/yggdrasil-mobs.json5");
```

### Solution 3: Use absolute paths in config

Manually set absolute paths in the config file to ensure no ambiguity.

## Next Steps

1. Add logging to see exact paths being used
2. Check if files exist where the code expects them
3. Verify config type matches usage (server-only features should use SERVER config)
4. Ensure paths in TOML config match where files are written
