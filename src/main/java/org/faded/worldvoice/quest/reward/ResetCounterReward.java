package org.faded.worldvoice.quest.reward;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.faded.worldvoice.quest.QuestDisplayUtil;

public class ResetCounterReward implements QuestReward {
    private final int amount;
    private final String displayText;

    public ResetCounterReward(int amount) {
        this(amount, null);
    }

    public ResetCounterReward(int amount, String displayText) {
        this.amount = amount;
        this.displayText = QuestDisplayUtil.normalize(displayText);
    }

    public int getAmount() {
        return amount;
    }

    @Override
    public QuestRewardType<?> getType() {
        return QuestRewardType.RESET_COUNTER;
    }

    @Override
    public Component getDisplayComponent() {
        String text = displayText != null ? displayText :
                String.format("+%d Reset Counter", amount);
        return Component.literal(text);
    }

    @Override
    public String getDisplayString() {
        return displayText != null ? displayText :
                String.format("+%d Reset Counter", amount);
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "reset_counter");
        json.addProperty("amount", amount);
        QuestDisplayUtil.addDisplayProperty(json, displayText);
        return json;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeInt(amount);
        QuestDisplayUtil.writeDisplay(buf, displayText);
    }

    public static ResetCounterReward fromJson(JsonObject json) {
        int amount = json.get("amount").getAsInt();
        String display = QuestDisplayUtil.readDisplay(json);
        return new ResetCounterReward(amount, display);
    }

    public static ResetCounterReward fromNetwork(FriendlyByteBuf buf) {
        int amount = buf.readInt();
        String display = QuestDisplayUtil.readDisplay(buf);
        return new ResetCounterReward(amount, display);
    }
}
