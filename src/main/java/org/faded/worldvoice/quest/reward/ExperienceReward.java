package org.faded.worldvoice.quest.reward;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.faded.worldvoice.quest.QuestDisplayUtil;

public class ExperienceReward implements QuestReward {
    private final int levels;
    private final String displayText;

    public ExperienceReward(int levels) {
        this(levels, null);
    }

    public ExperienceReward(int levels, String displayText) {
        this.levels = levels;
        this.displayText = QuestDisplayUtil.normalize(displayText);
    }

    public int getLevels() {
        return levels;
    }

    @Override
    public QuestRewardType<?> getType() {
        return QuestRewardType.EXPERIENCE;
    }

    @Override
    public Component getDisplayComponent() {
        String text = displayText != null ? displayText :
                String.format("+%d Experience Levels", levels);
        return Component.literal(text);
    }

    @Override
    public String getDisplayString() {
        return displayText != null ? displayText :
                String.format("+%d XP Levels", levels);
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "experience");
        json.addProperty("levels", levels);
        QuestDisplayUtil.addDisplayProperty(json, displayText);
        return json;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeInt(levels);
        QuestDisplayUtil.writeDisplay(buf, displayText);
    }

    public static ExperienceReward fromJson(JsonObject json) {
        int levels = json.get("levels").getAsInt();
        String display = QuestDisplayUtil.readDisplay(json);
        return new ExperienceReward(levels, display);
    }

    public static ExperienceReward fromNetwork(FriendlyByteBuf buf) {
        int levels = buf.readInt();
        String display = QuestDisplayUtil.readDisplay(buf);
        return new ExperienceReward(levels, display);
    }
}
