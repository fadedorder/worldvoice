package org.faded.worldvoice.ai;

import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.faded.worldvoice.config.WVConfig;
import org.faded.worldvoice.service.SkillService;
import org.faded.worldvoice.util.LogJsonL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
 

/**
 * Wish adjudication routed through Yggdrasil/AI.
 */
public class WishService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WishService.class);
    private static final SkillService SKILL_SERVICE = new SkillService();
    private static final long PREVIEW_SUPPRESSION_MS = 1000L;
    private static final long ATTEMPT_TTL_MS = 5_000L;

    private final Map<UUID, WishAttempt> recentAttempts = new ConcurrentHashMap<>();
    private final WishActionExecutor actionExecutor = new WishActionExecutor();
    private final YggdrasilService yggdrasil;

    public WishService(YggdrasilService yggdrasil) {
        this.yggdrasil = yggdrasil;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onChat(ServerChatEvent.Submitted event) {
        if (!WVConfig.YGG_WISH_ENABLE.get()) return;
        if (event.isCanceled()) return;
        ServerPlayer player = event.getPlayer();
        if (player == null) return;

        String raw = event.getMessage().getString();
        if (raw == null) return;
        String trimmed = raw.trim();
        if (trimmed.isBlank()) return;
        if (trimmed.startsWith("/")) return;
        String lowered = trimmed.toLowerCase(Locale.ROOT);
        if (!lowered.startsWith("i wish")) return;
        LOGGER.debug("[Wish] Matched wish prefix from {}: {}",
                player.getGameProfile().getName(), trimmed);

        long now = System.currentTimeMillis();
        if (shouldSuppress(player.getUUID(), trimmed, now)) {
            return;
        }
        YggdrasilSavedData.PlayerRecord record = yggdrasil.getOrCreateRecord(player);
        long cooldownMs = WVConfig.YGG_WISH_COOLDOWN_MINUTES.get() * 60L * 1000L;
        if (record.lastWishMs + cooldownMs > now) {
            long remaining = record.lastWishMs + cooldownMs - now;
            long minutes = Math.max(1, remaining / 60000L);
            player.sendSystemMessage(Component.literal("The World Voice ignores your plea (cooldown " + minutes + "m)."));
            return;
        }

        record.lastWishMs = now;
        yggdrasil.markDirty(player);

        double strictness = WVConfig.YGG_WISH_STRICTNESS.get();
        double favorScore = record.favorScore(now);
        double effectiveStrictness = clamp01(strictness - Math.min(10.0d, favorScore) * 0.1d);
        boolean hasFavor = record.hasFavor(now);
        boolean requiresFavor = WVConfig.YGG_WISH_REQUIRE_FAVOR.get();
        boolean allowSkillGrants = WVConfig.YGG_WISH_ALLOW_SKILL_GRANTS.get();
        boolean allowItemGrants = WVConfig.YGG_WISH_ALLOW_ITEM_GRANTS.get();

        WishBacklashCalculator.WishOutcome baseline = WishBacklashCalculator.evaluate(
                player, trimmed, effectiveStrictness, favorScore
        );

        WishContext context = new WishContext(
                player.getUUID(),
                yggdrasil.captureProfile(player, null),
                trimmed,
                baseline,
                favorScore,
                effectiveStrictness,
                allowSkillGrants,
                allowItemGrants,
                requiresFavor,
                hasFavor
        );

        dispatch(player, context);
    }

    

    private void dispatch(ServerPlayer player, WishContext context) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        Util.backgroundExecutor().execute(() -> {
            LOGGER.debug("[Wish] Dispatching AI evaluation: {}", context.wishText());
            Optional<WishDecision> aiDecision = yggdrasil.requestWishDecision(
                    context.snapshot(),
                    context.wishText(),
                    context.baseline().intensity(),
                    context.favorScore(),
                    context.effectiveStrictness(),
                    context.allowSkillGrants(),
                    context.allowItemGrants(),
                    context.hasFavor(),
                    context.requiresFavor()
            );

            server.execute(() -> finalize(server, context, aiDecision));
        });
    }

    private void finalize(MinecraftServer server, WishContext context, Optional<WishDecision> aiDecision) {
        ServerPlayer player = server.getPlayerList().getPlayer(context.playerId());
        if (player == null) {
            LOGGER.debug("Wish result dropped – player {} offline.", context.playerId());
            return;
        }

        WishDecision decision = aiDecision.orElseGet(() -> buildFallbackDecision(player, context.baseline(),
                context.requiresFavor(), context.hasFavor()));
        if (context.effectiveStrictness() <= 0.0d) {
            decision = decision.withGrantOverride(true);
        }
        decision = normalizeDecision(decision, context);

        String narrative = decision.narrative();
        boolean applied = applyDecision(player, decision, context);
        LOGGER.debug("[Wish] Finalized for {} -> granted={}, type={}, actions={}, commands={}",
                player.getGameProfile().getName(),
                applied,
                decision.grantType(),
                decision.actions().size(),
                decision.grantCommands().size());
        if (narrative != null && !narrative.isBlank()) {
            sendNarrative(player, narrative, applied);
        }
        if (applied) {
            yggdrasil.signalWish(player, true, context.baseline().intensity());
        } else {
            applyBacklash(player, WishDecision.deny(Math.max(0.4d, decision.backlashStrength()),
                    "The wish cannot take form.", decision.backlashNarrative()), context.baseline());
            yggdrasil.signalWish(player, false, context.baseline().intensity());
        }
        logWish(player, context.wishText(), applied, decision);
    }

    private WishDecision normalizeDecision(WishDecision decision, WishContext context) {
        WishDecision current = decision;
        if (current.grantType() == WishDecision.GrantType.SKILL && !context.allowSkillGrants()) {
            current = WishDecision.boon(
                    Math.max(30, decision.boonSeconds()),
                    decision.backlashStrength(),
                    "Material boons are sealed; only a fleeting resonance manifests."
            );
        } else if (current.grantType() == WishDecision.GrantType.ITEM && !context.allowItemGrants()) {
            current = WishDecision.boon(
                    Math.max(30, decision.boonSeconds()),
                    decision.backlashStrength(),
                    "Objects cannot manifest; the Voice lends temporal strength instead."
            );
        }
        return current;
    }

    private boolean applyDecision(ServerPlayer player, WishDecision decision, WishContext context) {
        if (!decision.granted()) {
            return false;
        }
        boolean applied = switch (decision.grantType()) {
            case SKILL -> context.allowSkillGrants() && grantSkill(player, decision);
            case ITEM -> context.allowItemGrants() && grantItem(player, decision);
            case BOON -> {
                applyBoon(player, decision.boonSeconds(), decision.narrative());
                yield true;
            }
            case NONE -> false;
        };

        applied |= actionExecutor.execute(player, decision.actions(), context.allowSkillGrants());
        applied |= executeCommands(player, decision.grantCommands());

        return applied;
    }

    private boolean grantSkill(ServerPlayer player, WishDecision decision) {
        ResourceLocation id = decision.skillId();
        if (id == null) return false;
        boolean assigned = SKILL_SERVICE.assign(player, id, false, true);
        if (assigned) {
            player.sendSystemMessage(skillAcquiredLine(id));
        }
        return assigned;
    }

    private boolean grantItem(ServerPlayer player, WishDecision decision) {
        ResourceLocation id = decision.itemId();
        if (id == null) return false;
        var item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null) return false;
        int count = Math.max(1, decision.itemCount());
        ItemStack stack = new ItemStack(item, Math.min(count, item.getMaxStackSize()));
        boolean added = player.getInventory().add(stack);
        if (!added) {
            player.drop(stack, false);
        }
        player.sendSystemMessage(Component.literal("Manifested [" + stack.getHoverName().getString() + "].")
                .withStyle(style -> style.withColor(0xb7ffdf)));
        return true;
    }

    private Component skillAcquiredLine(ResourceLocation id) {
        String pretty = prettifyId(id);
        return Component.literal("Unique Skill [" + pretty + "], has been acquired.")
                .withStyle(style -> style.withColor(0xb7ffdf));
    }

    private String prettifyId(ResourceLocation id) {
        String path = id.getPath();
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(parts[i].charAt(0)));
            if (parts[i].length() > 1) {
                sb.append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }

    private void applyBoon(ServerPlayer player, int seconds, String narrative) {
        int duration = Math.max(5, seconds) * 20;
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, duration, 0));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, 0));
    }

    private void applyBacklash(ServerPlayer player, WishDecision decision, WishBacklashCalculator.WishOutcome fallback) {
        boolean executed = executeCommands(player, decision.penaltyCommands());
        double strength = decision.backlashStrength() > 0 ? decision.backlashStrength() : fallback.backlashStrength();
        if (!executed) {
            int duration = (int) (200 + strength * 200);
            int amplifier = (int) Math.min(3, Math.floor(strength * 3));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, amplifier));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, duration, amplifier));
            if (strength > 0.5d) {
                player.addEffect(new MobEffectInstance(MobEffects.UNLUCK, duration * 2, amplifier));
            }
        }
        String message = decision.backlashNarrative();
        if (message == null || message.isBlank()) {
            message = "The Voice disperses your plea, leaving an ache.";
        }
        player.sendSystemMessage(Component.literal(message).withStyle(style -> style.withColor(0xff6b6b)));
    }

    private boolean executeCommands(ServerPlayer player, Iterable<String> commands) {
        boolean ran = false;
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        for (String raw : commands) {
            if (raw == null) continue;
            String cmd = raw.trim();
            if (cmd.isEmpty()) continue;
            while (cmd.startsWith("/")) cmd = cmd.substring(1);
            cmd = cmd.replace("%player%", player.getGameProfile().getName());
            if (!isAllowedCommand(cmd)) {
                LOGGER.debug("Blocked wish command: {}", cmd);
                continue;
            }
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4).withSuppressedOutput(), cmd);
            ran = true;
        }
        return ran;
    }

    private boolean isAllowedCommand(String cmd) {
        String lower = cmd.toLowerCase(Locale.ROOT);
        return lower.startsWith("give ")
                || lower.startsWith("effect ")
                || lower.startsWith("worldvoice ")
                || lower.startsWith("xp ")
                || lower.startsWith("experience ")
                || lower.startsWith("playsound ")
                || lower.startsWith("attribute ")
                || lower.startsWith("data modify entity ")
                || lower.startsWith("title ");
    }

    private void logWish(ServerPlayer player, String wish, boolean granted, WishDecision decision) {
        LogJsonL.append(String.format(Locale.ROOT,
                "{\"ts\":\"%s\",\"event\":\"wish\",\"player\":\"%s\",\"wish\":\"%s\",\"granted\":%s,\"type\":\"%s\",\"detail\":\"%s\",\"actions\":%d}",
                LogJsonL.iso8601(System.currentTimeMillis()),
                player.getUUID(),
                wish.replace("\"", "'"),
                granted,
                decision.grantType(),
                decision.narrative() == null ? "" : decision.narrative().replace("\"", "'"),
                decision.actions().size()));
    }

    private WishDecision buildFallbackDecision(ServerPlayer player, WishBacklashCalculator.WishOutcome baseline,
                                               boolean requiresFavor, boolean hasFavor) {
        boolean grant = !requiresFavor || hasFavor;
        if (grant) {
            double roll = player.getRandom().nextDouble();
            grant = roll < baseline.grantProbability();
        }
        if (grant) {
            int seconds = (int) Math.max(20, 40 + baseline.grantProbability() * 120);
            return WishDecision.boon(seconds, baseline.backlashStrength(), "Grace answers your plea.");
        }
        return WishDecision.deny(baseline.backlashStrength(),
                "The thread of fate remains still.",
                "The Voice stays silent.");
    }

    private double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private boolean shouldSuppress(UUID playerId, String wish, long now) {
        cleanupAttempts(now);
        WishAttempt previous = recentAttempts.get(playerId);
        if (previous != null && previous.text().equalsIgnoreCase(wish) && now - previous.timestamp() < PREVIEW_SUPPRESSION_MS) {
            return true;
        }
        recentAttempts.put(playerId, new WishAttempt(wish, now));
        return false;
    }

    private void cleanupAttempts(long now) {
        recentAttempts.entrySet().removeIf(entry -> now - entry.getValue().timestamp() > ATTEMPT_TTL_MS);
    }

    private void sendNarrative(ServerPlayer player, String narrative, boolean positive) {
        int color = positive ? 0xb7ffdf : 0xff6b6b;
        player.sendSystemMessage(Component.literal(narrative).withStyle(style -> style.withColor(color)));
    }

    private record WishContext(UUID playerId,
                               PlayerProfileSnapshot snapshot,
                               String wishText,
                               WishBacklashCalculator.WishOutcome baseline,
                               double favorScore,
                               double effectiveStrictness,
                               boolean allowSkillGrants,
                               boolean allowItemGrants,
                               boolean requiresFavor,
                               boolean hasFavor) {
    }

    private record WishAttempt(String text, long timestamp) {}
}
