package org.faded.worldvoice.quest.requirement;

import com.github.manasmods.manascore.api.skills.ManasSkill;
import com.github.manasmods.manascore.api.skills.SkillAPI;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.faded.worldvoice.quest.QuestDisplayUtil;

public class SkillRequirement implements QuestRequirement {
    private final ResourceLocation skillId;
    private final String displayText;

    public SkillRequirement(ResourceLocation skillId) {
        this(skillId, null);
    }

    public SkillRequirement(ResourceLocation skillId, String displayText) {
        this.skillId = skillId;
        this.displayText = QuestDisplayUtil.normalize(displayText);
    }

    @Override
    public boolean meetsRequirement(ServerPlayer player) {
        ManasSkill skill = SkillAPI.getSkillRegistry().getValue(skillId);
        if (skill == null) return false;
        return SkillAPI.getSkillsFrom(player).getSkill(skill).isPresent();
    }

    @Override
    public String getDisplayString() {
        return displayText != null ? displayText : "Requires skill: " + skillId.getPath();
    }

    @Override
    public String getType() {
        return "skill";
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

    public static SkillRequirement fromJson(JsonObject json) {
        ResourceLocation skillId = new ResourceLocation(json.get("skill_id").getAsString());
        String display = QuestDisplayUtil.readDisplay(json);
        return new SkillRequirement(skillId, display);
    }

    public static SkillRequirement fromNetwork(FriendlyByteBuf buf) {
        ResourceLocation skillId = buf.readResourceLocation();
        String display = QuestDisplayUtil.readDisplay(buf);
        return new SkillRequirement(skillId, display);
    }

    public ResourceLocation getSkillId() {
        return skillId;
    }
}
