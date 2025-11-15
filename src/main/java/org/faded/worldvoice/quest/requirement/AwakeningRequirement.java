package org.faded.worldvoice.quest.requirement;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.faded.worldvoice.integration.TensuraBridge;
import org.faded.worldvoice.quest.QuestDisplayUtil;

/**
 * Requirement: Player must have a specific awakening (True Hero, True Demon Lord, etc.)
 */
public class AwakeningRequirement implements QuestRequirement {
    private final AwakeningType awakeningType;
    private final String displayText;

    public enum AwakeningType {
        TRUE_HERO,
        TRUE_DEMON_LORD,
        HERO_SEED,
        DEMON_LORD_SEED,
        BLESSING
    }

    public AwakeningRequirement(AwakeningType awakeningType) {
        this(awakeningType, null);
    }

    public AwakeningRequirement(AwakeningType awakeningType, String displayText) {
        this.awakeningType = awakeningType;
        this.displayText = QuestDisplayUtil.normalize(displayText);
    }

    @Override
    public boolean meetsRequirement(ServerPlayer player) {
        return switch (awakeningType) {
            case TRUE_HERO -> TensuraBridge.isTrueHero(player);
            case TRUE_DEMON_LORD -> TensuraBridge.isTrueDemonLord(player);
            default -> false; // TODO: Add other awakening checks when available
        };
    }

    @Override
    public String getDisplayString() {
        return displayText != null ? displayText :
                "Requires: " + awakeningType.name().replace("_", " ");
    }

    @Override
    public String getType() {
        return "awakening";
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "awakening");
        json.addProperty("awakening_type", awakeningType.name());
        QuestDisplayUtil.addDisplayProperty(json, displayText);
        return json;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeEnum(awakeningType);
        QuestDisplayUtil.writeDisplay(buf, displayText);
    }

    public static AwakeningRequirement fromJson(JsonObject json) {
        AwakeningType type = AwakeningType.valueOf(json.get("awakening_type").getAsString());
        String display = QuestDisplayUtil.readDisplay(json);
        return new AwakeningRequirement(type, display);
    }

    public static AwakeningRequirement fromNetwork(FriendlyByteBuf buf) {
        AwakeningType type = buf.readEnum(AwakeningType.class);
        String display = QuestDisplayUtil.readDisplay(buf);
        return new AwakeningRequirement(type, display);
    }

    public AwakeningType getAwakeningType() {
        return awakeningType;
    }
}
