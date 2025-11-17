package org.faded.worldvoice.quest.requirement;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;


public interface QuestRequirement {

    boolean meetsRequirement(ServerPlayer player);

    String getDisplayString();

    String getType();

    JsonObject toJson();

    void toNetwork(FriendlyByteBuf buf);
}
