# Diagnostic Logging for Dictionary Loading

## Problem
The wishing system appears to only grant vanilla items despite the user running `/worldvoice yggdrasil register all` to populate dictionaries. The root cause is unclear - could be a path mismatch where the code reads from a different location than where files are written.

## Solution: Enhanced Diagnostic Logging

Added comprehensive logging to show exactly where the system is trying to load dictionaries from and whether files exist at those locations.

## Changes Made

### 1. YggdrasilService.java
Added logging to all three dictionary reload methods:

**reloadDictionary()** (Skills):
- Logs configured path (relative and absolute)
- Logs whether file exists
- Logs number of entries loaded

**reloadItemDictionary()** (Items):
- Logs configured path (relative and absolute)
- Logs whether file exists
- Logs number of entries loaded

**reloadMobDictionary()** (Mobs):
- Logs configured path (relative and absolute)
- Logs whether file exists
- Logs number of entries loaded

### 2. ItemDictionary.java
Enhanced `load()` method to show:
- When custom file is found and successfully loaded
- **When custom file does NOT exist** (path mismatch!)
- When falling back to embedded JAR resource
- When both custom and fallback fail

### 3. SkillDictionary.java
Enhanced `load()` method with same logging as ItemDictionary

### 4. MobDictionary.java
Enhanced `load()` method with same logging as ItemDictionary

## How to Diagnose the Issue

When the server starts or when dictionaries are reloaded, you'll now see log messages like:

### If files are in the correct location:
```
[INFO] Loading item dictionary from configured path: config/worldvoice/yggdrasil-items.json5 (absolute: <project>/config/worldvoice/yggdrasil-items.json5)
[INFO] Item dictionary file exists: true
[INFO] Item dictionary: Custom file found at <project>/config/worldvoice/yggdrasil-items.json5, attempting to load...
[INFO] Item dictionary: Successfully loaded from custom file
[INFO] Item dictionary loaded: 1842 total entries
```

### If there's a path mismatch (PROBLEM!):
```
[INFO] Loading item dictionary from configured path: serverconfig/worldvoice/yggdrasil-items.json5 (absolute: <project>/serverconfig/worldvoice/yggdrasil-items.json5)
[INFO] Item dictionary file exists: false
[WARN] Item dictionary: Custom file does NOT exist at <project>/serverconfig/worldvoice/yggdrasil-items.json5
[WARN] Item dictionary: Loading from embedded fallback resource (JAR file)
[INFO] Item dictionary loaded: 1 total entries  <-- Only 1 entry! Using fallback!
```

### What the paths tell you:

| Configured Path | Absolute Path | Meaning |
|----------------|---------------|----------|
| `config/worldvoice/...` | `/server/config/worldvoice/...` | ✅ Default path (correct for COMMON config) |
| `serverconfig/worldvoice/...` | `/server/serverconfig/worldvoice/...` | ⚠️ Config might have been manually changed |
| Other path | Other absolute path | ⚠️ Custom configuration |

## Expected Behavior After Fix

Once you compile and run with these changes:

1. **Check server logs** on startup or after running `/worldvoice yggdrasil register all`
2. Look for the "Loading item dictionary from configured path:" message
3. **Check if "file exists: false"** - this is the smoking gun!
4. **Check the absolute path** shown in logs
5. **Compare with where you actually wrote the files**

## Common Scenarios

### Scenario 1: Config file has wrong path
**Symptom:** Logs show `serverconfig/worldvoice/...` but files are in `config/worldvoice/...`

**Fix:** Edit `config/worldvoice-common.toml`:
```toml
[yggdrasil]
itemDictionaryPath = "config/worldvoice/yggdrasil-items.json5"
dictionaryPath = "config/worldvoice/yggdrasil-skills.json5"
mobDictionaryPath = "config/worldvoice/yggdrasil-mobs.json5"
```

### Scenario 2: Files written to wrong location
**Symptom:** Logs show `config/worldvoice/...` but files are in `serverconfig/worldvoice/...`

**Fix:** Move files to correct location:
```bash
mkdir -p config/worldvoice
mv serverconfig/worldvoice/*.json5 config/worldvoice/
```

Or re-run the register command which will write to the configured path.

### Scenario 3: Working directory issue
**Symptom:** Absolute path doesn't match server root

**Fix:** Paths are relative to server working directory. Ensure server is started from the correct directory, or use absolute paths in config.

## Additional Diagnostic Command

You can also manually trigger a dictionary reload to see the logs:
```
/worldvoice yggdrasil reload
```

(if this command exists, or just restart the server/wait 5 minutes for auto-reload)

## Next Steps

1. Compile and deploy these changes
2. Start server and check logs immediately
3. Look for the log messages showing:
   - What path is being checked
   - Whether file exists at that path
   - How many entries were loaded
4. If "file exists: false", you've found the problem!
5. Fix the path mismatch (either move files or update config)
6. Run `/worldvoice yggdrasil register items` again to regenerate if needed

## If Logging Shows Files Exist But Still Only 1 Entry

If logs show:
- File exists: true
- Successfully loaded from custom file
- **But only 1 total entry**

Then the actual problem is the JSON file content itself is empty/has only 1 item. In that case:
- Check the file size: `ls -lh config/worldvoice/yggdrasil-items.json5`
- If <5KB, the register command didn't work properly
- Re-run: `/worldvoice yggdrasil register all`
- Check logs for "Registered X new item entries" message

## Success Criteria

You know it's working when logs show:
```
[INFO] Item dictionary loaded: [large number like 1000+] total entries
```

Not:
```
[INFO] Item dictionary loaded: 1 total entries
```
