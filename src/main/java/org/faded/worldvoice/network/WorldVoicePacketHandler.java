package org.faded.worldvoice.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.faded.worldvoice.Worldvoice;

/**
 * Network packet handler for WorldVoice quest system
 */
public class WorldVoicePacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Worldvoice.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    private static int id() {
        return packetId++;
    }

    public static void register() {
        // Server -> Client packets
        INSTANCE.registerMessage(
                id(),
                QuestSyncPacket.class,
                QuestSyncPacket::encode,
                QuestSyncPacket::decode,
                QuestSyncPacket::handle
        );

        INSTANCE.registerMessage(
                id(),
                QuestProgressUpdatePacket.class,
                QuestProgressUpdatePacket::encode,
                QuestProgressUpdatePacket::decode,
                QuestProgressUpdatePacket::handle
        );

        // Client -> Server packets
        INSTANCE.registerMessage(
                id(),
                QuestActionPacket.class,
                QuestActionPacket::encode,
                QuestActionPacket::decode,
                QuestActionPacket::handle
        );

        Worldvoice.LOGGER.info("WorldVoice quest packets registered");
    }
}
