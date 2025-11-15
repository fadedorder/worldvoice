package org.faded.worldvoice.quest.reward;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.faded.worldvoice.quest.QuestDisplayUtil;

public class MaxAuraReward implements QuestReward {
    private final double amount;
    private final String displayText;

    public MaxAuraReward(double amount) {
        this(amount, null);
    }

    public MaxAuraReward(double amount, String displayText) {
        this.amount = amount;
        this.displayText = QuestDisplayUtil.normalize(displayText);
    }

    public double getAmount() {
        return amount;
    }

    @Override
    public QuestRewardType<?> getType() {
        return QuestRewardType.MAX_AURA;
    }

    @Override
    public Component getDisplayComponent() {
        String text = displayText != null ? displayText :
                String.format("+%.0f Max Aura", amount);
        return Component.literal(text);
    }

    @Override
    public String getDisplayString() {
        return displayText != null ? displayText :
                String.format("+%.0f Max Aura", amount);
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "max_aura");
        json.addProperty("amount", amount);
        QuestDisplayUtil.addDisplayProperty(json, displayText);
        return json;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeDouble(amount);
        QuestDisplayUtil.writeDisplay(buf, displayText);
    }

    public static MaxAuraReward fromJson(JsonObject json) {
        double amount = json.get("amount").getAsDouble();
        String display = QuestDisplayUtil.readDisplay(json);
        return new MaxAuraReward(amount, display);
    }

    public static MaxAuraReward fromNetwork(FriendlyByteBuf buf) {
        double amount = buf.readDouble();
        String display = QuestDisplayUtil.readDisplay(buf);
        return new MaxAuraReward(amount, display);
    }
}
