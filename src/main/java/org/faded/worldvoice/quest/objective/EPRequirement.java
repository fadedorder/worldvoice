package org.faded.worldvoice.quest.objective;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.faded.worldvoice.quest.QuestDisplayUtil;

/**
 * Objective requiring the player to have a certain amount of EP (Experience Points)
 */
public class EPRequirement implements QuestObjective {
    private final double requiredEP;
    private double currentEP;
    private final boolean consumeEP;
    private final boolean unlockEP;
    private final String displayText;

    public EPRequirement(double requiredEP, double currentEP, boolean consumeEP, boolean unlockEP) {
        this(requiredEP, currentEP, consumeEP, unlockEP, null);
    }

    public EPRequirement(double requiredEP, double currentEP, boolean consumeEP, boolean unlockEP, String displayText) {
        this.requiredEP = requiredEP;
        this.currentEP = currentEP;
        this.consumeEP = consumeEP;
        this.unlockEP = unlockEP;
        this.displayText = QuestDisplayUtil.normalize(displayText);
    }

    public double getRequiredEP() {
        return requiredEP;
    }

    public boolean shouldConsumeEP() {
        return consumeEP;
    }

    public boolean shouldUnlockEP() {
        return unlockEP;
    }

    public void setCurrentEP(double ep) {
        this.currentEP = ep;
    }

    @Override
    public QuestObjectiveType<?> getType() {
        return QuestObjectiveType.EP_REQUIREMENT;
    }

    @Override
    public float getProgress() {
        return (float) Math.min(currentEP / requiredEP, 1.0);
    }

    @Override
    public boolean isCompleted() {
        return currentEP >= requiredEP;
    }

    @Override
    public Component getProgressDisplay() {
        String suffix = consumeEP ? " (Will Consume)" : unlockEP ? " (Will Unlock)" : "";
        String label = displayText != null ? displayText : "EP Required";
        return Component.literal(String.format("%s: %.0f/%.0f%s", label, currentEP, requiredEP, suffix));
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "ep_requirement");
        json.addProperty("required", requiredEP);
        json.addProperty("current", currentEP);
        json.addProperty("consume", consumeEP);
        json.addProperty("unlock", unlockEP);
        QuestDisplayUtil.addDisplayProperty(json, displayText);
        return json;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeDouble(requiredEP);
        buf.writeDouble(currentEP);
        buf.writeBoolean(consumeEP);
        buf.writeBoolean(unlockEP);
        QuestDisplayUtil.writeDisplay(buf, displayText);
    }

    @Override
    public int getRequiredCount() {
        return (int) requiredEP;
    }

    @Override
    public int getCurrentCount() {
        return (int) currentEP;
    }

    public static EPRequirement fromJson(JsonObject json) {
        double required = json.get("required").getAsDouble();
        double current = json.has("current") ? json.get("current").getAsDouble() : 0;
        boolean consume = json.has("consume") && json.get("consume").getAsBoolean();
        boolean unlock = json.has("unlock") && json.get("unlock").getAsBoolean();
        String display = QuestDisplayUtil.readDisplay(json);
        return new EPRequirement(required, current, consume, unlock, display);
    }

    public static EPRequirement fromNetwork(FriendlyByteBuf buf) {
        double required = buf.readDouble();
        double current = buf.readDouble();
        boolean consume = buf.readBoolean();
        boolean unlock = buf.readBoolean();
        String display = QuestDisplayUtil.readDisplay(buf);
        return new EPRequirement(required, current, consume, unlock, display);
    }
}
