package org.faded.worldvoice.quest.requirement;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.faded.worldvoice.quest.QuestDisplayUtil;
import org.faded.worldvoice.quest.ServerQuestManager;

import java.util.UUID;


public class CompletedQuestRequirement implements QuestRequirement {
    private final UUID requiredQuestId;
    private final String questName;
    private final String displayText;

    public CompletedQuestRequirement(UUID requiredQuestId, String questName) {
        this(requiredQuestId, questName, null);
    }

    public CompletedQuestRequirement(UUID requiredQuestId, String questName, String displayText) {
        this.requiredQuestId = requiredQuestId;
        this.questName = questName;
        this.displayText = QuestDisplayUtil.normalize(displayText);
    }

    @Override
    public boolean meetsRequirement(ServerPlayer player) {
        var quest = ServerQuestManager.getQuest(player.getUUID(), requiredQuestId);
        return quest != null && quest.isCompleted();
    }

    @Override
    public String getDisplayString() {
        return displayText != null ? displayText : "Requires completion of: " + questName;
    }

    @Override
    public String getType() {
        return "quest_completed";
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "quest_completed");
        json.addProperty("quest_id", requiredQuestId.toString());
        json.addProperty("quest_name", questName);
        QuestDisplayUtil.addDisplayProperty(json, displayText);
        return json;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeUUID(requiredQuestId);
        buf.writeUtf(questName);
        QuestDisplayUtil.writeDisplay(buf, displayText);
    }

    public static CompletedQuestRequirement fromJson(JsonObject json) {
        UUID questId = UUID.fromString(json.get("quest_id").getAsString());
        String questName = json.get("quest_name").getAsString();
        String display = QuestDisplayUtil.readDisplay(json);
        return new CompletedQuestRequirement(questId, questName, display);
    }

    public static CompletedQuestRequirement fromNetwork(FriendlyByteBuf buf) {
        UUID questId = buf.readUUID();
        String questName = buf.readUtf();
        String display = QuestDisplayUtil.readDisplay(buf);
        return new CompletedQuestRequirement(questId, questName, display);
    }

    public UUID getRequiredQuestId() {
        return requiredQuestId;
    }
}
