package org.faded.worldvoice.quest.reward;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.faded.worldvoice.quest.QuestDisplayUtil;

public class MaxMagiculesReward implements QuestReward {
    private final double amount;
    private final String displayText;

    public MaxMagiculesReward(double amount) {
        this(amount, null);
    }

    public MaxMagiculesReward(double amount, String displayText) {
        this.amount = amount;
        this.displayText = QuestDisplayUtil.normalize(displayText);
    }

    public double getAmount() {
        return amount;
    }

    @Override
    public QuestRewardType<?> getType() {
        return QuestRewardType.MAX_MAGICULES;
    }

    @Override
    public Component getDisplayComponent() {
        String text = displayText != null ? displayText :
                String.format("+%.0f Max Magicules", amount);
        return Component.literal(text);
    }

    @Override
    public String getDisplayString() {
        return displayText != null ? displayText :
                String.format("+%.0f Max Magicules", amount);
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "max_magicules");
        json.addProperty("amount", amount);
        QuestDisplayUtil.addDisplayProperty(json, displayText);
        return json;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeDouble(amount);
        QuestDisplayUtil.writeDisplay(buf, displayText);
    }

    public static MaxMagiculesReward fromJson(JsonObject json) {
        double amount = json.get("amount").getAsDouble();
        String display = QuestDisplayUtil.readDisplay(json);
        return new MaxMagiculesReward(amount, display);
    }

    public static MaxMagiculesReward fromNetwork(FriendlyByteBuf buf) {
        double amount = buf.readDouble();
        String display = QuestDisplayUtil.readDisplay(buf);
        return new MaxMagiculesReward(amount, display);
    }
}
