package org.faded.worldvoice.quest.requirement;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.faded.worldvoice.integration.TensuraBridge;
import org.faded.worldvoice.quest.QuestDisplayUtil;

/**
 * Requirement: Player must be a specific race
 */
public class RaceRequirement implements QuestRequirement {
    private final ResourceLocation raceId;
    private final String displayText;

    public RaceRequirement(ResourceLocation raceId) {
        this(raceId, null);
    }

    public RaceRequirement(ResourceLocation raceId, String displayText) {
        this.raceId = raceId;
        this.displayText = QuestDisplayUtil.normalize(displayText);
    }

    @Override
    public boolean meetsRequirement(ServerPlayer player) {
        String playerRace = TensuraBridge.getRaceId(player);
        return playerRace.equals(raceId.toString());
    }

    @Override
    public String getDisplayString() {
        return displayText != null ? displayText : "Requires race: " + raceId.getPath();
    }

    @Override
    public String getType() {
        return "race";
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "race");
        json.addProperty("race_id", raceId.toString());
        QuestDisplayUtil.addDisplayProperty(json, displayText);
        return json;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeResourceLocation(raceId);
        QuestDisplayUtil.writeDisplay(buf, displayText);
    }

    public static RaceRequirement fromJson(JsonObject json) {
        ResourceLocation raceId = new ResourceLocation(json.get("race_id").getAsString());
        String display = QuestDisplayUtil.readDisplay(json);
        return new RaceRequirement(raceId, display);
    }

    public static RaceRequirement fromNetwork(FriendlyByteBuf buf) {
        ResourceLocation raceId = buf.readResourceLocation();
        String display = QuestDisplayUtil.readDisplay(buf);
        return new RaceRequirement(raceId, display);
    }

    public ResourceLocation getRaceId() {
        return raceId;
    }
}
