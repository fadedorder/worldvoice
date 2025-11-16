package org.faded.worldvoice.ai;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.faded.worldvoice.integration.TensuraBridge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lightweight immutable view of a player's current footprint.
 */
public record PlayerProfileSnapshot(
        String name,
        int experienceLevel,
        long playTicks,
        long distanceTravelled,
        int totalDeaths,
        boolean hasTeam,
        ResourceLocation lastAdvancement,
        double ep,
        double magicules,
        double aura,
        String raceId,
        boolean trueHero,
        boolean trueDemonLord,
        int recentAdvancements,
        long lastEvaluationMs,
        int uniqueOwned,
        long mobsKilled,
        long bossKills,
        long damageDealt,
        long damageTaken,
        long blocksMined,
        long blocksPlaced,
        long itemsCrafted,
        long itemsUsed
) {

    public static PlayerProfileSnapshot capture(ServerPlayer player, ResourceLocation lastAdvancement) {
        return capture(player, lastAdvancement, Extras.EMPTY);
    }

    public static PlayerProfileSnapshot capture(ServerPlayer player, ResourceLocation lastAdvancement, Extras extras) {
        ServerStatsCounter stats = player.getStats();
        long playTicks = stats.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        long distance = stats.getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM))
                + stats.getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM))
                + stats.getValue(Stats.CUSTOM.get(Stats.SWIM_ONE_CM))
                + stats.getValue(Stats.CUSTOM.get(Stats.FLY_ONE_CM));
        int deaths = stats.getValue(Stats.CUSTOM.get(Stats.DEATHS));
        long mobsKilled = sumEntityKills(stats);
        long bossKills = sumBossKills(stats);
        long damageDealt = stats.getValue(Stats.CUSTOM.get(Stats.DAMAGE_DEALT));
        long damageTaken = stats.getValue(Stats.CUSTOM.get(Stats.DAMAGE_TAKEN));
        long blocksMined = sumBlockMined(stats);
        long blocksPlaced = sumBlockPlacements(stats);
        long itemsCrafted = sumItemStat(stats, Stats.ITEM_CRAFTED);
        long itemsUsed = sumItemStat(stats, Stats.ITEM_USED);
        boolean hasTeam = player.getTeam() != null;
        Extras data = extras == null ? Extras.EMPTY : extras;
        return new PlayerProfileSnapshot(
                player.getGameProfile().getName(),
                player.experienceLevel,
                playTicks,
                distance,
                deaths,
                hasTeam,
                lastAdvancement,
                TensuraBridge.getEp(player),
                TensuraBridge.getMagicules(player),
                TensuraBridge.getAura(player),
                TensuraBridge.getRaceId(player),
                TensuraBridge.isTrueHero(player),
                TensuraBridge.isTrueDemonLord(player),
                data.recentAdvancements(),
                data.lastEvaluationMs(),
                data.uniqueOwned(),
                mobsKilled,
                bossKills,
                damageDealt,
                damageTaken,
                blocksMined,
                blocksPlaced,
                itemsCrafted,
                itemsUsed
        );
    }

    public double playHours() {
        return playTicks / 72000.0d;
    }

    public double killsPerHour() {
        return safeDivide(mobsKilled, normalizedHours());
    }

    public double bossKillsPerHour() {
        return safeDivide(bossKills, normalizedHours());
    }

    public double deathsPerHour() {
        return safeDivide(totalDeaths, normalizedHours());
    }

    public double blocksPlacedPerHour() {
        return safeDivide(blocksPlaced, normalizedHours());
    }

    public double blocksMinedPerHour() {
        return safeDivide(blocksMined, normalizedHours());
    }

    public double craftsPerHour() {
        return safeDivide(itemsCrafted, normalizedHours());
    }

    public double itemsUsedPerHour() {
        return safeDivide(itemsUsed, normalizedHours());
    }

    public double distanceKmPerHour() {
        // Stats are in centimeters; convert to kilometers.
        double kilometers = distanceTravelled / 100000.0d;
        return safeDivide(kilometers, normalizedHours());
    }

    public double damageTakenPerHour() {
        return safeDivide(damageTaken, normalizedHours());
    }

    public double damageDealtPerHour() {
        return safeDivide(damageDealt, normalizedHours());
    }

    public List<String> profileTags() {
        List<String> tags = new ArrayList<>();
        double buildRate = blocksPlacedPerHour();
        double killRate = killsPerHour();
        double deathRate = deathsPerHour();
        double travelRate = distanceKmPerHour();
        double craftRate = craftsPerHour();

        if (buildRate >= 160) {
            tags.add("builder");
        }
        if (killRate >= 15 || bossKills >= 5) {
            tags.add("slayer");
        } else if (killRate >= 5) {
            tags.add("combatant");
        }
        if (travelRate >= 2.0d) {
            tags.add("wanderer");
        }
        if (bossKillsPerHour() > 0.2d) {
            tags.add("conqueror");
        }
        if (craftRate >= 45) {
            tags.add("artisan");
        }
        if (itemsUsedPerHour() >= 80) {
            tags.add("support");
        }
        if (deathRate <= 0.1d && damageTakenPerHour() < 80) {
            tags.add("survivor");
        } else if (deathRate >= 0.6d) {
            tags.add("reckless");
        }
        if (hasTeam) {
            tags.add("ally");
        }
        if (tags.isEmpty()) {
            return List.of("balanced");
        }
        return List.copyOf(tags);
    }

    private double normalizedHours() {
        return Math.max(playHours(), 1.0d / 72.0d);
    }

    private static double safeDivide(long value, double divisor) {
        return safeDivide((double) value, divisor);
    }

    private static double safeDivide(double value, double divisor) {
        return divisor <= 0.0d ? 0.0d : value / divisor;
    }

    private static long sumEntityKills(ServerStatsCounter stats) {
        long total = 0L;
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES.getValues()) {
            total += stats.getValue(Stats.ENTITY_KILLED.get(type));
        }
        return total;
    }

    private static long sumBossKills(ServerStatsCounter stats) {
        long total = 0L;
        for (ResourceLocation bossId : DEFAULT_BOSS_IDS) {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(bossId);
            if (type != null) {
                total += stats.getValue(Stats.ENTITY_KILLED.get(type));
            }
        }
        return total;
    }

    private static long sumBlockMined(ServerStatsCounter stats) {
        long total = 0L;
        for (Block block : ForgeRegistries.BLOCKS.getValues()) {
            total += stats.getValue(Stats.BLOCK_MINED.get(block));
        }
        return total;
    }

    private static long sumBlockPlacements(ServerStatsCounter stats) {
        long total = 0L;
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (item instanceof BlockItem) {
                total += stats.getValue(Stats.ITEM_USED.get(item));
            }
        }
        return total;
    }

    private static long sumItemStat(ServerStatsCounter stats, StatType<Item> type) {
        long total = 0L;
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            total += stats.getValue(type.get(item));
        }
        return total;
    }

    private static final Set<ResourceLocation> DEFAULT_BOSS_IDS = buildBossSet();

    private static Set<ResourceLocation> buildBossSet() {
        Set<ResourceLocation> ids = new HashSet<>();
        ids.add(ResourceLocation.fromNamespaceAndPath("minecraft", "ender_dragon"));
        ids.add(ResourceLocation.fromNamespaceAndPath("minecraft", "wither"));
        ids.add(ResourceLocation.fromNamespaceAndPath("minecraft", "elder_guardian"));
        ids.add(ResourceLocation.fromNamespaceAndPath("minecraft", "warden"));
        ids.add(ResourceLocation.fromNamespaceAndPath("minecraft", "evoker"));
        return ids;
    }

    public record Extras(int recentAdvancements, long lastEvaluationMs, int uniqueOwned) {
        private static final Extras EMPTY = new Extras(0, 0L, 0);
    }
}
