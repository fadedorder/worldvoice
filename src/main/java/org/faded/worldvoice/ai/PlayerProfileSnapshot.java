package org.faded.worldvoice.ai;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import org.faded.worldvoice.integration.TensuraBridge;

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
        int uniqueOwned
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
                data.uniqueOwned()
        );
    }

    public record Extras(int recentAdvancements, long lastEvaluationMs, int uniqueOwned) {
        private static final Extras EMPTY = new Extras(0, 0L, 0);
    }
}
