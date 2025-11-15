package org.faded.worldvoice.quest.requirement;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * Base interface for quest unlock requirements
 */
public interface QuestRequirement {
    /**
     * Check if the player meets this requirement
     */
    boolean meetsRequirement(ServerPlayer player);

    /**
     * Get a display string describing this requirement
     */
    String getDisplayString();

    /**
     * Get the requirement type identifier
     */
    String getType();

    /**
     * Serialize to JSON
     */
    JsonObject toJson();

    /**
     * Serialize to network
     */
    void toNetwork(FriendlyByteBuf buf);
}
