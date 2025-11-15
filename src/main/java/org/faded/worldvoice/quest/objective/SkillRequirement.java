package org.faded.worldvoice.quest.objective;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.faded.worldvoice.quest.QuestDisplayUtil;

/**
 * Objective requiring the player to have a specific skill
 */
public class SkillRequirement implements QuestObjective {
    private final ResourceLocation skillId;
    private boolean hasSkill;
    private final String displayText;

    public SkillRequirement(ResourceLocation skillId, boolean hasSkill) {
        this(skillId, hasSkill, null);
    }

    public SkillRequirement(ResourceLocation skillId, boolean hasSkill, String displayText) {
        this.skillId = skillId;
        this.hasSkill = hasSkill;
        this.displayText = QuestDisplayUtil.normalize(displayText);
    }

    public ResourceLocation getSkillId() {
        return skillId;
    }

    public void setHasSkill(boolean hasSkill) {
        this.hasSkill = hasSkill;
    }

    @Override
    public QuestObjectiveType<?> getType() {
        return QuestObjectiveType.SKILL_REQUIREMENT;
    }

    @Override
    public float getProgress() {
        return hasSkill ? 1.0f : 0.0f;
    }

    @Override
    public boolean isCompleted() {
        return hasSkill;
    }

    @Override
    public Component getProgressDisplay() {
        String status = hasSkill ? "✓" : "✗";
        String label = displayText != null ? displayText : "Skill: " + skillId.getPath();
        return Component.literal(String.format("%s %s", label, status));
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "skill_requirement");
        json.addProperty("skill_id", skillId.toString());
        json.addProperty("has_skill", hasSkill);
        QuestDisplayUtil.addDisplayProperty(json, displayText);
        return json;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeResourceLocation(skillId);
        buf.writeBoolean(hasSkill);
        QuestDisplayUtil.writeDisplay(buf, displayText);
    }

    @Override
    public int getRequiredCount() {
        return 1;
    }

    @Override
    public int getCurrentCount() {
        return hasSkill ? 1 : 0;
    }

    public static SkillRequirement fromJson(JsonObject json) {
        ResourceLocation skillId = new ResourceLocation(json.get("skill_id").getAsString());
        boolean hasSkill = json.has("has_skill") && json.get("has_skill").getAsBoolean();
        String display = QuestDisplayUtil.readDisplay(json);
        return new SkillRequirement(skillId, hasSkill, display);
    }

    public static SkillRequirement fromNetwork(FriendlyByteBuf buf) {
        ResourceLocation skillId = buf.readResourceLocation();
        boolean hasSkill = buf.readBoolean();
        String display = QuestDisplayUtil.readDisplay(buf);
        return new SkillRequirement(skillId, hasSkill, display);
    }
}
