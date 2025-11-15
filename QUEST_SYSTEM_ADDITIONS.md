# Quest System New Features - Implementation Complete

## Summary of Additions

This document outlines all the new features added to the WorldVoice quest system.

---

## 1. Quest Requirements System ✅

### Files Created (Server - WorldVoice):
- `quest/requirement/QuestRequirement.java` - Base interface
- `quest/requirement/EPRequirement.java` - Requires minimum EP
- `quest/requirement/RaceRequirement.java` - Requires specific race
- `quest/requirement/SkillRequirement.java` - Requires specific skill
- `quest/requirement/AwakeningRequirement.java` - Requires awakening (True Hero, True Demon Lord, etc.)
- `quest/requirement/CompletedQuestRequirement.java` - Requires another quest completed
- `quest/requirement/QuestRequirementType.java` - Registry for requirement types

### Features:
- Quests can now have unlock requirements
- Requirements checked when assigning quests to players
- Supports: EP, Race, Skills, Awakening status, Quest completion
- Easily extensible for new requirement types

### Integration:
- Added to `AdvancedQuestData` with `requirements` list and `autoAppear` flag
- Requirements checked in `QuestLoader.assignQuestsToPlayer()`
- Serialized in JSON and network packets

---

## 2. Quest Refresh System ✅

### Files Created (Server - WorldVoice):
- `quest/QuestRefreshScheduler.java` - Automatic daily/weekly quest reset

### Features:
- **Daily Quests**: Reset at midnight each day
- **Weekly Quests**: Reset every Monday at midnight
- Automatic progress reset
- Completed quests become available again
- Per-player tracking (doesn't reset on login/logout)

### How It Works:
- Checks every minute via server tick event
- Tracks last reset timestamp per player
- Resets quest status and objective progress
- Syncs to client after reset

---

## 3. JSON Quest Loader ✅

### Files Created (Server - WorldVoice):
- `quest/QuestLoader.java` - Loads quests from config files

### Features:
- Loads quests from `config/worldvoice/quests/` directory
- Organized by folders: `daily/`, `weekly/`, `one_time/`, `special/`, `evolution/`
- Automatic quest assignment based on requirements
- Hot-reload support via `/wvquest reload` command
- Creates example quest on first run

### Quest File Structure:
```
config/worldvoice/quests/
├── daily/
│   └── monster_hunt.json
├── weekly/
│   └── gathering.json
├── one_time/
│   └── first_quest.json
├── special/
│   └── advanced_training.json
└── evolution/
    └── kijin_evolution.json
```

### JSON Format:
```json
{
  "quest_id": "worldvoice:example_quest",
  "title": "Quest Title",
  "description": "Quest description",
  "category": "DAILY",
  "status": "AVAILABLE",
  "auto_appear": true,
  "objectives": [...],
  "rewards": [...],
  "requirements": [...]
}
```

---

## 4. KubeJS Integration ✅

### Files Created (Server - WorldVoice):
- `quest/KubeJSQuestRegistry.java` - KubeJS integration API
- `kubejs_example_quests.js` - Example KubeJS script

### Features:
- Register quests programmatically from KubeJS scripts
- Builder pattern for easy quest creation
- Supports all objective types, reward types, and requirements
- JSON string registration also supported

### Example Usage:
```javascript
let builder = Java.type('org.faded.worldvoice.quest.KubeJSQuestRegistry').create('my_quest');

let quest = builder
    .title('My Quest')
    .description('Quest description')
    .category('DAILY')
    .requireEP(1000)
    .addKillMobObjective('minecraft:zombie', 10)
    .addItemReward('minecraft:diamond', 5)
    .build();

Java.type('org.faded.worldvoice.quest.KubeJSQuestRegistry').register(quest);
```

### Supported Methods:
**Requirements:**
- `requireEP(amount)`
- `requireRace(raceId)`
- `requireSkill(skillId)`
- `requireAwakening(type)`
- `requireQuestCompleted(questId, questName)`

**Objectives:**
- `addKillMobObjective(mobType, required)`
- `addCollectItemObjective(itemId, required, consume)`
- `addEPRequirementObjective(required, consume, unlock)`
- `addEPGainObjective(required)`

**Rewards:**
- `addItemReward(itemId, count)`
- `addCommandReward(command)`
- `addSkillReward(skillId)`
- `addEPLevelsReward(levels)`
- `addMaxMagiculesReward(amount)`
- `addMaxAuraReward(amount)`
- `addExperienceReward(levels)`

---

## 5. Admin Commands ✅

### Files Created (Server - WorldVoice):
- `command/QuestCommand.java` - Admin quest management commands

### Commands:
```
/wvquest give <players> <questId>      - Give quest to player(s)
/wvquest remove <players> <questId>    - Remove quest from player(s)
/wvquest complete <players> <questId>  - Force complete a quest
/wvquest reset <players> <questId>     - Reset quest progress
/wvquest resetall <players>            - Remove all quests from player(s)
/wvquest list <player>                 - List all quests for a player
/wvquest reload                        - Reload quests from config files
/wvquest forcerefresh <players> <type> - Force refresh daily/weekly quests
```

### Permissions:
- Requires OP level 2 (operator permissions)

---

## 6. Client-Side Updates

### Files To Update in ArcadiaClientUtils:
1. **Create requirement package** (simplified versions):
   - `quest/requirement/QuestRequirement.java`
   - `quest/requirement/EPRequirement.java` (and other types as needed)
   - `quest/requirement/QuestRequirementType.java`

2. **Update AdvancedQuestData.java**:
   - Add `requirements` field
   - Add `autoAppear` field
   - Update serialization methods (toJson, toNetwork, fromJson, fromNetwork)
   - Add `getRequirements()` method

3. **Update AdvancedQuestScreen.java**:
   - Display requirements in quest details
   - Show requirement status (met/not met) with color coding
   - Display "Locked" status for quests with unmet requirements

### UI Display Example:
```
Quest Title
------------
Description...

Requirements:
  ✓ Requires 5000 EP
  ✗ Requires race: Ogre
  ✓ Requires skill: Predator

Objectives:
  ...
```

---

## 7. Integration Points

### Worldvoice.java Changes:
```java
// In commonSetup:
event.enqueueWork(() -> {
    QuestObjectiveType.init();
    QuestRewardType.init();
    QuestRequirementType.init();  // NEW
});

// In onServerStarting:
QuestLoader.loadQuests();  // NEW
```

### QuestEventHandler.java Changes:
```java
@SubscribeEvent
public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
        QuestRefreshScheduler.initializePlayer(player.getUUID());  // NEW
        QuestLoader.assignQuestsToPlayer(player);  // NEW

        // Existing sync code...
    }
}
```

### CommandRegistrar.java Changes:
```java
@SubscribeEvent
public void onRegister(RegisterCommandsEvent evt) {
    CommandDispatcher<CommandSourceStack> dispatcher = evt.getDispatcher();

    QuestCommand.register(dispatcher);  // NEW

    // Existing commands...
}
```

---

## 8. Example Quest Configurations

### Example 1: Daily Quest with No Requirements
```json
{
  "quest_id": "worldvoice:daily_monster_hunt",
  "title": "Daily Monster Hunt",
  "description": "Defeat 20 zombies",
  "category": "DAILY",
  "status": "AVAILABLE",
  "auto_appear": true,
  "objectives": [
    {
      "type": "kill_mob",
      "mob_type": "minecraft:zombie",
      "required": 20,
      "current": 0
    }
  ],
  "rewards": [
    {
      "type": "item",
      "item_id": "minecraft:diamond",
      "count": 3
    }
  ],
  "requirements": []
}
```

### Example 2: Evolution Quest with Requirements
```json
{
  "quest_id": "worldvoice:ogre_to_kijin",
  "title": "Path to Kijin",
  "description": "Evolve from Ogre to Kijin",
  "category": "EVOLUTION",
  "status": "AVAILABLE",
  "auto_appear": false,
  "objectives": [
    {
      "type": "ep_requirement",
      "required": 5000,
      "consume": true,
      "unlock": false
    }
  ],
  "rewards": [
    {
      "type": "command",
      "command": "/tensura race set @s tensura:kijin true"
    }
  ],
  "requirements": [
    {
      "type": "race",
      "race_id": "tensura:ogre"
    },
    {
      "type": "ep",
      "required_ep": 10000
    }
  ]
}
```

---

## 9. Testing Checklist

### Server-Side:
- [ ] Build WorldVoice successfully with new features
- [ ] Quests load from config files
- [ ] Daily quests refresh at midnight
- [ ] Weekly quests refresh on Monday
- [ ] Quest requirements checked on player join
- [ ] Admin commands work correctly
- [ ] KubeJS integration works (if using KubeJS)

### Client-Side:
- [ ] Build ArcadiaClientUtils with requirement support
- [ ] Quest requirements display in UI
- [ ] Locked quests show correctly
- [ ] Requirements update when player stats change

### Integration:
- [ ] Server assigns quests based on requirements
- [ ] Client displays quests with requirements
- [ ] Automatic quest appearance works
- [ ] Manual quest assignment (via commands) works

---

## 10. Future Enhancements (Not Implemented Yet)

1. **Quest Persistence**: Save quests to disk (currently resets on server restart)
2. **Soul Points Rewards**: Integration when soul points system exists
3. **Reset Counter Rewards**: Integration when reset counter system exists
4. **Quest Templates**: Pre-built quest templates for common patterns
5. **Quest Notifications**: Chat/title notifications for quest events
6. **Quest Leaderboards**: Track and display quest completion statistics
7. **Quest Reputation**: Faction/reputation system tied to quests
8. **Dynamic Quest Generation**: Procedurally generated quests

---

## Status: ✅ COMPLETE

All requested features have been implemented:
1. ✅ Daily/Weekly quest refresh system
2. ✅ Quest requirement system (EP, race, skills, awakening, quest chains)
3. ✅ JSON quest loader from config folder
4. ✅ KubeJS integration
5. ✅ Admin commands for quest management
6. ✅ Auto-appear quest system based on requirements
7. 🔄 Client-side requirement display (needs final UI integration)

**Next Step**: Test the system end-to-end and integrate requirement display in client UI.

**Author**: Claude (Anthropic)
**Date**: 2025-11-12
**Version**: 2.0-FEATURE-COMPLETE
