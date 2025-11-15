package org.faded.worldvoice.quest.reward;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.faded.worldvoice.quest.QuestDisplayUtil;

public class EPLevelsReward implements QuestReward {
    private final int levels;
    private final String displayText;

    public EPLevelsReward(int levels) {
        this(levels, null);
    }

    public EPLevelsReward(int levels, String displayText) {
        this.levels = levels;
        this.displayText = QuestDisplayUtil.normalize(displayText);
    }

    public int getLevels() {
        return levels;
    }

    @Override
    public QuestRewardType<?> getType() {
        return QuestRewardType.EP_LEVELS;
    }

    @Override
    public Component getDisplayComponent() {
        String text = displayText != null ? displayText :
                String.format("+%d EP Levels", levels);
        return Component.literal(text);
    }

    @Override
    public String getDisplayString() {
        return displayText != null ? displayText :
                String.format("+%d EP Levels", levels);
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "ep_levels");
        json.addProperty("levels", levels);
        QuestDisplayUtil.addDisplayProperty(json, displayText);
        return json;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeInt(levels);
        QuestDisplayUtil.writeDisplay(buf, displayText);
    }

    public static EPLevelsReward fromJson(JsonObject json) {
        int levels = json.get("levels").getAsInt();
        String display = QuestDisplayUtil.readDisplay(json);
        return new EPLevelsReward(levels, display);
    }

    public static EPLevelsReward fromNetwork(FriendlyByteBuf buf) {
        int levels = buf.readInt();
        String display = QuestDisplayUtil.readDisplay(buf);
        return new EPLevelsReward(levels, display);
    }
}
