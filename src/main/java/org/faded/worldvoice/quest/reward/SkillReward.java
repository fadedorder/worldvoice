package org.faded.worldvoice.quest.reward;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.faded.worldvoice.quest.QuestDisplayUtil;

public class SkillReward implements QuestReward {
    private final ResourceLocation skillId;
    private final String displayText;

    public SkillReward(ResourceLocation skillId) {
        this(skillId, null);
    }

    public SkillReward(ResourceLocation skillId, String displayText) {
        this.skillId = skillId;
        this.displayText = QuestDisplayUtil.normalize(displayText);
    }

    public ResourceLocation getSkillId() {
        return skillId;
    }

    @Override
    public QuestRewardType<?> getType() {
        return QuestRewardType.SKILL;
    }

    @Override
    public Component getDisplayComponent() {
        String text = displayText != null ? displayText : "Skill: " + skillId.getPath();
        return Component.literal(text);
    }

    @Override
    public String getDisplayString() {
        return displayText != null ? displayText : "Skill: " + skillId.getPath();
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "skill");
        json.addProperty("skill_id", skillId.toString());
        QuestDisplayUtil.addDisplayProperty(json, displayText);
        return json;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeResourceLocation(skillId);
        QuestDisplayUtil.writeDisplay(buf, displayText);
    }

    public static SkillReward fromJson(JsonObject json) {
        ResourceLocation skillId = new ResourceLocation(json.get("skill_id").getAsString());
        String display = QuestDisplayUtil.readDisplay(json);
        return new SkillReward(skillId, display);
    }

    public static SkillReward fromNetwork(FriendlyByteBuf buf) {
        ResourceLocation skillId = buf.readResourceLocation();
        String display = QuestDisplayUtil.readDisplay(buf);
        return new SkillReward(skillId, display);
    }
}
