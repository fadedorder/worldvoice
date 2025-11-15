package org.faded.worldvoice.quest;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Utility helpers for working with optional custom display strings on quest data.
 */
public final class QuestDisplayUtil {
    private QuestDisplayUtil() {
    }

    /**
     * Normalize a display string by trimming whitespace and returning {@code null} if blank.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Add the display property to a JSON object if present.
     */
    public static void addDisplayProperty(JsonObject json, String display) {
        String normalized = normalize(display);
        if (normalized != null) {
            json.addProperty("display", normalized);
        }
    }

    /**
     * Read an optional display property from JSON.
     */
    public static String readDisplay(JsonObject json) {
        return json.has("display") ? normalize(json.get("display").getAsString()) : null;
    }

    /**
     * Write an optional display string to the network buffer.
     */
    public static void writeDisplay(FriendlyByteBuf buf, String display) {
        String normalized = normalize(display);
        buf.writeBoolean(normalized != null);
        if (normalized != null) {
            buf.writeUtf(normalized);
        }
    }

    /**
     * Read an optional display string from the network buffer.
     */
    public static String readDisplay(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUtf() : null;
    }
}
