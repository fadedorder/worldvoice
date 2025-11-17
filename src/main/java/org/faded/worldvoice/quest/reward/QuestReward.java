package org.faded.worldvoice.quest.reward;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;


public interface QuestReward {

    QuestRewardType<?> getType();

    Component getDisplayComponent();

    JsonObject toJson();

    void toNetwork(FriendlyByteBuf buf);


    String getDisplayString();
}
