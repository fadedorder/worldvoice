package org.faded.worldvoice.quest.reward;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/**
 * Base interface for all quest rewards.
 * Rewards define what the player receives upon quest completion.
 */
public interface QuestReward {
    /**
     * Get the type identifier for this reward
     */
    QuestRewardType<?> getType();

    /**
     * Get a display component describing this reward
     */
    Component getDisplayComponent();

    /**
     * Serialize this reward to JSON
     */
    JsonObject toJson();

    /**
     * Write this reward to a network buffer
     */
    void toNetwork(FriendlyByteBuf buf);

    /**
     * Get a string representation for display in lists
     */
    String getDisplayString();
}
