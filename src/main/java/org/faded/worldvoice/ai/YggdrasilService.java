package org.faded.worldvoice.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.faded.worldvoice.config.WVConfig;
import org.faded.worldvoice.core.SkillCategory;
import org.faded.worldvoice.core.SkillConfigManager;
import org.faded.worldvoice.core.SlotLedger;
import org.faded.worldvoice.integration.TensuraBridge;
import org.faded.worldvoice.service.SkillService;
import org.faded.worldvoice.util.LogJsonL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Central orchestrator for periodic AI-driven unique skill assignments and favor management.
 */
public class YggdrasilService {
    private static final Logger LOGGER = LoggerFactory.getLogger(YggdrasilService.class);

    private final SkillService skillService = new SkillService();
    private final AIClient aiClient = new AIClient();
    private final Map<UUID, QueueEntry> pending = new ConcurrentHashMap<>();
    private final Map<UUID, CopyOnWriteArrayList<CommandSourceStack>> evaluationListeners = new ConcurrentHashMap<>();
    private final Map<UUID, List<NearbyPlayerMetrics>> awakeningInsights = new ConcurrentHashMap<>();
    private final Map<UUID, ArrayDeque<Long>> advancementHistory = new ConcurrentHashMap<>();
    private final AtomicBoolean dictionaryLoaded = new AtomicBoolean(false);
    private final ScheduledExecutorService intervalExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "WorldVoice-Yggdrasil-Timer");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean intervalSchedulerRunning = new AtomicBoolean(false);
    private ScheduledFuture<?> intervalTask;
    private final AtomicInteger consecutiveAiFailures = new AtomicInteger(0);
    private volatile long aiCircuitUntilMs = 0L;

    private SkillDictionary dictionary;
    private long lastRunMs = 0L;
    private long dictionaryLastAttempt = 0L;
    private static final long ADVANCEMENT_WINDOW_MS = 60L * 60L * 1000L;

    private SkillDictionary ensureDictionary() {
        if (dictionaryLoaded.compareAndSet(false, true) || staleDictionary()) {
            reloadDictionary();
        }
        return dictionary;
    }

    private boolean staleDictionary() {
        long now = System.currentTimeMillis();
        long interval = 5 * 60 * 1000L;
        return now - dictionaryLastAttempt > interval;
    }

    public void reloadDictionary() {
        dictionaryLastAttempt = System.currentTimeMillis();
        Path configured = Path.of(WVConfig.YGG_DICTIONARY_PATH.get());
        InputStream fallback = YggdrasilService.class.getResourceAsStream("/defaultconfigs/worldvoice/yggdrasil-skills.json5");
        dictionary = SkillDictionary.load(configured, fallback);
    }

    @SubscribeEvent
    public void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!WVConfig.YGG_ENABLE.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ResourceLocation advancementId = event.getAdvancement() != null ? event.getAdvancement().getId() : null;
        queue(player, QueueReason.ADVANCEMENT, false, advancementId);
        recordAdvancement(player.getUUID(), System.currentTimeMillis());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!WVConfig.YGG_ENABLE.get()) return;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        runEvaluationCycle(server, false);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        startIntervalScheduler();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        stopIntervalScheduler();
    }

    private void runEvaluationCycle(MinecraftServer server, boolean schedulerTriggered) {
        if (!WVConfig.YGG_ENABLE.get()) return;
        long now = System.currentTimeMillis();
        cleanupQueues(server, now);
        long intervalMs = Math.max(60_000L, WVConfig.YGG_INTERVAL_MINUTES.get() * 60L * 1000L);
        if (lastRunMs != 0 && now - lastRunMs < intervalMs) {
            return;
        }
        if (server.getPlayerList().getPlayerCount() < WVConfig.YGG_MIN_PLAYERS.get()) {
            return;
        }
        QueueEntry entry = selectNextEntry(server, now);
        if (entry == null) {
            return;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(entry.playerId());
        if (target == null) {
            pending.remove(entry.playerId());
            return;
        }
        pending.remove(entry.playerId());
        lastRunMs = now;
        evaluate(server, target, entry, now);
    }

    private QueueEntry selectNextEntry(MinecraftServer server, long now) {
        QueueEntry best = pending.values().stream()
                .min(Comparator.comparingInt((QueueEntry e) -> e.reason.priority)
                        .thenComparingLong(e -> e.createdMs))
                .orElse(null);
        if (best != null) {
            return best;
        }
        if (WVConfig.YGG_REQUIRE_EVENT.get()) {
            return null;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return null;
        ServerLevel level = server.overworld();
        SlotLedger ledger = SlotLedger.get(level);
        YggdrasilSavedData data = YggdrasilSavedData.get(level);
        Set<UUID> pendingIds = pending.keySet();
        ServerPlayer fallback = null;
        long bestScore = Long.MIN_VALUE;
        for (ServerPlayer candidate : players) {
            UUID id = candidate.getUUID();
            if (pendingIds.contains(id)) continue;
            int recentAdv = countRecentAdvancements(id, now);
            YggdrasilSavedData.PlayerRecord record = data.getOrCreate(id);
            long sinceEval = record.lastEvaluationMs == 0 ? Long.MAX_VALUE : now - record.lastEvaluationMs;
            SlotLedger.PlayerRecord ledgerRecord = ledger.getOrCreatePlayer(id, candidate.getGameProfile().getName());
            List<SkillCandidate> preview = collectCandidates(level, ledger, ledgerRecord, id, QueueReason.PASSIVE);
            if (preview.isEmpty()) continue;
            long score = Math.min(sinceEval, Long.MAX_VALUE / 4) + (long) recentAdv * 1_000L + preview.size() * 100L;
            if (score > bestScore) {
                bestScore = score;
                fallback = candidate;
            }
        }
        if (fallback == null) {
            return null;
        }
        QueueEntry entry = new QueueEntry(fallback.getUUID(), QueueReason.PASSIVE, now, false, null);
        pending.put(fallback.getUUID(), entry);
        return entry;
    }

    private void evaluate(MinecraftServer server, ServerPlayer player, QueueEntry entry, long now) {
        ensureDictionary();
        ServerLevel level = (ServerLevel) player.getLevel();
        SlotLedger ledger = SlotLedger.get(level);
        UUID playerId = player.getUUID();
        SlotLedger.PlayerRecord ledgerRecord = ledger.getOrCreatePlayer(playerId, player.getGameProfile().getName());
        YggdrasilSavedData data = YggdrasilSavedData.get(level);
        YggdrasilSavedData.PlayerRecord aiRecord = data.getOrCreate(playerId);

        int assignedUnique = countAssignments(ledger, playerId, SkillCategory.UNIQUE);
        int cap = WVConfig.YGG_PER_PLAYER_UNIQUE_CAP.get();
        if (aiRecord.uniqueCapOverride >= 0) {
            cap = aiRecord.uniqueCapOverride;
        }
        boolean hasFavor = aiRecord.hasFavor(now);
        if (cap > 0 && assignedUnique >= cap && !hasFavor) {
            LOGGER.debug("Skipping {} – unique cap {} reached.", player.getGameProfile().getName(), cap);
            sendEvaluationMessage(player, "The Voice cannot grant further uniques; destiny is at its limit.", null);
            return;
        }

        final List<SkillCandidate> candidates = collectCandidates(level, ledger, ledgerRecord, playerId, entry.reason);
        if (candidates.isEmpty()) {
            LOGGER.debug("No eligible skills for {}", player.getGameProfile().getName());
            sendEvaluationMessage(player, "The Voice finds no suitable blessing for your current path.", null);
            return;
        }

        PlayerProfileSnapshot profile = captureProfile(player, entry.contextAdvancement(), aiRecord, assignedUnique, now);
        List<NearbyPlayerMetrics> nearbyMetrics = awakeningInsights.remove(playerId);
        double favorMultiplier = aiRecord.favorMultiplier(now);

        String apiKey = WVConfig.YGG_OPENROUTER_KEY.get();
        boolean aiAllowed = apiKey != null && !apiKey.isBlank() && canUseAi(now);
        if (!aiAllowed) {
            if (apiKey != null && !apiKey.isBlank() && !canUseAi(now)) {
                LOGGER.debug("Yggdrasil AI circuit open; falling back to heuristic decision for {}.", player.getGameProfile().getName());
            }
            ResourceLocation chosen = pickHeuristic(player.getRandom(), candidates, profile, favorMultiplier, entry.reason);
            sendEvaluationMessage(player, null, chosen);
            if (chosen != null) {
                finishAssignment(level, player, aiRecord, chosen, entry.reason, "heuristic", now, null);
                if (WVConfig.YGG_LOG_DECISIONS.get()) {
                    logDecision(now, player, "assign", chosen, entry.reason, "heuristic");
                }
            }
            return;
        }

        SkillDictionary dict = dictionary;
        String prompt = buildPrompt(player, profile, aiRecord, entry, candidates, favorMultiplier, dict, nearbyMetrics);
        AIClient.AIRequest request = new AIClient.AIRequest(
                WVConfig.YGG_OPENROUTER_MODEL.get(),
                apiKey,
                WVConfig.YGG_OPENROUTER_MAX_TOKENS.get(),
                WVConfig.YGG_OPENROUTER_TEMPERATURE.get(),
                buildSystemPrompt(),
                prompt,
                WVConfig.YGG_OPENROUTER_REFERER.get(),
                WVConfig.YGG_OPENROUTER_TITLE.get()
        );

        LOGGER.debug("Yggdrasil evaluation queued for {} (reason {}, candidates={})",
                player.getGameProfile().getName(), entry.reason, candidates.size());
        Util.backgroundExecutor().execute(() -> {
            var responseOpt = aiClient.requestDecision(request);
            long responseTime = System.currentTimeMillis();
            server.execute(() -> {
                if (responseOpt.isPresent()) {
                    noteAiSuccess();
                } else {
                    noteAiFailure(responseTime);
                }
                AIClient.DecisionResponse response = responseOpt.orElse(null);
                ResourceLocation decisionId = null;
                boolean denied = false;
                String narrative = null;
                if (response != null && !response.deny()) {
                    decisionId = response.skillId();
                    narrative = response.rationale();
                } else if (response != null && response.deny()) {
                    denied = true;
                    narrative = response.denyReason();
                }

                if (decisionId == null) {
                    decisionId = pickHeuristic(player.getRandom(), candidates, profile, favorMultiplier, entry.reason);
                } else if (!containsCandidate(candidates, decisionId)) {
                    LOGGER.debug("AI suggested {}, which is not an eligible candidate; falling back.", decisionId);
                    decisionId = pickHeuristic(player.getRandom(), candidates, profile, favorMultiplier, entry.reason);
                }

                sendEvaluationMessage(player, narrative, decisionId);

                if (decisionId == null) {
                    if (denied && WVConfig.YGG_LOG_DECISIONS.get()) {
                        logDecision(now, player, "deny", null, entry.reason, narrative);
                    }
                    return;
                }

                finishAssignment(level, player, aiRecord, decisionId, entry.reason,
                        response != null ? "openrouter" : "heuristic", now, narrative);
                if (WVConfig.YGG_LOG_DECISIONS.get()) {
                    logDecision(now, player, "assign", decisionId, entry.reason, narrative);
                }
            });
        });
    }

    public boolean forceEvaluate(ServerPlayer player, QueueReason reason, CommandSourceStack listener) {
        if (listener != null) {
            evaluationListeners.computeIfAbsent(player.getUUID(), id -> new CopyOnWriteArrayList<>()).add(listener);
        }
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        long now = System.currentTimeMillis();
        evaluate(server, player, new QueueEntry(player.getUUID(), reason, now, true, null), now);
        return true;
    }

    public boolean forceEvaluate(ServerPlayer player, QueueReason reason) {
        return forceEvaluate(player, reason, null);
    }

    public boolean forceEvaluate(ServerPlayer player) {
        return forceEvaluate(player, QueueReason.MANUAL, null);
    }

    public PlayerProfileSnapshot captureProfile(ServerPlayer player, ResourceLocation lastAdvancement) {
        if (player == null) return null;
        ServerLevel level = (ServerLevel) player.getLevel();
        SlotLedger ledger = SlotLedger.get(level);
        YggdrasilSavedData data = YggdrasilSavedData.get(level);
        YggdrasilSavedData.PlayerRecord record = data.getOrCreate(player.getUUID());
        long now = System.currentTimeMillis();
        int uniqueOwned = countAssignments(ledger, player.getUUID(), SkillCategory.UNIQUE);
        return captureProfile(player, lastAdvancement, record, uniqueOwned, now);
    }

    private PlayerProfileSnapshot captureProfile(ServerPlayer player, ResourceLocation lastAdvancement,
                                                 YggdrasilSavedData.PlayerRecord aiRecord, int uniqueOwned, long now) {
        PlayerProfileSnapshot.Extras extras = new PlayerProfileSnapshot.Extras(
                countRecentAdvancements(player.getUUID(), now),
                aiRecord.lastEvaluationMs,
                uniqueOwned
        );
        return PlayerProfileSnapshot.capture(player, lastAdvancement, extras);
    }

    private void finishAssignment(ServerLevel level, ServerPlayer player, YggdrasilSavedData.PlayerRecord aiRecord,
                                  ResourceLocation id, QueueReason reason, String source, long now, String narrative) {
        boolean assigned = skillService.assign(player, id, false, true);
        if (!assigned) {
            LOGGER.debug("Yggdrasil failed to assign {} to {}", id, player.getGameProfile().getName());
            return;
        }
        aiRecord.uniqueGranted++;
        aiRecord.lastEvaluationMs = now;
        YggdrasilSavedData.get(level).setDirty();
        LogJsonL.append(String.format(Locale.ROOT,
                "{\"ts\":\"%s\",\"event\":\"yggdrasil-assign\",\"skillId\":\"%s\",\"player\":\"%s\",\"reason\":\"%s\",\"source\":\"%s\"}",
                LogJsonL.iso8601(now), id, player.getUUID(), reason.name().toLowerCase(Locale.ROOT), source));
    }

    private void logDecision(long now, ServerPlayer player, String event, ResourceLocation id,
                             QueueReason reason, String rationale) {
        LogJsonL.append(String.format(Locale.ROOT,
                "{\"ts\":\"%s\",\"event\":\"yggdrasil-%s\",\"skillId\":\"%s\",\"player\":\"%s\",\"reason\":\"%s\",\"detail\":\"%s\"}",
                LogJsonL.iso8601(now),
                event,
                id == null ? "" : id,
                player.getUUID(),
                reason.name().toLowerCase(Locale.ROOT),
                rationale == null ? "" : rationale.replace("\"", "'")));
    }

    private List<SkillCandidate> collectCandidates(ServerLevel level, SlotLedger ledger,
                                                   SlotLedger.PlayerRecord ledgerRecord,
                                                   UUID playerId, QueueReason reason) {
        List<SkillCandidate> list = new ArrayList<>();
        for (ResourceLocation id : SkillConfigManager.INSTANCE.getManagedSkills()) {
            SkillCategory cat = SkillConfigManager.INSTANCE.classify(id);
            if (cat != SkillCategory.UNIQUE) continue;
            if (!org.faded.worldvoice.world.WorldVoiceGameRules.isEnabled(level, cat)) continue;
            if (SkillConfigManager.INSTANCE.isLearningBlacklisted(cat, id) && !ledgerRecord.hasBlacklistBypass(cat, id)) {
                continue;
            }
            SlotLedger.SkillState state = ledger.getOrCreateSkill(cat, id);
            state.capacity = SkillConfigManager.INSTANCE.getCapacity(cat, id);
            boolean already = state.holders.contains(playerId);
            if (already) continue;
            boolean bypass = ledgerRecord.hasBypass(cat, id);
            if (!bypass && state.capacity >= 0 && state.holders.size() >= state.capacity) {
                continue;
            }
            list.add(new SkillCandidate(id, cat, state.capacity, state.holders.size(), reason));
        }
        return list;
    }

    private ResourceLocation pickHeuristic(RandomSource random, List<SkillCandidate> candidates,
                                           PlayerProfileSnapshot profile, double favorMultiplier,
                                           QueueReason reason) {
        if (candidates.isEmpty()) return null;
        SkillDictionary dict = dictionary;
        double totalWeight = 0.0d;
        double[] weights = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            SkillCandidate candidate = candidates.get(i);
            double weight = computeScore(candidate, profile, favorMultiplier, reason, dict);
            weights[i] = weight;
            totalWeight += weight;
        }
        if (totalWeight <= 0.0d) {
            return candidates.get(random.nextInt(candidates.size())).id();
        }
        double roll = random.nextDouble() * totalWeight;
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll <= 0) {
                return candidates.get(i).id();
            }
        }
        return candidates.get(candidates.size() - 1).id();
    }

    private double computeScore(SkillCandidate candidate, PlayerProfileSnapshot profile,
                                double favorMultiplier, QueueReason reason, SkillDictionary dict) {
        double weight = 1.0d;
        // Disregard old dictionary weight system; keep a neutral baseline.
        // Optionally nudge based on tier, but keep the effect minimal for fallback.
        if (dict != null) {
            var metaOpt = dict.lookup(candidate.id());
            if (metaOpt.isPresent()) {
                int tier = Math.max(1, Math.min(5, metaOpt.get().tier()));
                weight = 1.0d + (tier - 3) * 0.05d; // small +/- bias
            }
        }
        weight += profile.experienceLevel() / 30.0d;
        weight += Math.min(2.0d, profile.playTicks() / 72000.0d);
        if (profile.hasTeam()) weight += 0.5d;
        if (reason == QueueReason.AWAKENING_DEMON || reason == QueueReason.AWAKENING_HERO) {
            weight += 2.0d * favorMultiplier;
        } else if (reason == QueueReason.SUBORDINATE) {
            weight += 1.5d * favorMultiplier;
        } else {
            weight += favorMultiplier;
        }
        return Math.max(0.1d, weight);
    }

    private String buildPrompt(ServerPlayer player, PlayerProfileSnapshot profile,
                               YggdrasilSavedData.PlayerRecord record, QueueEntry entry,
                               List<SkillCandidate> candidates, double favorMultiplier,
                               SkillDictionary dict, List<NearbyPlayerMetrics> nearbyMetrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("Player ").append(player.getGameProfile().getName()).append(" seeks guidance.\n");
        sb.append("Stats: level=").append(profile.experienceLevel())
                .append(", deaths=").append(profile.totalDeaths())
                .append(", playTicks=").append(profile.playTicks())
                .append(", distance=").append(profile.distanceTravelled())
                .append(", hasTeam=").append(profile.hasTeam()).append("\n");
        sb.append("RecentAdvancements=").append(profile.recentAdvancements())
                .append(", uniquesOwned=").append(profile.uniqueOwned())
                .append(", lastEvaluationMs=").append(profile.lastEvaluationMs()).append("\n");
        sb.append("Essence: ep=").append(Math.round(profile.ep()))
                .append(", magicules=").append(Math.round(profile.magicules()))
                .append(", aura=").append(Math.round(profile.aura()))
                .append(", race=").append(profile.raceId().isBlank() ? "unknown" : profile.raceId())
                .append(", hero=").append(profile.trueHero())
                .append(", demon=").append(profile.trueDemonLord()).append("\n");
        sb.append("Trigger=").append(entry.reason.name()).append(", favorMultiplier=").append(favorMultiplier).append("\n");
        if (profile.lastAdvancement() != null) {
            sb.append("LatestAdvancement=").append(profile.lastAdvancement()).append("\n");
        }
        sb.append("Eligible skills:\n");
        for (SkillCandidate candidate : candidates) {
            sb.append("- ").append(candidate.id()).append(" slots(")
                    .append(candidate.occupied()).append("/").append(candidate.capacity()).append(")");
            dict.lookup(candidate.id()).ifPresent(meta -> {
                sb.append(" name=").append(meta.name())
                  .append(" tier=").append(meta.tier());
                if (meta.evolution() != null) sb.append(" evolution=").append(meta.evolution());
                if (!meta.tags().isEmpty()) sb.append(" tags=").append(meta.tags());
                if (!meta.notes().isBlank()) sb.append(" note=").append(meta.notes());
            });
            sb.append("\n");
        }
        if (nearbyMetrics != null && !nearbyMetrics.isEmpty()) {
            sb.append("Nearby players touched by the awakening:\n");
            for (NearbyPlayerMetrics metrics : nearbyMetrics) {
                sb.append("- ").append(metrics.name())
                        .append(" dist=").append(String.format(Locale.ROOT, "%.1f", metrics.distance()))
                        .append(" level=").append(metrics.level())
                        .append(" ep=").append(Math.round(metrics.ep()))
                        .append(" uniques=").append(metrics.uniqueOwned())
                        .append(" score=").append(String.format(Locale.ROOT, "%.1f", metrics.statScore()));
                if (metrics.evolvedFrom() != null && metrics.evolvedTo() != null) {
                    sb.append(" evolved=").append(metrics.evolvedFrom()).append("->").append(metrics.evolvedTo());
                }
                if (metrics.queued()) {
                    sb.append(" queuedForEvaluation=true");
                }
                sb.append("\n");
            }
        }
        sb.append("Respond with JSON: {\"skillId\":\"namespace:path\",\"rationale\":\"...\",\"deny\":false}.");
        return sb.toString();
    }

    public Optional<WishDecision> requestWishDecision(PlayerProfileSnapshot profile, String wishText,
                                                      double intensity, double favorScore,
                                                      double effectiveStrictness,
                                                      boolean allowSkills, boolean allowItems,
                                                      boolean hasFavor, boolean requiresFavor) {
        String apiKey = WVConfig.YGG_OPENROUTER_KEY.get();
        long now = System.currentTimeMillis();
        if (apiKey == null || apiKey.isBlank() || !canUseAi(now)) return Optional.empty();
        SkillDictionary dict = ensureDictionary();
        String system = """
                You are Yggdrasil, the Voice of the World from Tensura.
                Judge the wish with divine neutrality. Consider the player's history, favor, strictness, and intensity.
                Narratives must be short (<=2 sentences) and ethereal.
                Output JSON ONLY:
                {
                  "grant": true|false,
                  "grantType": "skill|item|boon|deny",
                  "skillId": "namespace:path",
                  "itemId": "namespace:path",
                  "itemCount": 1,
                  "boonSeconds": 60,
                  "backlash": 0..1,
                  "narrative": "lore prose for the outcome",
                  "backlashNarrative": "if denied or consequences apply",
                  "grantCommands": ["give %player% minecraft:apple 1", ...],
                  "penaltyCommands": ["effect give %player% minecraft:weakness 60 1", ...],
                  "actions": [
                    {"type":"ep|magicules|aura|skill|evolution|awakening","op":"add|set|grant|revoke|form","value":number,"skillId":"namespace:path","payload":"context string"}
                  ]
                }
                grantCommands/penaltyCommands may be empty arrays. Commands MUST use %player% as a placeholder and only use safe verbs (give, effect, xp/experience, worldvoice, playsound, attribute, data modify entity, title).
                If grant=false, describe the consequence and include penaltyCommands when appropriate.
                Prefer using typed actions for EP/magicules/aura/race changes when possible.
                Weigh intensity and favor: greater intensity means harsher backlash when denying.
                """;
        StringBuilder user = new StringBuilder();
        user.append("Player=").append(profile.name())
                .append(" level=").append(profile.experienceLevel())
                .append(" deaths=").append(profile.totalDeaths())
                .append(" playTicks=").append(profile.playTicks())
                .append(" ep=").append(Math.round(profile.ep()))
                .append(" magicules=").append(Math.round(profile.magicules()))
                .append(" aura=").append(Math.round(profile.aura()))
                .append(" race=").append(profile.raceId().isBlank() ? "unknown" : profile.raceId())
                .append(" hero=").append(profile.trueHero())
                .append(" demon=").append(profile.trueDemonLord())
                .append(" favorScore=").append(favorScore)
                .append(" strictness=").append(effectiveStrictness)
                .append(" intensity=").append(intensity)
                .append(" hasFavor=").append(hasFavor)
                .append(" requiresFavor=").append(requiresFavor)
                .append(" allowSkills=").append(allowSkills)
                .append(" allowItems=").append(allowItems)
                .append("\nWish: ").append(wishText);
        if (dict != null && allowSkills) {
            user.append("\nSampleUniqueSkills: ");
            dict.all().stream().limit(5).forEach(meta -> user.append(meta.id()).append(" "));
        }
        user.append("\nAllowedCommands=give,effect,worldvoice,xp,experience,playsound,attribute,data modify entity,title. Use %player% placeholder.");
        user.append("\nTypedActions: ")
                .append("ep(op:add|set,value)")
                .append(", magicules(op:add|set,value)")
                .append(", aura(op:add|set,value)")
                .append(", skill(op:grant|revoke,skillId)")
                .append(", evolution(op:set,skillId=race id)")
                .append(", awakening(payload=true_hero|true_demon_lord|blessing,value 1 enable 0 disable).")
                .append("\nStrictness 0 means automatic approval.");
        AIClient.AIRequest request = new AIClient.AIRequest(
                WVConfig.YGG_OPENROUTER_MODEL.get(),
                apiKey,
                WVConfig.YGG_OPENROUTER_MAX_TOKENS.get(),
                WVConfig.YGG_OPENROUTER_TEMPERATURE.get(),
                system,
                user.toString(),
                WVConfig.YGG_OPENROUTER_REFERER.get(),
                WVConfig.YGG_OPENROUTER_TITLE.get()
        );
        var response = aiClient.requestJsonResponse(request);
        long responseTime = System.currentTimeMillis();
        if (response.isPresent()) {
            noteAiSuccess();
        } else {
            noteAiFailure(responseTime);
        }
        return response.flatMap(json -> parseWishDecision(json, intensity));
    }

    private Optional<WishDecision> parseWishDecision(JsonObject json, double fallbackBacklash) {
        try {
            boolean grant = json.has("grant") && json.get("grant").getAsBoolean();
            String typeRaw = json.has("grantType") ? json.get("grantType").getAsString() : "boon";
            WishDecision.GrantType type = switch (typeRaw.toLowerCase(Locale.ROOT)) {
                case "skill" -> WishDecision.GrantType.SKILL;
                case "item" -> WishDecision.GrantType.ITEM;
                case "boon" -> WishDecision.GrantType.BOON;
                default -> WishDecision.GrantType.NONE;
            };
            ResourceLocation skillId = json.has("skillId") ? ResourceLocation.tryParse(json.get("skillId").getAsString()) : null;
            ResourceLocation itemId = json.has("itemId") ? ResourceLocation.tryParse(json.get("itemId").getAsString()) : null;
            int itemCount = json.has("itemCount") ? Math.max(1, json.get("itemCount").getAsInt()) : 1;
            int boonSeconds = json.has("boonSeconds") ? Math.max(5, json.get("boonSeconds").getAsInt()) : 60;
            double backlash = json.has("backlash") ? Math.max(0.0d, Math.min(1.0d, json.get("backlash").getAsDouble())) : Math.min(1.0d, fallbackBacklash);
            String rationale = json.has("rationale") ? json.get("rationale").getAsString() : "";
            String backlashNarrative = json.has("backlashNarrative") ? json.get("backlashNarrative").getAsString() : "";
            var grantCommands = asStringList(json, "grantCommands");
            var penaltyCommands = asStringList(json, "penaltyCommands");
            var actions = parseActions(json);
            return Optional.of(new WishDecision(grant, type, skillId, itemId, itemCount, boonSeconds,
                    backlash, rationale, backlashNarrative, grantCommands, penaltyCommands, actions));
        } catch (Exception ex) {
            LOGGER.warn("Failed to parse wish decision JSON: {}", ex.toString());
            return Optional.empty();
        }
    }

    private String buildSystemPrompt() {
        return """
                You are Yggdrasil, the Voice of the World from Tensura.
                Speak as an ancient, impartial oracle that weighs destiny, deeds, and balance.
                Narratives must be short (<=2 sentences) and ethereal.
                When judging a player, choose at most one unique skill from the candidates or deny them.
                Respond in JSON:
                {
                  "skillId":"namespace:path or empty",
                  "rationale":"lore-friendly prose",
                  "deny":false,
                  "denyReason":"used only if deny=true"
                }
                No prose outside the JSON. Never invent skill ids.
                """;
    }

    private int countAssignments(SlotLedger ledger, UUID playerId, SkillCategory cat) {
        int total = 0;
        for (SlotLedger.SkillState state : ledger.categories.get(cat).values()) {
            if (state.holders.contains(playerId)) total++;
        }
        return total;
    }

    private void queue(ServerPlayer player, QueueReason reason, boolean urgent, ResourceLocation ctx) {
        UUID id = player.getUUID();
        QueueEntry entry = new QueueEntry(id, reason, System.currentTimeMillis(), urgent, ctx);
        pending.compute(id, (uuid, existing) -> {
            if (existing == null) return entry;
            if (urgent || reason.priority < existing.reason.priority) {
                return entry;
            }
            return existing;
        });
    }

    private void applyAwakeningEffects(ServerPlayer source, boolean trueHero) {
        MinecraftServer server = source.getServer();
        if (server == null) return;
        double radius = WVConfig.YGG_AWAKENING_RADIUS.get();
        int maxNearby = WVConfig.YGG_AWAKENING_MAX_NEARBY.get();
        if (radius <= 0 || maxNearby <= 0) {
            awakeningInsights.remove(source.getUUID());
            return;
        }
        String flavor = trueHero ? "heroic radiance" : "demonic maelstrom";
        ServerLevel level = (ServerLevel) source.getLevel();
        SlotLedger ledger = SlotLedger.get(level);
        SkillDictionary dict = ensureDictionary();
        List<NearbyPlayerMetrics> metrics = new ArrayList<>();
        for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
            if (candidate == source) continue;
            if (candidate.getLevel() != source.getLevel()) continue;
            double distance = candidate.distanceTo(source);
            if (distance > radius) continue;
            if (metrics.size() >= maxNearby) break;
            PlayerProfileSnapshot snapshot = PlayerProfileSnapshot.capture(candidate, null);
            double epValue = TensuraBridge.getEp(candidate);
            double multiplier = WVConfig.YGG_AWAKENING_EP_MULTIPLIER.get();
            if (multiplier > 1.0d) {
                double boosted = epValue * multiplier;
                TensuraBridge.setEp(candidate, boosted);
                epValue = boosted;
            }
            EvolutionResult evolution = WVConfig.YGG_AWAKENING_EVOLVE.get()
                    ? tryEvolveSubordinate(candidate, ledger, dict)
                    : EvolutionResult.NONE;
            double score = computeSubordinateScore(snapshot, epValue);
            boolean queued = false;
            if (WVConfig.YGG_AWAKENING_QUEUE_SUBORDINATES.get()
                    && score >= WVConfig.YGG_AWAKENING_QUEUE_THRESHOLD.get()) {
                queue(candidate, QueueReason.SUBORDINATE, true, null);
                queued = true;
            }
            int uniqueOwned = countAssignments(ledger, candidate.getUUID(), SkillCategory.UNIQUE);
            metrics.add(new NearbyPlayerMetrics(
                    candidate.getGameProfile().getName(),
                    snapshot.experienceLevel(),
                    epValue,
                    uniqueOwned,
                    distance,
                    score,
                    evolution.from(),
                    evolution.to(),
                    queued
            ));
            candidate.sendSystemMessage(Component.literal("A " + flavor + " washes over you, empowering your spirit.")
                    .withStyle(style -> style.withColor(0xb7ffdf)));
        }
        if (metrics.isEmpty()) {
            awakeningInsights.remove(source.getUUID());
        } else {
            awakeningInsights.put(source.getUUID(), metrics);
        }
    }

    private EvolutionResult tryEvolveSubordinate(ServerPlayer player, SlotLedger ledger, SkillDictionary dict) {
        if (dict == null) return EvolutionResult.NONE;
        UUID playerId = player.getUUID();
        Map<ResourceLocation, SlotLedger.SkillState> uniqueStates = ledger.categories.get(SkillCategory.UNIQUE);
        for (Map.Entry<ResourceLocation, SlotLedger.SkillState> entry : uniqueStates.entrySet()) {
            if (!entry.getValue().holders.contains(playerId)) continue;
            var metaOpt = dict.lookup(entry.getKey());
            if (metaOpt.isEmpty()) continue;
            ResourceLocation evolution = metaOpt.get().evolution();
            if (evolution == null) continue;
            if (ownsSkill(ledger, playerId, SkillCategory.UNIQUE, evolution)) continue;
            boolean assigned = skillService.assign(player, evolution, true, true);
            if (!assigned) continue;
            skillService.revoke(player, entry.getKey(), "awakening-evolution");
            return new EvolutionResult(entry.getKey(), evolution);
        }
        return EvolutionResult.NONE;
    }

    private boolean ownsSkill(SlotLedger ledger, UUID playerId, SkillCategory category, ResourceLocation skillId) {
        Map<ResourceLocation, SlotLedger.SkillState> map = ledger.categories.get(category);
        if (map == null) return false;
        SlotLedger.SkillState state = map.get(skillId);
        return state != null && state.holders.contains(playerId);
    }

    private double computeSubordinateScore(PlayerProfileSnapshot snapshot, double currentEp) {
        double playHours = snapshot.playTicks() / 72000.0d;
        return snapshot.experienceLevel() + (currentEp / 1000.0d) + playHours;
    }

    public void signalAwakening(ServerPlayer player, boolean trueHero) {
        setFavor(player, 15 * 60 * 1000L, trueHero ? 8.0d : 6.0d,
                trueHero ? "true_hero" : "true_demon_lord");
        queue(player, trueHero ? QueueReason.AWAKENING_HERO : QueueReason.AWAKENING_DEMON, true, null);
        applyAwakeningEffects(player, trueHero);
    }

    public void signalWish(ServerPlayer player, boolean granted, double intensity) {
        if (granted) {
            long duration = (long) (5 * 60 * 1000L * Math.max(0.5d, intensity));
            double bump = Math.max(0.5d, intensity * 2.0d);
            bumpFavor(player, duration, bump, "wish");
        } else if (intensity > 0.75d) {
            bumpFavor(player, 60_000L, -1.0d, "wish_deny");
        }
        queue(player, QueueReason.WISH, granted, null);
    }

    public void setFavor(ServerPlayer player, long durationMs, double strength, String context) {
        ServerLevel level = (ServerLevel) player.getLevel();
        YggdrasilSavedData data = YggdrasilSavedData.get(level);
        YggdrasilSavedData.PlayerRecord record = data.getOrCreate(player.getUUID());
        record.favorUntilMs = System.currentTimeMillis() + durationMs;
        record.favorStrength = Math.max(0.0d, Math.min(10.0d, strength));
        record.favorContext = context;
        data.setDirty();
    }

    private void bumpFavor(ServerPlayer player, long durationMs, double delta, String context) {
        ServerLevel level = (ServerLevel) player.getLevel();
        YggdrasilSavedData data = YggdrasilSavedData.get(level);
        YggdrasilSavedData.PlayerRecord record = data.getOrCreate(player.getUUID());
        long now = System.currentTimeMillis();
        record.favorStrength = Math.max(0.0d, Math.min(10.0d, record.favorStrength + delta));
        record.favorUntilMs = Math.max(record.favorUntilMs, now + durationMs);
        record.favorContext = context;
        data.setDirty();
    }

    public void clearFavor(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.getLevel();
        YggdrasilSavedData data = YggdrasilSavedData.get(level);
        YggdrasilSavedData.PlayerRecord record = data.getOrCreate(player.getUUID());
        record.favorUntilMs = 0L;
        record.favorStrength = 0.0d;
        record.favorContext = "";
        data.setDirty();
    }

    public void setWishCooldown(ServerPlayer player, long remainingMinutes) {
        ServerLevel level = (ServerLevel) player.getLevel();
        YggdrasilSavedData data = YggdrasilSavedData.get(level);
        YggdrasilSavedData.PlayerRecord record = data.getOrCreate(player.getUUID());
        long cooldownWindowMs = WVConfig.YGG_WISH_COOLDOWN_MINUTES.get() * 60_000L;
        long desiredRemainingMs = Math.max(0L, remainingMinutes * 60_000L);
        long clamped = Math.min(cooldownWindowMs, desiredRemainingMs);
        long now = System.currentTimeMillis();
        record.lastWishMs = now - (cooldownWindowMs - clamped);
        data.setDirty();
    }

    public YggdrasilSavedData.PlayerRecord getOrCreateRecord(ServerPlayer player) {
        return YggdrasilSavedData.get((ServerLevel) player.getLevel()).getOrCreate(player.getUUID());
    }

    public void markDirty(ServerPlayer player) {
        YggdrasilSavedData.get((ServerLevel) player.getLevel()).setDirty();
    }

    public void setPlayerUniqueCap(ServerPlayer player, int cap) {
        ServerLevel level = (ServerLevel) player.getLevel();
        YggdrasilSavedData data = YggdrasilSavedData.get(level);
        YggdrasilSavedData.PlayerRecord record = data.getOrCreate(player.getUUID());
        record.uniqueCapOverride = cap;
        data.setDirty();
    }

    public void signalManual(ServerPlayer player) {
        queue(player, QueueReason.MANUAL, true, null);
    }

    private record NearbyPlayerMetrics(String name, int level, double ep, int uniqueOwned,
                                       double distance, double statScore,
                                       ResourceLocation evolvedFrom, ResourceLocation evolvedTo,
                                       boolean queued) {}

    private record EvolutionResult(ResourceLocation from, ResourceLocation to) {
        static final EvolutionResult NONE = new EvolutionResult(null, null);
    }

    public record QueueEntry(UUID playerId, QueueReason reason, long createdMs, boolean urgent,
                             ResourceLocation contextAdvancement) {}

    private void cleanupQueues(MinecraftServer server, long now) {
        Set<UUID> online = server.getPlayerList().getPlayers().stream()
                .map(ServerPlayer::getUUID)
                .collect(Collectors.toSet());
        long intervalMs = Math.max(60_000L, WVConfig.YGG_INTERVAL_MINUTES.get() * 60L * 1000L);
        long maxAge = Math.max(intervalMs, 300_000L) * 2;
        pending.entrySet().removeIf(entry ->
                (now - entry.getValue().createdMs() > maxAge) || !online.contains(entry.getKey()));
        evaluationListeners.entrySet().removeIf(entry ->
                entry.getValue().isEmpty() || !online.contains(entry.getKey()));
        awakeningInsights.keySet().removeIf(id -> !online.contains(id));
        advancementHistory.entrySet().removeIf(entry ->
                entry.getValue().isEmpty() && !online.contains(entry.getKey()));
    }

    private void startIntervalScheduler() {
        if (!intervalSchedulerRunning.compareAndSet(false, true)) {
            return;
        }
        if (intervalTask != null) {
            intervalTask.cancel(false);
        }
        intervalTask = intervalExecutor.scheduleWithFixedDelay(this::runScheduledCheck, 30, 60, TimeUnit.SECONDS);
    }

    private void stopIntervalScheduler() {
        intervalSchedulerRunning.set(false);
        if (intervalTask != null) {
            intervalTask.cancel(false);
            intervalTask = null;
        }
    }

    private void runScheduledCheck() {
        if (!intervalSchedulerRunning.get()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> runEvaluationCycle(server, true));
    }

    private boolean canUseAi(long now) {
        return aiCircuitUntilMs == 0L || now >= aiCircuitUntilMs;
    }

    private void noteAiFailure(long now) {
        int failures = consecutiveAiFailures.incrementAndGet();
        if (failures >= 3) {
            aiCircuitUntilMs = now + TimeUnit.MINUTES.toMillis(5);
            consecutiveAiFailures.set(0);
            LOGGER.warn("Yggdrasil AI circuit opened for 5 minutes due to repeated failures.");
        }
    }

    private void noteAiSuccess() {
        consecutiveAiFailures.set(0);
        aiCircuitUntilMs = 0L;
    }

    private void recordAdvancement(UUID playerId, long now) {
        ArrayDeque<Long> deque = advancementHistory.computeIfAbsent(playerId, id -> new ArrayDeque<>());
        deque.addLast(now);
        trimAdvancementDeque(deque, now);
    }

    private int countRecentAdvancements(UUID playerId, long now) {
        ArrayDeque<Long> deque = advancementHistory.get(playerId);
        if (deque == null) return 0;
        trimAdvancementDeque(deque, now);
        return deque.size();
    }

    private void trimAdvancementDeque(ArrayDeque<Long> deque, long now) {
        while (!deque.isEmpty() && now - deque.peekFirst() > ADVANCEMENT_WINDOW_MS) {
            deque.pollFirst();
        }
    }

    private boolean containsCandidate(List<SkillCandidate> candidates, ResourceLocation id) {
        if (id == null) return false;
        for (SkillCandidate candidate : candidates) {
            if (candidate.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    public enum QueueReason {
        AWAKENING_HERO(0),
        AWAKENING_DEMON(0),
        WISH(1),
        SUBORDINATE(1),
        ADVANCEMENT(2),
        MANUAL(3),
        PASSIVE(4);

        final int priority;

        QueueReason(int priority) {
            this.priority = priority;
        }
    }

    private record SkillCandidate(ResourceLocation id, SkillCategory category, int capacity,
                                  int occupied, QueueReason reason) {}

    private List<String> asStringList(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (element.isJsonPrimitive()) {
                String value = element.getAsString();
                if (value != null && !value.isBlank()) {
                    out.add(value.trim());
                }
            }
        }
        return out;
    }

    private List<WishDecision.WishAction> parseActions(JsonObject json) {
        if (!json.has("actions") || !json.get("actions").isJsonArray()) return List.of();
        List<WishDecision.WishAction> out = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("actions")) {
            if (!element.isJsonObject()) continue;
            JsonObject action = element.getAsJsonObject();
            WishDecision.WishAction.ActionType type = parseActionType(action);
            if (type == null) continue;
            WishDecision.WishAction.ActionOperation op = parseActionOperation(action);
            double value = action.has("value") ? action.get("value").getAsDouble() : 0.0d;
            ResourceLocation target = action.has("skillId") ? ResourceLocation.tryParse(action.get("skillId").getAsString()) : null;
            String payload = action.has("payload") ? action.get("payload").getAsString() : "";
            out.add(new WishDecision.WishAction(type, op, value, target, payload));
        }
        return out;
    }

    private WishDecision.WishAction.ActionType parseActionType(JsonObject json) {
        if (!json.has("type")) return null;
        String raw = json.get("type").getAsString();
        if (raw == null) return null;
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "ep" -> WishDecision.WishAction.ActionType.EP;
            case "magicules" -> WishDecision.WishAction.ActionType.MAGICULES;
            case "aura" -> WishDecision.WishAction.ActionType.AURA;
            case "skill" -> WishDecision.WishAction.ActionType.SKILL;
            case "evolution", "evolve", "race" -> WishDecision.WishAction.ActionType.EVOLUTION;
            case "awakening", "awaken" -> WishDecision.WishAction.ActionType.AWAKENING;
            default -> null;
        };
    }

    private WishDecision.WishAction.ActionOperation parseActionOperation(JsonObject json) {
        if (!json.has("op")) return WishDecision.WishAction.ActionOperation.SET;
        String raw = json.get("op").getAsString();
        if (raw == null) return WishDecision.WishAction.ActionOperation.SET;
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "add", "gain" -> WishDecision.WishAction.ActionOperation.ADD;
            case "grant" -> WishDecision.WishAction.ActionOperation.GRANT;
            case "revoke", "remove" -> WishDecision.WishAction.ActionOperation.REVOKE;
            case "form", "flag" -> WishDecision.WishAction.ActionOperation.FORM;
            case "toggle" -> WishDecision.WishAction.ActionOperation.TOGGLE;
            default -> WishDecision.WishAction.ActionOperation.SET;
        };
    }

    private void sendEvaluationMessage(ServerPlayer player, String narrative, ResourceLocation skillId) {
        boolean positive = skillId != null;
        String fallback;
        if (positive) {
            fallback = "Unique Skill [" + prettifySkill(skillId) + "], has been acquired.";
        } else {
            fallback = "The Voice withholds judgment.";
        }
        String text = (narrative == null || narrative.isBlank()) ? fallback : narrative;
        int color = positive ? 0xb7ffdf : 0xff6b6b;
        Component message = Component.literal(text).withStyle(style -> style.withColor(color));
        player.sendSystemMessage(message);
        var listeners = evaluationListeners.remove(player.getUUID());
        if (listeners != null) {
            for (CommandSourceStack source : listeners) {
                source.sendSuccess(message, false);
            }
        }
    }

    private String prettifySkill(ResourceLocation id) {
        if (id == null) return "Unknown";
        String path = id.getPath();
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }
}
