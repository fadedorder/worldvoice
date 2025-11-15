package org.faded.worldvoice.integration;

import com.github.manasmods.tensura.capability.ep.ITensuraEPCapability;
import com.github.manasmods.tensura.capability.ep.TensuraEPCapability;
import com.github.manasmods.tensura.capability.race.ITensuraPlayerCapability;
import com.github.manasmods.tensura.capability.race.TensuraPlayerCapability;
import com.github.manasmods.tensura.race.Race;
import com.github.manasmods.tensura.registry.race.TensuraRaces;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Convenience wrappers for interacting with Tensura capabilities safely.
 */
public final class TensuraBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(TensuraBridge.class);

    private TensuraBridge() {}

    public static boolean addEp(ServerPlayer player, double delta) {
        return ep(player).map(cap -> {
            double value = Math.max(0.0d, cap.getEP() + delta);
            cap.setEP(player, value);
            LOGGER.debug("Adjusted EP for {} -> {}", player.getGameProfile().getName(), value);
            return true;
        }).orElse(false);
    }

    public static boolean setEp(ServerPlayer player, double value) {
        return ep(player).map(cap -> {
            double clamped = Math.max(0.0d, value);
            cap.setEP(player, clamped);
            LOGGER.debug("Set EP for {} -> {}", player.getGameProfile().getName(), clamped);
            return true;
        }).orElse(false);
    }

    public static double getEp(ServerPlayer player) {
        return ep(player).map(ITensuraEPCapability::getEP).orElse(0.0d);
    }

    public static boolean addMagicules(ServerPlayer player, double delta) {
        return tensura(player).map(cap -> {
            double value = Math.max(0.0d, cap.getBaseMagicule() + delta);
            cap.setBaseMagicule(value, player);
            cap.setMagicule(value);
            sync(player);
            return true;
        }).orElse(false);
    }

    public static boolean setMagicules(ServerPlayer player, double value) {
        return tensura(player).map(cap -> {
            double clamped = Math.max(0.0d, value);
            cap.setBaseMagicule(clamped, player);
            cap.setMagicule(clamped);
            sync(player);
            return true;
        }).orElse(false);
    }

    public static boolean addAura(ServerPlayer player, double delta) {
        return tensura(player).map(cap -> {
            double value = Math.max(0.0d, cap.getBaseAura() + delta);
            cap.setBaseAura(value, player);
            cap.setAura(value);
            sync(player);
            return true;
        }).orElse(false);
    }

    public static boolean setAura(ServerPlayer player, double value) {
        return tensura(player).map(cap -> {
            double clamped = Math.max(0.0d, value);
            cap.setBaseAura(clamped, player);
            cap.setAura(clamped);
            sync(player);
            return true;
        }).orElse(false);
    }

    public static double getMagicules(ServerPlayer player) {
        return tensura(player).map(ITensuraPlayerCapability::getBaseMagicule).orElse(0.0d);
    }

    public static double getAura(ServerPlayer player) {
        return tensura(player).map(ITensuraPlayerCapability::getBaseAura).orElse(0.0d);
    }

    public static String getRaceId(ServerPlayer player) {
        return tensura(player).map(cap -> {
            Race race = cap.getRace();
            if (race == null || race.getRegistryName() == null) return "";
            return race.getRegistryName().toString();
        }).orElse("");
    }

    public static boolean isTrueHero(ServerPlayer player) {
        return tensura(player).map(ITensuraPlayerCapability::isTrueHero).orElse(false);
    }

    public static boolean isTrueDemonLord(ServerPlayer player) {
        return tensura(player).map(ITensuraPlayerCapability::isTrueDemonLord).orElse(false);
    }

    public static boolean setRace(ServerPlayer player, ResourceLocation raceId, boolean resetStats) {
        if (raceId == null) return false;
        Race race = TensuraRaces.RACE_REGISTRY.get().getValue(raceId);
        if (race == null) {
            LOGGER.debug("Unknown race id {}", raceId);
            return false;
        }
        return tensura(player).map(cap -> {
            cap.setRace(player, race, resetStats);
            sync(player);
            return true;
        }).orElse(false);
    }

    public static boolean setAwakening(ServerPlayer player, String token, boolean value) {
        if (token == null || token.isBlank()) return false;
        return tensura(player).map(cap -> {
            String lowered = token.toLowerCase();
            switch (lowered) {
                case "true_hero" -> cap.setTrueHero(value);
                case "true_demon_lord" -> cap.setTrueDemonLord(value);
                case "hero_seed" -> cap.setHeroEgg(value);
                case "demon_lord_seed" -> cap.setDemonLordSeed(value);
                case "blessing" -> cap.setBlessed(value);
                default -> {
                    LOGGER.debug("Unknown awakening token {}", token);
                    return false;
                }
            }
            sync(player);
            return true;
        }).orElse(false);
    }

    private static Optional<ITensuraEPCapability> ep(ServerPlayer player) {
        return TensuraEPCapability.getFrom(player).resolve();
    }

    private static Optional<ITensuraPlayerCapability> tensura(ServerPlayer player) {
        return TensuraPlayerCapability.getFrom(player).resolve();
    }

    private static void sync(ServerPlayer player) {
        TensuraPlayerCapability.sync(player);
        TensuraEPCapability.updateEP(player);
    }
}
