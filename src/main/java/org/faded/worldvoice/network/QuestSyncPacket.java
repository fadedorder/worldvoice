package org.faded.worldvoice.network;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.faded.worldvoice.Worldvoice;
import org.faded.worldvoice.quest.AdvancedQuestData;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Packet sent from server to client to synchronize quest data
 */
public class QuestSyncPacket {
    private final List<AdvancedQuestData> quests;

    public QuestSyncPacket(List<AdvancedQuestData> quests) {
        this.quests = quests;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(quests.size());
        for (AdvancedQuestData quest : quests) {
            quest.toNetwork(buf);
        }
    }

    public static QuestSyncPacket decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<AdvancedQuestData> quests = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            quests.add(AdvancedQuestData.fromNetwork(buf));
        }
        return new QuestSyncPacket(quests);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            try {
                Class<?> clientManagerClass = Class.forName("org.faded.arcclient.quest.ClientQuestManager");
                Class<?> clientQuestClass = Class.forName("org.faded.arcclient.quest.AdvancedQuestData");
                Object manager = clientManagerClass.getMethod("getInstance").invoke(null);

                Method fromJsonMethod = clientQuestClass.getMethod("fromJson", JsonObject.class);
                Method replaceMethod = clientManagerClass.getMethod("replaceAllQuests", java.util.Collection.class);

                List<Object> clientQuests = new ArrayList<>();
                for (AdvancedQuestData quest : quests) {
                    JsonObject questJson = quest.toJson();
                    clientQuests.add(fromJsonMethod.invoke(null, questJson));
                }

                replaceMethod.invoke(manager, clientQuests);
            } catch (ClassNotFoundException ignored) {
                // Client utility mod not installed
            } catch (Exception e) {
                Worldvoice.LOGGER.warn("Failed to sync quests to client", e);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
