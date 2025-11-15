package org.faded.worldvoice.quest.requirement;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.faded.worldvoice.integration.TensuraBridge;
import org.faded.worldvoice.quest.QuestDisplayUtil;

/**
 * Requirement: Player must have at least X EP
 */
public class EPRequirement implements QuestRequirement {
    private final double requiredEP;
    private final String displayText;

    public EPRequirement(double requiredEP) {
        this(requiredEP, null);
    }

    public EPRequirement(double requiredEP, String displayText) {
        this.requiredEP = requiredEP;
        this.displayText = QuestDisplayUtil.normalize(displayText);
    }

    @Override
    public boolean meetsRequirement(ServerPlayer player) {
        return TensuraBridge.getEp(player) >= requiredEP;
    }

    @Override
    public String getDisplayString() {
        return displayText != null ? displayText : String.format("Requires %.0f EP", requiredEP);
    }

    @Override
    public String getType() {
        return "ep";
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "ep");
        json.addProperty("required_ep", requiredEP);
        QuestDisplayUtil.addDisplayProperty(json, displayText);
        return json;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeDouble(requiredEP);
        QuestDisplayUtil.writeDisplay(buf, displayText);
    }

    public static EPRequirement fromJson(JsonObject json) {
        double required = json.get("required_ep").getAsDouble();
        String display = QuestDisplayUtil.readDisplay(json);
        return new EPRequirement(required, display);
    }

    public static EPRequirement fromNetwork(FriendlyByteBuf buf) {
        double required = buf.readDouble();
        String display = QuestDisplayUtil.readDisplay(buf);
        return new EPRequirement(required, display);
    }

    public double getRequiredEP() {
        return requiredEP;
    }
}
