// Example KubeJS quest registration script
// Place this file in kubejs/server_scripts/ folder

// IMPORTANT: To use this, you need to call the Java methods directly from KubeJS
// This is a documentation/example file showing how to register quests programmatically

/* Example 1: Simple daily quest */
function registerDailyMonsterHunt() {
    let builder = Java.type('org.faded.worldvoice.quest.KubeJSQuestRegistry').create('daily_monster_hunt');

    let quest = builder
        .title('Daily Monster Hunt')
        .description('Defeat 20 zombies to earn rewards')
        .category('DAILY')
        .autoAppear(true)
        .addKillMobObjective('minecraft:zombie', 20)
        .addItemReward('minecraft:diamond', 3)
        .addExperienceReward(5)
        .build();

    Java.type('org.faded.worldvoice.quest.KubeJSQuestRegistry').register(quest);
}

/* Example 2: Weekly gather quest */
function registerWeeklyGathering() {
    let builder = Java.type('org.faded.worldvoice.quest.KubeJSQuestRegistry').create('weekly_gathering');

    let quest = builder
        .title('Weekly Resource Gathering')
        .description('Collect valuable resources')
        .category('WEEKLY')
        .autoAppear(true)
        .addCollectItemObjective('minecraft:diamond', 16, false)  // Don't consume
        .addCollectItemObjective('minecraft:iron_ingot', 64, false)
        .addItemReward('minecraft:netherite_ingot', 2)
        .addEPLevelsReward(10)
        .build();

    Java.type('org.faded.worldvoice.quest.KubeJSQuestRegistry').register(quest);
}

/* Example 3: Special quest with EP requirement */
function registerSpecialTraining() {
    let builder = Java.type('org.faded.worldvoice.quest.KubeJSQuestRegistry').create('special_training');

    let quest = builder
        .title('Advanced Training')
        .description('Train and gain EP')
        .category('SPECIAL')
        .autoAppear(false)  // Won't appear automatically
        .requireEP(5000)  // Requires 5000 EP to unlock
        .addEPGainObjective(1000)  // Must gain 1000 EP
        .addKillMobObjective('minecraft:wither_skeleton', 50)
        .addEPLevelsReward(20)
        .addSkillReward('tensura:unique_skill_predator')
        .build();

    Java.type('org.faded.worldvoice.quest.KubeJSQuestRegistry').register(quest);
}

/* Example 4: Evolution quest with race requirement */
function registerEvolutionQuest() {
    let builder = Java.type('org.faded.worldvoice.quest.KubeJSQuestRegistry').create('ogre_evolution');

    let quest = builder
        .title('Path to Kijin')
        .description('Evolve from Ogre to Kijin')
        .category('EVOLUTION')
        .autoAppear(true)
        .requireRace('tensura:ogre')  // Must be an Ogre
        .requireEP(10000)
        .addEPRequirementObjective(5000, true, false)  // Consume 5000 EP
        .addCollectItemObjective('minecraft:nether_star', 1, true)  // Consume nether star
        .addCommandReward('/tensura race set @s tensura:kijin true')  // Set race to Kijin
        .addMaxMagiculesReward(10000)
        .addMaxAuraReward(10000)
        .build();

    Java.type('org.faded.worldvoice.quest.KubeJSQuestRegistry').register(quest);
}

/* Example 5: Quest chain with prerequisite */
function registerQuestChain() {
    // First quest in chain
    let builder1 = Java.type('org.faded.worldvoice.quest.KubeJSQuestRegistry').create('chain_quest_1');

    let quest1 = builder1
        .title('The Beginning')
        .description('Start your journey')
        .category('ONE_TIME')
        .autoAppear(true)
        .addKillMobObjective('minecraft:zombie', 10)
        .addItemReward('minecraft:iron_sword', 1)
        .build();

    Java.type('org.faded.worldvoice.quest.KubeJSQuestRegistry').register(quest1);

    // Second quest - requires first to be completed
    let builder2 = Java.type('org.faded.worldvoice.quest.KubeJSQuestRegistry').create('chain_quest_2');

    let quest2 = builder2
        .title('The Journey Continues')
        .description('Take on a greater challenge')
        .category('ONE_TIME')
        .autoAppear(true)
        .requireQuestCompleted('chain_quest_1', 'The Beginning')  // Requires previous quest
        .addKillMobObjective('minecraft:skeleton', 20)
        .addKillMobObjective('minecraft:creeper', 15)
        .addItemReward('minecraft:diamond_sword', 1)
        .addExperienceReward(10)
        .build();

    Java.type('org.faded.worldvoice.quest.KubeJSQuestRegistry').register(quest2);
}

/* Example 6: Awakening quest */
function registerAwakeningQuest() {
    let builder = Java.type('org.faded.worldvoice.quest.KubeJSQuestRegistry').create('true_hero_awakening');

    let quest = builder
        .title('Awakening of the True Hero')
        .description('Awaken as a True Hero')
        .category('EVOLUTION')
        .autoAppear(false)
        .requireEP(50000)
        .requireAwakening('HERO_SEED')  // Must have Hero Seed
        .addEPGainObjective(10000)
        .addKillMobObjective('minecraft:wither', 3)
        .addCollectItemObjective('minecraft:dragon_egg', 1, true)
        .addCommandReward('/tensura awakening set @s true_hero true')
        .addMaxMagiculesReward(50000)
        .addMaxAuraReward(50000)
        .addSkillReward('tensura:ultimate_skill_hope_sariel')
        .build();

    Java.type('org.faded.worldvoice.quest.KubeJSQuestRegistry').register(quest);
}

/* Example 7: Simple JSON registration */
function registerQuestFromJSON() {
    let questJson = JSON.stringify({
        "quest_id": "worldvoice:json_example_quest",
        "title": "Quest from JSON",
        "description": "This quest was registered using JSON",
        "category": "DAILY",
        "status": "AVAILABLE",
        "auto_appear": true,
        "objectives": [
            {
                "type": "kill_mob",
                "display": "Put down 10 zombies",
                "mob_type": "minecraft:zombie",
                "required": 10,
                "current": 0
            }
        ],
        "rewards": [
            {
                "type": "item",
                "display": "Reward: Diamonds",
                "item_id": "minecraft:diamond",
                "count": 5
            }
        ],
        "requirements": []
    });

    Java.type('org.faded.worldvoice.quest.KubeJSQuestRegistry').registerFromJson(questJson);
}

// Register all quests
registerDailyMonsterHunt();
registerWeeklyGathering();
registerSpecialTraining();
registerEvolutionQuest();
registerQuestChain();
registerAwakeningQuest();
registerQuestFromJSON();
