package org.faded.worldvoice.quest;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;

public final class QuestDisplayUtil {
    private QuestDisplayUtil() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static void addDisplayProperty(JsonObject json, String display) {
        String normalized = normalize(display);
        if (normalized != null) {
            json.addProperty("display", normalized);
        }
    }

    public static String readDisplay(JsonObject json) {
        return json.has("display") ? normalize(json.get("display").getAsString()) : null;
    }

    public static void writeDisplay(FriendlyByteBuf buf, String display) {
        String normalized = normalize(display);
        buf.writeBoolean(normalized != null);
        if (normalized != null) {
            buf.writeUtf(normalized);
        }
    }

    public static String readDisplay(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUtf() : null;
    }
}
