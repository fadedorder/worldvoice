package org.faded.worldvoice.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.faded.worldvoice.quest.ServerQuestManager;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Packet sent from client to server for quest actions
 */
public class QuestActionPacket {
    private final UUID questId;
    private final ActionType action;
    private final int objectiveIndex; // For submit actions

    public enum ActionType {
        START, CANCEL, TRACK, UNTRACK, SUBMIT_ITEMS, SUBMIT_EP
    }

    public QuestActionPacket(UUID questId, ActionType action) {
        this(questId, action, -1);
    }

    public QuestActionPacket(UUID questId, ActionType action, int objectiveIndex) {
        this.questId = questId;
        this.action = action;
        this.objectiveIndex = objectiveIndex;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(questId);
        buf.writeEnum(action);
        buf.writeInt(objectiveIndex);
    }

    public static QuestActionPacket decode(FriendlyByteBuf buf) {
        UUID questId = buf.readUUID();
        ActionType action = buf.readEnum(ActionType.class);
        int objectiveIndex = buf.readInt();
        return new QuestActionPacket(questId, action, objectiveIndex);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            switch (action) {
                case START -> ServerQuestManager.startQuest(player, questId);
                case CANCEL -> ServerQuestManager.cancelQuest(player, questId);
                case TRACK -> ServerQuestManager.trackQuest(player, questId);
                case UNTRACK -> ServerQuestManager.untrackQuest(player, questId);
                case SUBMIT_ITEMS -> ServerQuestManager.submitItems(player, questId, objectiveIndex);
                case SUBMIT_EP -> ServerQuestManager.submitEP(player, questId, objectiveIndex);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
