package org.faded.worldvoice.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.faded.worldvoice.Worldvoice;
import org.faded.worldvoice.quest.objective.*;
import org.faded.worldvoice.quest.requirement.*;
import org.faded.worldvoice.quest.reward.*;

import java.util.*;

/**
 * KubeJS integration for quest registration
 *
 * Usage in KubeJS:
 * ```javascript
 * WorldVoiceEvents.registerQuests(event => {
 *     event.create('my_quest_id')
 *         .title('My Quest Title')
 *         .description('Quest description')
 *         .category('DAILY')
 *         .autoAppear(true)
 *         .requireEP(1000)
 *         .requireRace('tensura:ogre')
 *         .objective('kill_mob', obj => {
 *             obj.mobType('minecraft:zombie')
 *             obj.required(10)
 *         })
 *         .reward('item', rew => {
 *             rew.item('minecraft:diamond')
 *             rew.count(5)
 *         })
 * })
 * ```
 */
public class KubeJSQuestRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<AdvancedQuestData> registeredQuests = new ArrayList<>();

    public static class QuestBuilder {
        private UUID questId;
        private String title = "Untitled Quest";
        private String description = "";
        private QuestTab category = QuestTab.ONE_TIME;
        private boolean autoAppear = true;
        private final List<QuestObjective> objectives = new ArrayList<>();
        private final List<QuestReward> rewards = new ArrayList<>();
        private final List<QuestRequirement> requirements = new ArrayList<>();

        public QuestBuilder(String questIdString) {

            this.questId = UUID.nameUUIDFromBytes(("worldvoice:" + questIdString).getBytes());
        }

        public QuestBuilder title(String title) {
            this.title = title;
            return this;
        }

        public QuestBuilder description(String description) {
            this.description = description;
            return this;
        }

        public QuestBuilder category(String category) {
            this.category = QuestTab.valueOf(category.toUpperCase());
            return this;
        }

        public QuestBuilder autoAppear(boolean autoAppear) {
            this.autoAppear = autoAppear;
            return this;
        }

        public QuestBuilder requireEP(double amount) {
            requirements.add(new org.faded.worldvoice.quest.requirement.EPRequirement(amount));
            return this;
        }

        public QuestBuilder requireRace(String raceId) {
            requirements.add(new org.faded.worldvoice.quest.requirement.RaceRequirement(new ResourceLocation(raceId)));
            return this;
        }

        public QuestBuilder requireSkill(String skillId) {
            requirements.add(new org.faded.worldvoice.quest.requirement.SkillRequirement(new ResourceLocation(skillId)));
            return this;
        }

        public QuestBuilder requireAwakening(String awakening) {
            AwakeningRequirement.AwakeningType type = AwakeningRequirement.AwakeningType.valueOf(awakening.toUpperCase());
            requirements.add(new AwakeningRequirement(type));
            return this;
        }

        public QuestBuilder requireQuestCompleted(String questIdString, String questName) {
            UUID requiredQuestId = UUID.nameUUIDFromBytes(("worldvoice:" + questIdString).getBytes());
            requirements.add(new CompletedQuestRequirement(requiredQuestId, questName));
            return this;
        }

        public QuestBuilder addKillMobObjective(String mobType, int required) {
            objectives.add(new KillMobObjective(new ResourceLocation(mobType), required, 0));
            return this;
        }

        public QuestBuilder addCollectItemObjective(String itemId, int required, boolean consume) {
            objectives.add(new CollectItemObjective(new ResourceLocation(itemId), required, 0, consume));
            return this;
        }

        public QuestBuilder addEPRequirementObjective(double required, boolean consume, boolean unlock) {
            objectives.add(new org.faded.worldvoice.quest.objective.EPRequirement(required, 0, consume, unlock));
            return this;
        }

        public QuestBuilder addEPGainObjective(double required) {
            objectives.add(new EPGainRequirement(required, 0, 0, false));
            return this;
        }

        public QuestBuilder addItemReward(String itemId, int count) {
            rewards.add(new ItemReward(new ResourceLocation(itemId), count));
            return this;
        }

        public QuestBuilder addCommandReward(String command) {
            rewards.add(new CommandReward(command));
            return this;
        }

        public QuestBuilder addSkillReward(String skillId) {
            rewards.add(new SkillReward(new ResourceLocation(skillId)));
            return this;
        }

        public QuestBuilder addEPLevelsReward(int levels) {
            rewards.add(new EPLevelsReward(levels));
            return this;
        }

        public QuestBuilder addMaxMagiculesReward(double amount) {
            rewards.add(new MaxMagiculesReward(amount));
            return this;
        }

        public QuestBuilder addMaxAuraReward(double amount) {
            rewards.add(new MaxAuraReward(amount));
            return this;
        }

        public QuestBuilder addExperienceReward(int levels) {
            rewards.add(new ExperienceReward(levels));
            return this;
        }

        public AdvancedQuestData build() {
            if (objectives.isEmpty()) {
                Worldvoice.LOGGER.warn("Quest {} has no objectives", title);
            }
            if (rewards.isEmpty()) {
                Worldvoice.LOGGER.warn("Quest {} has no rewards", title);
            }

            return new AdvancedQuestData(
                    questId,
                    title,
                    description,
                    category,
                    QuestStatus.AVAILABLE,
                    objectives,
                    rewards,
                    requirements,
                    autoAppear
            );
        }
    }

    public static QuestBuilder create(String questId) {
        return new QuestBuilder(questId);
    }

    public static void register(AdvancedQuestData quest) {
        registeredQuests.add(quest);
        Worldvoice.LOGGER.info("Registered quest from KubeJS: {}", quest.getTitle());
    }

    public static List<AdvancedQuestData> getRegisteredQuests() {
        return new ArrayList<>(registeredQuests);
    }

    public static void clear() {
        registeredQuests.clear();
    }

    public static void registerFromJson(String jsonString) {
        try {
            JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
            AdvancedQuestData quest = AdvancedQuestData.fromJson(json);
            register(quest);
        } catch (Exception e) {
            Worldvoice.LOGGER.error("Failed to register quest from JSON: {}", e.getMessage());
        }
    }
}
