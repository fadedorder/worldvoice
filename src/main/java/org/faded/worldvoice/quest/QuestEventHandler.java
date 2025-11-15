package org.faded.worldvoice.quest;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.faded.worldvoice.Worldvoice;

/**
 * Event handler for quest progression
 */
@Mod.EventBusSubscriber(modid = Worldvoice.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class QuestEventHandler {

    /**
     * Handle mob kills for quest objectives
     */
    @SubscribeEvent
    public static void onMobKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }

        LivingEntity killed = event.getEntity();
        net.minecraft.core.Registry.ENTITY_TYPE.getKey(killed.getType());
        var mobKey = net.minecraft.core.Registry.ENTITY_TYPE.getKey(killed.getType());
        if (mobKey != null) {
            ServerQuestManager.updateKillProgress(player, mobKey);
        }
    }

    /**
     * Handle item pickups for quest objectives
     */
    @SubscribeEvent
    public static void onItemPickup(PlayerEvent.ItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        var itemKey = net.minecraft.core.Registry.ITEM.getKey(event.getStack().getItem());
        if (itemKey != null) {
            ServerQuestManager.updateItemProgress(player, itemKey);
        }
    }

    /**
     * Sync quests when player joins
     */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Initialize quest refresh scheduler for this player
            QuestRefreshScheduler.initializePlayer(player.getUUID());

            // Assign available quests to player
            QuestLoader.assignQuestsToPlayer(player);

            // Sync quests after a short delay to ensure client is ready
            player.getServer().tell(new net.minecraft.server.TickTask(
                    player.getServer().getTickCount() + 20,
                    () -> ServerQuestManager.syncQuests(player)
            ));
        }
    }

    /**
     * Clear quests when player leaves (optional - depends on if you want to persist)
     */
    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Optionally save quest data here
            // ServerQuestManager.savePlayerQuests(player.getUUID());
        }
    }
}
