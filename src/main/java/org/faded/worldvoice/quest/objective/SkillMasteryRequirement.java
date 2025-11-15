package org.faded.worldvoice.quest.objective;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.faded.worldvoice.quest.QuestDisplayUtil;

/**
 * Objective requiring the player to have mastered a specific skill
 */
public class SkillMasteryRequirement implements QuestObjective {
    private final ResourceLocation skillId;
    private boolean isMastered;
    private final String displayText;

    public SkillMasteryRequirement(ResourceLocation skillId, boolean isMastered) {
        this(skillId, isMastered, null);
    }

    public SkillMasteryRequirement(ResourceLocation skillId, boolean isMastered, String displayText) {
        this.skillId = skillId;
        this.isMastered = isMastered;
        this.displayText = QuestDisplayUtil.normalize(displayText);
    }

    public ResourceLocation getSkillId() {
        return skillId;
    }

    public void setMastered(boolean mastered) {
        this.isMastered = mastered;
    }

    @Override
    public QuestObjectiveType<?> getType() {
        return QuestObjectiveType.SKILL_MASTERY_REQUIREMENT;
    }

    @Override
    public float getProgress() {
        return isMastered ? 1.0f : 0.0f;
    }

    @Override
    public boolean isCompleted() {
        return isMastered;
    }

    @Override
    public Component getProgressDisplay() {
        String status = isMastered ? "✓" : "✗";
        String label = displayText != null ? displayText : "Master Skill: " + skillId.getPath();
        return Component.literal(String.format("%s %s", label, status));
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "skill_mastery_requirement");
        json.addProperty("skill_id", skillId.toString());
        json.addProperty("is_mastered", isMastered);
        QuestDisplayUtil.addDisplayProperty(json, displayText);
        return json;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeResourceLocation(skillId);
        buf.writeBoolean(isMastered);
        QuestDisplayUtil.writeDisplay(buf, displayText);
    }

    @Override
    public int getRequiredCount() {
        return 1;
    }

    @Override
    public int getCurrentCount() {
        return isMastered ? 1 : 0;
    }

    public static SkillMasteryRequirement fromJson(JsonObject json) {
        ResourceLocation skillId = new ResourceLocation(json.get("skill_id").getAsString());
        boolean isMastered = json.has("is_mastered") && json.get("is_mastered").getAsBoolean();
        String display = QuestDisplayUtil.readDisplay(json);
        return new SkillMasteryRequirement(skillId, isMastered, display);
    }

    public static SkillMasteryRequirement fromNetwork(FriendlyByteBuf buf) {
        ResourceLocation skillId = buf.readResourceLocation();
        boolean isMastered = buf.readBoolean();
        String display = QuestDisplayUtil.readDisplay(buf);
        return new SkillMasteryRequirement(skillId, isMastered, display);
    }
}
