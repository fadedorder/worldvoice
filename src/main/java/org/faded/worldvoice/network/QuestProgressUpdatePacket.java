package org.faded.worldvoice.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.faded.worldvoice.Worldvoice;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Packet sent from server to client when quest progress updates
 */
public class QuestProgressUpdatePacket {
    private final UUID questId;
    private final int objectiveIndex;
    private final int newProgress;
    private final int requiredProgress;

    public QuestProgressUpdatePacket(UUID questId, int objectiveIndex, int newProgress, int requiredProgress) {
        this.questId = questId;
        this.objectiveIndex = objectiveIndex;
        this.newProgress = newProgress;
        this.requiredProgress = requiredProgress;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(questId);
        buf.writeInt(objectiveIndex);
        buf.writeInt(newProgress);
        buf.writeInt(requiredProgress);
    }

    public static QuestProgressUpdatePacket decode(FriendlyByteBuf buf) {
        UUID questId = buf.readUUID();
        int objectiveIndex = buf.readInt();
        int newProgress = buf.readInt();
        int requiredProgress = buf.readInt();
        return new QuestProgressUpdatePacket(questId, objectiveIndex, newProgress, requiredProgress);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            try {
                Class<?> clientManagerClass = Class.forName("org.faded.arcclient.quest.ClientQuestManager");
                Class<?> clientQuestClass = Class.forName("org.faded.arcclient.quest.AdvancedQuestData");
                Object manager = clientManagerClass.getMethod("getInstance").invoke(null);

                Object quest = clientManagerClass.getMethod("getQuest", UUID.class).invoke(manager, questId);
                if (quest == null) {
                    return;
                }

                @SuppressWarnings("unchecked")
                List<?> objectives = (List<?>) clientQuestClass.getMethod("getObjectives").invoke(quest);
                if (objectiveIndex < 0 || objectiveIndex >= objectives.size()) {
                    return;
                }

                Object objective = objectives.get(objectiveIndex);
                boolean updated = tryInvokeProgressSetter(objective, "setCurrentCount", int.class, newProgress);
                if (!updated) {
                    updated = tryInvokeProgressSetter(objective, "setCurrentKills", int.class, newProgress);
                }
                if (!updated) {
                    updated = tryInvokeProgressSetter(objective, "setCurrentEP", double.class, (double) newProgress);
                }

                if (updated) {
                    clientManagerClass.getMethod("addOrUpdateQuest", clientQuestClass).invoke(manager, quest);
                }
            } catch (ClassNotFoundException ignored) {
                // Client utility mod not installed
            } catch (Exception e) {
                Worldvoice.LOGGER.warn("Failed to update quest progress on client", e);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static boolean tryInvokeProgressSetter(Object target, String methodName, Class<?> paramType, Object value) {
        try {
            target.getClass().getMethod(methodName, paramType).invoke(target, value);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
