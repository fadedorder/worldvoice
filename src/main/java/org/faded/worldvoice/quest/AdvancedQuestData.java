package org.faded.worldvoice.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.faded.worldvoice.quest.objective.QuestObjective;
import org.faded.worldvoice.quest.objective.QuestObjectiveType;
import org.faded.worldvoice.quest.requirement.QuestRequirement;
import org.faded.worldvoice.quest.requirement.QuestRequirementType;
import org.faded.worldvoice.quest.reward.QuestReward;
import org.faded.worldvoice.quest.reward.QuestRewardType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Advanced quest data structure compatible with tensurajs-style quests
 * Supports modular objectives and rewards
 */
public class AdvancedQuestData {
    private final UUID questId;
    private final String title;
    private final String description;
    private final QuestTab category;
    private QuestStatus status;
    private final List<QuestObjective> objectives;
    private final List<QuestReward> rewards;
    private final List<QuestRequirement> requirements;
    private boolean tracked;
    private long completedTimestamp;
    private long lastResetTimestamp;
    private boolean autoAppear; // If true, quest appears automatically when requirements are met

    public AdvancedQuestData(UUID questId, String title, String description, QuestTab category,
                            QuestStatus status, List<QuestObjective> objectives,
                            List<QuestReward> rewards, List<QuestRequirement> requirements, boolean autoAppear) {
        this.questId = questId;
        this.title = title;
        this.description = description;
        this.category = category;
        this.status = status;
        this.objectives = objectives != null ? objectives : new ArrayList<>();
        this.rewards = rewards != null ? rewards : new ArrayList<>();
        this.requirements = requirements != null ? requirements : new ArrayList<>();
        this.tracked = false;
        this.completedTimestamp = 0;
        this.lastResetTimestamp = 0;
        this.autoAppear = autoAppear;
    }

    // Legacy constructor for backwards compatibility
    public AdvancedQuestData(UUID questId, String title, String description, QuestTab category,
                            QuestStatus status, List<QuestObjective> objectives,
                            List<QuestReward> rewards) {
        this(questId, title, description, category, status, objectives, rewards, new ArrayList<>(), true);
    }

    // Getters
    public UUID getQuestId() {
        return questId;
    }

    public String getTitle() {
        return title;
    }

    public Component getTitleComponent() {
        return Component.literal(title);
    }

    public String getDescription() {
        return description;
    }

    public QuestTab getCategory() {
        return category;
    }

    public QuestStatus getStatus() {
        return status;
    }

    public void setStatus(QuestStatus status) {
        this.status = status;
    }

    public List<QuestObjective> getObjectives() {
        return objectives;
    }

    public List<QuestReward> getRewards() {
        return rewards;
    }

    public List<QuestRequirement> getRequirements() {
        return requirements;
    }

    public boolean isAutoAppear() {
        return autoAppear;
    }

    public void setAutoAppear(boolean autoAppear) {
        this.autoAppear = autoAppear;
    }

    public boolean isTracked() {
        return tracked;
    }

    public void setTracked(boolean tracked) {
        this.tracked = tracked;
    }

    public long getCompletedTimestamp() {
        return completedTimestamp;
    }

    public void setCompletedTimestamp(long timestamp) {
        this.completedTimestamp = timestamp;
    }

    public long getLastResetTimestamp() {
        return lastResetTimestamp;
    }

    public void setLastResetTimestamp(long timestamp) {
        this.lastResetTimestamp = timestamp;
    }

    // Quest state checks
    public boolean canStart() {
        return status == QuestStatus.AVAILABLE;
    }

    public boolean canCancel() {
        return status == QuestStatus.ACTIVE || status == QuestStatus.TRACKED;
    }

    public boolean isActive() {
        return status == QuestStatus.ACTIVE || status == QuestStatus.TRACKED;
    }

    public boolean isCompleted() {
        return status == QuestStatus.COMPLETED;
    }

    /**
     * Check if all requirements are met by the player
     */
    public boolean areRequirementsMet(net.minecraft.server.level.ServerPlayer player) {
        return requirements.stream().allMatch(req -> req.meetsRequirement(player));
    }

    /**
     * Check if all objectives are completed
     */
    public boolean areObjectivesCompleted() {
        return objectives.stream().allMatch(QuestObjective::isCompleted);
    }

    /**
     * Get overall progress (0.0 to 1.0)
     */
    public float getOverallProgress() {
        if (objectives.isEmpty()) return 0.0f;
        float totalProgress = 0.0f;
        for (QuestObjective objective : objectives) {
            totalProgress += objective.getProgress();
        }
        return totalProgress / objectives.size();
    }

    // Serialization
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("quest_id", questId.toString());
        json.addProperty("title", title);
        json.addProperty("description", description);
        json.addProperty("category", category.name());
        json.addProperty("status", status.name());
        json.addProperty("tracked", tracked);
        json.addProperty("completed_timestamp", completedTimestamp);
        json.addProperty("last_reset_timestamp", lastResetTimestamp);
        json.addProperty("auto_appear", autoAppear);

        // Serialize objectives
        JsonArray objectivesArray = new JsonArray();
        for (QuestObjective objective : objectives) {
            objectivesArray.add(objective.toJson());
        }
        json.add("objectives", objectivesArray);

        // Serialize rewards
        JsonArray rewardsArray = new JsonArray();
        for (QuestReward reward : rewards) {
            rewardsArray.add(reward.toJson());
        }
        json.add("rewards", rewardsArray);

        // Serialize requirements
        JsonArray requirementsArray = new JsonArray();
        for (QuestRequirement requirement : requirements) {
            requirementsArray.add(requirement.toJson());
        }
        json.add("requirements", requirementsArray);

        return json;
    }

    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeUUID(questId);
        buf.writeUtf(title);
        buf.writeUtf(description);
        buf.writeEnum(category);
        buf.writeEnum(status);
        buf.writeBoolean(tracked);
        buf.writeLong(completedTimestamp);
        buf.writeLong(lastResetTimestamp);

        // Write objectives
        buf.writeInt(objectives.size());
        for (QuestObjective objective : objectives) {
            buf.writeResourceLocation(objective.getType().getId());
            objective.toNetwork(buf);
        }

        // Write rewards
        buf.writeInt(rewards.size());
        for (QuestReward reward : rewards) {
            buf.writeResourceLocation(reward.getType().getId());
            reward.toNetwork(buf);
        }

        buf.writeBoolean(autoAppear);

        // Write requirements
        buf.writeInt(requirements.size());
        for (QuestRequirement requirement : requirements) {
            QuestRequirementType.toNetwork(requirement, buf);
        }
    }

    // Deserialization
    public static AdvancedQuestData fromJson(JsonObject json) {
        // Support both UUID strings and namespaced string IDs
        UUID questId;
        String questIdStr = json.get("quest_id").getAsString();
        try {
            questId = UUID.fromString(questIdStr);
        } catch (IllegalArgumentException e) {
            // Not a UUID, generate deterministic UUID from string
            questId = UUID.nameUUIDFromBytes(questIdStr.getBytes());
        }

        String title = json.get("title").getAsString();
        String description = json.get("description").getAsString();
        QuestTab category = QuestTab.valueOf(json.get("category").getAsString());
        QuestStatus status = QuestStatus.valueOf(json.get("status").getAsString());
        boolean tracked = json.has("tracked") && json.get("tracked").getAsBoolean();
        long completedTimestamp = json.has("completed_timestamp") ? json.get("completed_timestamp").getAsLong() : 0;
        long lastResetTimestamp = json.has("last_reset_timestamp") ? json.get("last_reset_timestamp").getAsLong() : 0;
        boolean autoAppear = !json.has("auto_appear") || json.get("auto_appear").getAsBoolean(); // Default true

        // Deserialize objectives
        List<QuestObjective> objectives = new ArrayList<>();
        if (json.has("objectives")) {
            JsonArray objectivesArray = json.getAsJsonArray("objectives");
            for (int i = 0; i < objectivesArray.size(); i++) {
                JsonObject objJson = objectivesArray.get(i).getAsJsonObject();
                String typeStr = objJson.get("type").getAsString();
                QuestObjectiveType<?> type = QuestObjectiveType.getType(new ResourceLocation("arcclient", typeStr));
                if (type != null) {
                    objectives.add(type.fromJson(objJson));
                }
            }
        }

        // Deserialize rewards
        List<QuestReward> rewards = new ArrayList<>();
        if (json.has("rewards")) {
            JsonArray rewardsArray = json.getAsJsonArray("rewards");
            for (int i = 0; i < rewardsArray.size(); i++) {
                JsonObject rewJson = rewardsArray.get(i).getAsJsonObject();
                String typeStr = rewJson.get("type").getAsString();
                QuestRewardType<?> type = QuestRewardType.getType(new ResourceLocation("arcclient", typeStr));
                if (type != null) {
                    rewards.add(type.fromJson(rewJson));
                }
            }
        }

        // Deserialize requirements
        List<QuestRequirement> requirements = new ArrayList<>();
        if (json.has("requirements")) {
            JsonArray requirementsArray = json.getAsJsonArray("requirements");
            for (int i = 0; i < requirementsArray.size(); i++) {
                JsonObject reqJson = requirementsArray.get(i).getAsJsonObject();
                try {
                    requirements.add(QuestRequirementType.fromJson(reqJson));
                } catch (Exception e) {
                    // Skip invalid requirements
                }
            }
        }

        AdvancedQuestData questData = new AdvancedQuestData(questId, title, description, category,
                status, objectives, rewards, requirements, autoAppear);
        questData.setTracked(tracked);
        questData.setCompletedTimestamp(completedTimestamp);
        questData.setLastResetTimestamp(lastResetTimestamp);
        return questData;
    }

    public static AdvancedQuestData fromNetwork(FriendlyByteBuf buf) {
        UUID questId = buf.readUUID();
        String title = buf.readUtf();
        String description = buf.readUtf();
        QuestTab category = buf.readEnum(QuestTab.class);
        QuestStatus status = buf.readEnum(QuestStatus.class);
        boolean tracked = buf.readBoolean();
        long completedTimestamp = buf.readLong();
        long lastResetTimestamp = buf.readLong();

        // Read objectives
        List<QuestObjective> objectives = new ArrayList<>();
        int objectiveCount = buf.readInt();
        for (int i = 0; i < objectiveCount; i++) {
            ResourceLocation typeId = buf.readResourceLocation();
            QuestObjectiveType<?> type = QuestObjectiveType.getType(typeId);
            if (type != null) {
                objectives.add(type.fromNetwork(buf));
            }
        }

        // Read rewards
        List<QuestReward> rewards = new ArrayList<>();
        int rewardCount = buf.readInt();
        for (int i = 0; i < rewardCount; i++) {
            ResourceLocation typeId = buf.readResourceLocation();
            QuestRewardType<?> type = QuestRewardType.getType(typeId);
            if (type != null) {
                rewards.add(type.fromNetwork(buf));
            }
        }

        boolean autoAppear = buf.readBoolean();

        // Read requirements
        List<QuestRequirement> requirements = new ArrayList<>();
        int requirementCount = buf.readInt();
        for (int i = 0; i < requirementCount; i++) {
            try {
                requirements.add(QuestRequirementType.fromNetwork(buf));
            } catch (Exception e) {
                // Skip invalid requirements
            }
        }

        AdvancedQuestData questData = new AdvancedQuestData(questId, title, description, category,
                status, objectives, rewards, requirements, autoAppear);
        questData.setTracked(tracked);
        questData.setCompletedTimestamp(completedTimestamp);
        questData.setLastResetTimestamp(lastResetTimestamp);
        return questData;
    }
}
