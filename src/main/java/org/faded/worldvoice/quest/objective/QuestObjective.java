package org.faded.worldvoice.quest.objective;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

public interface QuestObjective {
    QuestObjectiveType<?> getType();

    float getProgress();

    boolean isCompleted();

    Component getProgressDisplay();

    JsonObject toJson();

    void toNetwork(FriendlyByteBuf buf);

    int getRequiredCount();

    int getCurrentCount();
}
