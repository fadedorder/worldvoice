package org.faded.worldvoice.quest.objective;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.faded.worldvoice.quest.QuestDisplayUtil;

/**
 * Objective requiring the player to gain a certain amount of EP during the quest
 */
public class EPGainRequirement implements QuestObjective {
    private final double requiredGain;
    private final double startEP;
    private double currentEP;
    private final boolean consumeGain;
    private final String displayText;

    public EPGainRequirement(double requiredGain, double startEP, double currentEP, boolean consumeGain) {
        this(requiredGain, startEP, currentEP, consumeGain, null);
    }

    public EPGainRequirement(double requiredGain, double startEP, double currentEP, boolean consumeGain, String displayText) {
        this.requiredGain = requiredGain;
        this.startEP = startEP;
        this.currentEP = currentEP;
        this.consumeGain = consumeGain;
        this.displayText = QuestDisplayUtil.normalize(displayText);
    }

    public double getGained() {
        return Math.max(0, currentEP - startEP);
    }

    public boolean shouldConsumeGain() {
        return consumeGain;
    }

    public void setCurrentEP(double ep) {
        this.currentEP = ep;
    }

    @Override
    public QuestObjectiveType<?> getType() {
        return QuestObjectiveType.EP_GAIN_REQUIREMENT;
    }

    @Override
    public float getProgress() {
        return (float) Math.min(getGained() / requiredGain, 1.0);
    }

    @Override
    public boolean isCompleted() {
        return getGained() >= requiredGain;
    }

    @Override
    public Component getProgressDisplay() {
        String suffix = consumeGain ? " (Will Consume Gain)" : "";
        String label = displayText != null ? displayText : "Gain EP";
        return Component.literal(String.format("%s: %.0f/%.0f%s", label, getGained(), requiredGain, suffix));
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "ep_gain_requirement");
        json.addProperty("required_gain", requiredGain);
        json.addProperty("start_ep", startEP);
        json.addProperty("current_ep", currentEP);
        json.addProperty("consume_gain", consumeGain);
        QuestDisplayUtil.addDisplayProperty(json, displayText);
        return json;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeDouble(requiredGain);
        buf.writeDouble(startEP);
        buf.writeDouble(currentEP);
        buf.writeBoolean(consumeGain);
        QuestDisplayUtil.writeDisplay(buf, displayText);
    }

    @Override
    public int getRequiredCount() {
        return (int) requiredGain;
    }

    @Override
    public int getCurrentCount() {
        return (int) getGained();
    }

    public static EPGainRequirement fromJson(JsonObject json) {
        double requiredGain = json.get("required_gain").getAsDouble();
        double startEP = json.has("start_ep") ? json.get("start_ep").getAsDouble() : 0;
        double currentEP = json.has("current_ep") ? json.get("current_ep").getAsDouble() : startEP;
        boolean consumeGain = json.has("consume_gain") && json.get("consume_gain").getAsBoolean();
        String display = QuestDisplayUtil.readDisplay(json);
        return new EPGainRequirement(requiredGain, startEP, currentEP, consumeGain, display);
    }

    public static EPGainRequirement fromNetwork(FriendlyByteBuf buf) {
        double requiredGain = buf.readDouble();
        double startEP = buf.readDouble();
        double currentEP = buf.readDouble();
        boolean consumeGain = buf.readBoolean();
        String display = QuestDisplayUtil.readDisplay(buf);
        return new EPGainRequirement(requiredGain, startEP, currentEP, consumeGain, display);
    }
}
