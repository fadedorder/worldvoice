package org.faded.worldvoice.quest;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.faded.worldvoice.Worldvoice;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles automatic refresh of Daily and Weekly quests
 */
@Mod.EventBusSubscriber(modid = Worldvoice.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class QuestRefreshScheduler {
    private static final long RESET_CHECK_INTERVAL_TICKS = 1200; // Check every minute for resets
    private static final int RAPID_SYNC_INTERVAL_TICKS = 20; // Quick sync every second
    private static long lastResetCheckTime = 0;
    private static long lastRapidSyncTick = 0;
    private static final Map<UUID, Long> lastDailyReset = new HashMap<>();
    private static final Map<UUID, Long> lastWeeklyReset = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var server = event.getServer();
        long currentTick = server.getTickCount();

        // Rapid quest sync to minimize UI delay
        if (currentTick - lastRapidSyncTick >= RAPID_SYNC_INTERVAL_TICKS) {
            lastRapidSyncTick = currentTick;
            runRapidSync(server);
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastResetCheckTime >= RESET_CHECK_INTERVAL_TICKS * 50) { // Convert ticks to ms
            lastResetCheckTime = currentTime;
            runResetChecks(server, currentTime);
        }
    }

    private static void runRapidSync(net.minecraft.server.MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                // Ensure new quests are assigned promptly
                QuestLoader.assignQuestsToPlayer(player);
                // Inexpensive periodic sync keeps UI up to date
                ServerQuestManager.syncQuests(player);
            }
        }
    }

    private static void runResetChecks(net.minecraft.server.MinecraftServer server, long currentTime) {
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                checkAndResetQuests(player, currentTime);
            }
        }
    }

    private static void checkAndResetQuests(ServerPlayer player, long currentTime) {
        UUID playerId = player.getUUID();

        // Check for daily reset (midnight local time)
        if (shouldResetDaily(playerId, currentTime)) {
            resetDailyQuests(player);
            lastDailyReset.put(playerId, currentTime);
        }

        // Check for weekly reset (Monday midnight local time)
        if (shouldResetWeekly(playerId, currentTime)) {
            resetWeeklyQuests(player);
            lastWeeklyReset.put(playerId, currentTime);
        }
    }

    private static boolean shouldResetDaily(UUID playerId, long currentTime) {
        Long lastReset = lastDailyReset.get(playerId);
        if (lastReset == null) {
            lastDailyReset.put(playerId, currentTime);
            return false;
        }

        LocalDateTime lastResetDate = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(lastReset), ZoneId.systemDefault());
        LocalDateTime currentDate = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(currentTime), ZoneId.systemDefault());

        // Reset if it's a different day
        return !lastResetDate.toLocalDate().equals(currentDate.toLocalDate());
    }

    private static boolean shouldResetWeekly(UUID playerId, long currentTime) {
        Long lastReset = lastWeeklyReset.get(playerId);
        if (lastReset == null) {
            lastWeeklyReset.put(playerId, currentTime);
            return false;
        }

        LocalDateTime lastResetDate = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(lastReset), ZoneId.systemDefault());
        LocalDateTime currentDate = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(currentTime), ZoneId.systemDefault());

        // Calculate days between dates
        long daysBetween = ChronoUnit.DAYS.between(lastResetDate.toLocalDate(), currentDate.toLocalDate());

        // Reset if 7 or more days have passed and it's Monday (day of week = 1)
        return daysBetween >= 7 || (daysBetween > 0 && currentDate.getDayOfWeek().getValue() == 1
                && lastResetDate.getDayOfWeek().getValue() != 1);
    }

    private static void resetDailyQuests(ServerPlayer player) {
        Map<UUID, AdvancedQuestData> quests = ServerQuestManager.getPlayerQuests(player.getUUID());
        int resetCount = 0;

        for (AdvancedQuestData quest : quests.values()) {
            if (quest.getCategory() == QuestTab.DAILY && !quest.isCompleted()) {
                // Reset quest progress
                resetQuestProgress(quest);
                resetCount++;
            } else if (quest.getCategory() == QuestTab.DAILY && quest.isCompleted()) {
                // Make available again
                quest.setStatus(QuestStatus.AVAILABLE);
                quest.setCompletedTimestamp(0);
                resetQuestProgress(quest);
                resetCount++;
            }
        }

        if (resetCount > 0) {
            ServerQuestManager.syncQuests(player);
            Worldvoice.LOGGER.info("Reset {} daily quests for player {}", resetCount, player.getGameProfile().getName());
        }
    }

    private static void resetWeeklyQuests(ServerPlayer player) {
        Map<UUID, AdvancedQuestData> quests = ServerQuestManager.getPlayerQuests(player.getUUID());
        int resetCount = 0;

        for (AdvancedQuestData quest : quests.values()) {
            if (quest.getCategory() == QuestTab.WEEKLY && !quest.isCompleted()) {
                // Reset quest progress
                resetQuestProgress(quest);
                resetCount++;
            } else if (quest.getCategory() == QuestTab.WEEKLY && quest.isCompleted()) {
                // Make available again
                quest.setStatus(QuestStatus.AVAILABLE);
                quest.setCompletedTimestamp(0);
                resetQuestProgress(quest);
                resetCount++;
            }
        }

        if (resetCount > 0) {
            ServerQuestManager.syncQuests(player);
            Worldvoice.LOGGER.info("Reset {} weekly quests for player {}", resetCount, player.getGameProfile().getName());
        }
    }

    private static void resetQuestProgress(AdvancedQuestData quest) {
        quest.setStatus(QuestStatus.AVAILABLE);
        quest.setTracked(false);
        quest.setLastResetTimestamp(System.currentTimeMillis());

        // Reset all objective progress
        for (var objective : quest.getObjectives()) {
            boolean reset = false;
            try {
                // Try setCurrentCount
                objective.getClass().getMethod("setCurrentCount", int.class).invoke(objective, 0);
                reset = true;
            } catch (Exception e) {
                // Try setCurrentKills
                try {
                    objective.getClass().getMethod("setCurrentKills", int.class).invoke(objective, 0);
                    reset = true;
                } catch (Exception e2) {
                    // Try setCurrentEP
                    try {
                        objective.getClass().getMethod("setCurrentEP", double.class).invoke(objective, 0.0);
                        reset = true;
                    } catch (Exception e3) {
                        // Objective doesn't support resetting
                    }
                }
            }
            if (!reset) {
                Worldvoice.LOGGER.debug("Could not reset objective of type: {}", objective.getClass().getSimpleName());
            }
        }
    }

    /**
     * Called when a player logs in to initialize reset timestamps
     */
    public static void initializePlayer(UUID playerId) {
        long currentTime = System.currentTimeMillis();
        lastDailyReset.putIfAbsent(playerId, currentTime);
        lastWeeklyReset.putIfAbsent(playerId, currentTime);
    }

    /**
     * Called when a player logs out to clean up memory
     */
    public static void cleanupPlayer(UUID playerId) {
        // Don't remove - keep timestamps for when they log back in
        // This prevents quests from resetting every time they log in
    }
}
