package org.faded.worldvoice.quest.objective;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/**
 * Base interface for all quest objectives.
 * Objectives define what the player must accomplish to complete a quest.
 */
public interface QuestObjective {
    /**
     * Get the type identifier for this objective
     */
    QuestObjectiveType<?> getType();

    /**
     * Get the current progress value (0.0 to 1.0)
     */
    float getProgress();

    /**
     * Check if this objective is completed
     */
    boolean isCompleted();

    /**
     * Get a display component showing progress
     */
    Component getProgressDisplay();

    /**
     * Serialize this objective to JSON
     */
    JsonObject toJson();

    /**
     * Write this objective to a network buffer
     */
    void toNetwork(FriendlyByteBuf buf);

    /**
     * Get the requirement count (e.g., number of kills needed)
     */
    int getRequiredCount();

    /**
     * Get the current progress count
     */
    int getCurrentCount();
}
