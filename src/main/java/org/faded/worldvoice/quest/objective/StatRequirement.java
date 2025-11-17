package org.faded.worldvoice.quest.objective;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.faded.worldvoice.quest.QuestDisplayUtil;


public class StatRequirement implements QuestObjective {
    private final String statName;
    private final double requiredValue;
    private double currentValue;
    private final double startValue;
    private final boolean isGainRequirement;
    private final String displayText;

    public StatRequirement(String statName, double requiredValue, double currentValue, double startValue, boolean isGainRequirement) {
        this(statName, requiredValue, currentValue, startValue, isGainRequirement, null);
    }

    public StatRequirement(String statName, double requiredValue, double currentValue, double startValue, boolean isGainRequirement, String displayText) {
        this.statName = statName;
        this.requiredValue = requiredValue;
        this.currentValue = currentValue;
        this.startValue = startValue;
        this.isGainRequirement = isGainRequirement;
        this.displayText = QuestDisplayUtil.normalize(displayText);
    }

    public String getStatName() {
        return statName;
    }

    public double getRequiredValue() {
        return requiredValue;
    }

    public boolean isGainRequirement() {
        return isGainRequirement;
    }

    public void setCurrentValue(double value) {
        this.currentValue = value;
    }

    public double getEffectiveValue() {
        return isGainRequirement ? Math.max(0, currentValue - startValue) : currentValue;
    }

    @Override
    public QuestObjectiveType<?> getType() {
        return statName.equals("max_aura") ?
                QuestObjectiveType.MAX_AURA_REQUIREMENT :
                QuestObjectiveType.MAX_MP_REQUIREMENT;
    }

    @Override
    public float getProgress() {
        return (float) Math.min(getEffectiveValue() / requiredValue, 1.0);
    }

    @Override
    public boolean isCompleted() {
        return getEffectiveValue() >= requiredValue;
    }

    @Override
    public Component getProgressDisplay() {
        String displayName = displayText != null ? displayText :
                (statName.replace("_", " ").toUpperCase() + (isGainRequirement ? " GAIN" : ""));
        return Component.literal(String.format("%s: %.0f/%.0f",
                displayName, getEffectiveValue(), requiredValue));
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", statName + (isGainRequirement ? "_gain" : "") + "_requirement");
        json.addProperty("stat_name", statName);
        json.addProperty("required", requiredValue);
        json.addProperty("current", currentValue);
        json.addProperty("start", startValue);
        json.addProperty("is_gain", isGainRequirement);
        QuestDisplayUtil.addDisplayProperty(json, displayText);
        return json;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeUtf(statName);
        buf.writeDouble(requiredValue);
        buf.writeDouble(currentValue);
        buf.writeDouble(startValue);
        buf.writeBoolean(isGainRequirement);
        QuestDisplayUtil.writeDisplay(buf, displayText);
    }

    @Override
    public int getRequiredCount() {
        return (int) requiredValue;
    }

    @Override
    public int getCurrentCount() {
        return (int) getEffectiveValue();
    }

    public static StatRequirement fromJson(JsonObject json, String defaultStatName) {
        String statName = json.has("stat_name") ? json.get("stat_name").getAsString() : defaultStatName;
        double required = json.get("required").getAsDouble();
        double current = json.has("current") ? json.get("current").getAsDouble() : 0;
        double start = json.has("start") ? json.get("start").getAsDouble() : 0;
        boolean isGain = json.has("is_gain") && json.get("is_gain").getAsBoolean();
        String display = QuestDisplayUtil.readDisplay(json);
        return new StatRequirement(statName, required, current, start, isGain, display);
    }

    public static StatRequirement fromNetwork(FriendlyByteBuf buf, String defaultStatName) {
        String statName = buf.readUtf();
        double required = buf.readDouble();
        double current = buf.readDouble();
        double start = buf.readDouble();
        boolean isGain = buf.readBoolean();
        String display = QuestDisplayUtil.readDisplay(buf);
        return new StatRequirement(statName, required, current, start, isGain, display);
    }
}
