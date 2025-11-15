package org.faded.worldvoice.quest.objective;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.faded.worldvoice.quest.QuestDisplayUtil;

/**
 * Objective requiring the player to kill a certain number of specific mobs
 */
public class KillMobObjective implements QuestObjective {
    private final ResourceLocation mobType;
    private final int requiredKills;
    private int currentKills;
    private final String displayText;

    public KillMobObjective(ResourceLocation mobType, int requiredKills, int currentKills) {
        this(mobType, requiredKills, currentKills, null);
    }

    public KillMobObjective(ResourceLocation mobType, int requiredKills, int currentKills, String displayText) {
        this.mobType = mobType;
        this.requiredKills = requiredKills;
        this.currentKills = currentKills;
        this.displayText = QuestDisplayUtil.normalize(displayText);
    }

    public ResourceLocation getMobType() {
        return mobType;
    }

    public void setCurrentKills(int kills) {
        this.currentKills = Math.min(kills, requiredKills);
    }

    @Override
    public QuestObjectiveType<?> getType() {
        return QuestObjectiveType.KILL_MOB;
    }

    @Override
    public float getProgress() {
        return (float) currentKills / requiredKills;
    }

    @Override
    public boolean isCompleted() {
        return currentKills >= requiredKills;
    }

    @Override
    public Component getProgressDisplay() {
        String label = displayText != null ? displayText :
                "Kill " + mobType.getPath().replace('_', ' ');
        return Component.literal(String.format("%s: %d/%d", label, currentKills, requiredKills));
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "kill_mob");
        json.addProperty("mob_type", mobType.toString());
        json.addProperty("required", requiredKills);
        json.addProperty("current", currentKills);
        QuestDisplayUtil.addDisplayProperty(json, displayText);
        return json;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeResourceLocation(mobType);
        buf.writeInt(requiredKills);
        buf.writeInt(currentKills);
        QuestDisplayUtil.writeDisplay(buf, displayText);
    }

    @Override
    public int getRequiredCount() {
        return requiredKills;
    }

    @Override
    public int getCurrentCount() {
        return currentKills;
    }

    public static KillMobObjective fromJson(JsonObject json) {
        ResourceLocation mobType = new ResourceLocation(json.get("mob_type").getAsString());
        int required = json.get("required").getAsInt();
        int current = json.has("current") ? json.get("current").getAsInt() : 0;
        String display = QuestDisplayUtil.readDisplay(json);
        return new KillMobObjective(mobType, required, current, display);
    }

    public static KillMobObjective fromNetwork(FriendlyByteBuf buf) {
        ResourceLocation mobType = buf.readResourceLocation();
        int required = buf.readInt();
        int current = buf.readInt();
        String display = QuestDisplayUtil.readDisplay(buf);
        return new KillMobObjective(mobType, required, current, display);
    }
}
