package org.faded.worldvoice.quest.reward;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.faded.worldvoice.quest.QuestDisplayUtil;

public class SoulPointsReward implements QuestReward {
    private final int points;
    private final String displayText;

    public SoulPointsReward(int points) {
        this(points, null);
    }

    public SoulPointsReward(int points, String displayText) {
        this.points = points;
        this.displayText = QuestDisplayUtil.normalize(displayText);
    }

    public int getPoints() {
        return points;
    }

    @Override
    public QuestRewardType<?> getType() {
        return QuestRewardType.SOUL_POINTS;
    }

    @Override
    public Component getDisplayComponent() {
        String text = displayText != null ? displayText :
                String.format("+%d Soul Points", points);
        return Component.literal(text);
    }

    @Override
    public String getDisplayString() {
        return displayText != null ? displayText :
                String.format("+%d Soul Points", points);
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "soul_points");
        json.addProperty("points", points);
        QuestDisplayUtil.addDisplayProperty(json, displayText);
        return json;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeInt(points);
        QuestDisplayUtil.writeDisplay(buf, displayText);
    }

    public static SoulPointsReward fromJson(JsonObject json) {
        int points = json.get("points").getAsInt();
        String display = QuestDisplayUtil.readDisplay(json);
        return new SoulPointsReward(points, display);
    }

    public static SoulPointsReward fromNetwork(FriendlyByteBuf buf) {
        int points = buf.readInt();
        String display = QuestDisplayUtil.readDisplay(buf);
        return new SoulPointsReward(points, display);
    }
}
