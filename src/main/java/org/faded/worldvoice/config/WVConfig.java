package org.faded.worldvoice.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class WVConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue TTL_DAYS;
    public static final ForgeConfigSpec.IntValue REFRESH_INTERVAL_MINUTES;
    public static final ForgeConfigSpec.BooleanValue SEED_FROM_TENSURA;
    public static final ForgeConfigSpec.ConfigValue<String> TENSURA_CONFIG_PATH;
    public static final ForgeConfigSpec.BooleanValue PREFER_WAITLIST_ORDER;
    public static final ForgeConfigSpec.BooleanValue ALLOW_BACKFILL_ON_LOGIN;
    public static final ForgeConfigSpec.BooleanValue ULT_BLACKLIST_ENABLED;
    public static final ForgeConfigSpec.IntValue ULT_BLACKLIST_TTL_SECONDS;
    public static final ForgeConfigSpec.BooleanValue REGISTER_PLUNDER_AS_OWNED;
    public static final ForgeConfigSpec.BooleanValue REGISTER_CREATION_AS_OWNED;
    public static final ForgeConfigSpec.BooleanValue REVOKE_SKILLS_ON_REFRESH;

    // Yggdrasil (AI orchestrator)
    public static final ForgeConfigSpec.BooleanValue YGG_ENABLE;
    public static final ForgeConfigSpec.IntValue YGG_INTERVAL_MINUTES;
    public static final ForgeConfigSpec.IntValue YGG_MIN_PLAYERS;
    public static final ForgeConfigSpec.IntValue YGG_PER_PLAYER_UNIQUE_CAP;
    public static final ForgeConfigSpec.BooleanValue YGG_REQUIRE_EVENT;
    public static final ForgeConfigSpec.ConfigValue<String> YGG_DICTIONARY_PATH;
    public static final ForgeConfigSpec.ConfigValue<String> YGG_OPENROUTER_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> YGG_OPENROUTER_KEY;
    public static final ForgeConfigSpec.IntValue YGG_OPENROUTER_MAX_TOKENS;
    public static final ForgeConfigSpec.DoubleValue YGG_OPENROUTER_TEMPERATURE;
    public static final ForgeConfigSpec.ConfigValue<String> YGG_OPENROUTER_REFERER;
    public static final ForgeConfigSpec.ConfigValue<String> YGG_OPENROUTER_TITLE;
    public static final ForgeConfigSpec.BooleanValue YGG_LOG_DECISIONS;
    public static final ForgeConfigSpec.IntValue YGG_AWAKENING_RADIUS;
    public static final ForgeConfigSpec.IntValue YGG_AWAKENING_MAX_NEARBY;
    public static final ForgeConfigSpec.DoubleValue YGG_AWAKENING_EP_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue YGG_AWAKENING_EVOLVE;
    public static final ForgeConfigSpec.BooleanValue YGG_AWAKENING_QUEUE_SUBORDINATES;
    public static final ForgeConfigSpec.DoubleValue YGG_AWAKENING_QUEUE_THRESHOLD;

    // Wish handling
    public static final ForgeConfigSpec.BooleanValue YGG_WISH_ENABLE;
    public static final ForgeConfigSpec.IntValue YGG_WISH_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.DoubleValue YGG_WISH_STRICTNESS;
    public static final ForgeConfigSpec.BooleanValue YGG_WISH_REQUIRE_FAVOR;
    public static final ForgeConfigSpec.BooleanValue YGG_WISH_ALLOW_SKILL_GRANTS;
    public static final ForgeConfigSpec.BooleanValue YGG_WISH_ALLOW_ITEM_GRANTS;
    public static final ForgeConfigSpec.BooleanValue YGG_WISH_PRIVATE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("general");
        TTL_DAYS = b.comment("Days of inactivity before auto-revoke")
                .defineInRange("ttlDays", 14, 1, 3650);
        REFRESH_INTERVAL_MINUTES = b.comment("Minutes between refresh checks")
                .defineInRange("refreshIntervalMinutes", 60, 1, 7 * 24 * 60);
        SEED_FROM_TENSURA = b.comment("Seed skill pool from Tensura config")
                .define("seedFromTensura", true);
        TENSURA_CONFIG_PATH = b.comment("Path to tensura common.toml")
                .define("tensuraConfigPath", "config/tensura-reincarnated/common.toml");
        PREFER_WAITLIST_ORDER = b.comment("If true, use FIFO waitlist order when backfilling")
                .define("preferWaitlistOrder", true);
        ALLOW_BACKFILL_ON_LOGIN = b.comment("Attempt to backfill open slots on player login")
                .define("allowBackfillOnLogin", true);
        ULT_BLACKLIST_ENABLED = b.comment("Enable lightweight blacklist for ultimate learns while slots are full (epoch-aware)")
                .define("ultimateBlacklistEnabled", true);
        ULT_BLACKLIST_TTL_SECONDS = b.comment("Seconds to suppress repeat ultimate learn attempts within the same epoch (auto-invalidated when capacity changes)")
                .defineInRange("ultimateBlacklistTtlSeconds", 900, 5, 86400);
        REGISTER_PLUNDER_AS_OWNED = b.comment("Register skills obtained via plundering/copying as owned (consume slot)")
                .define("registerPlunderAsOwned", false);
        REGISTER_CREATION_AS_OWNED = b.comment("Register skills created via creation skills as owned (consume slot); temporary skills should not be registered")
                .define("registerCreationAsOwned", false);
        REVOKE_SKILLS_ON_REFRESH = b.comment("If true, commands like assign/revoke/refresh taken affect actual player skills; otherwise only ledger is updated")
                .define("revokeSkillsOnRefresh", true);
        b.pop();

        b.push("yggdrasil");
        YGG_ENABLE = b.comment("Enable the Yggdrasil (Voice of the World) automation layer.")
                .define("enabled", true);
        YGG_INTERVAL_MINUTES = b.comment("Minutes between automatic Yggdrasil evaluations.")
                .defineInRange("intervalMinutes", 30, 1, 24 * 60);
        YGG_MIN_PLAYERS = b.comment("Minimum number of online players required before the scheduler runs.")
                .defineInRange("minOnlinePlayers", 3, 0, 1000);
        YGG_PER_PLAYER_UNIQUE_CAP = b.comment("Hard cap on unique skills that Yggdrasil may grant a player (baseline, can be bypassed during favor).")
                .defineInRange("perPlayerUniqueCap", 2, 0, 64);
        YGG_REQUIRE_EVENT = b.comment("If true, Yggdrasil only considers players who recently triggered a significant event (advancement, awakening, wish, etc.).")
                .define("requireTriggerEvent", true);
        YGG_DICTIONARY_PATH = b.comment("Path to the skill dictionary file describing intent/tags. Relative to the game directory.")
                .define("dictionaryPath", "config/worldvoice/yggdrasil-skills.json5");
        YGG_LOG_DECISIONS = b.comment("Write every Yggdrasil decision (success/failures) into the JSONL log.")
                .define("logDecisions", true);
        YGG_AWAKENING_RADIUS = b.comment("Radius (blocks) to scan for subordinates when an awakening occurs.")
                .defineInRange("awakeningRadius", 16, 0, 128);
        YGG_AWAKENING_MAX_NEARBY = b.comment("Maximum nearby subordinates to process per awakening.")
                .defineInRange("awakeningMaxNearby", 6, 0, 32);
        YGG_AWAKENING_EP_MULTIPLIER = b.comment("Permanent EP multiplier applied to eligible subordinates touched by the awakening.")
                .defineInRange("awakeningEpMultiplier", 1.5d, 1.0d, 5.0d);
        YGG_AWAKENING_EVOLVE = b.comment("Allow awakenings to evolve one subordinate unique skill when possible.")
                .define("awakeningEvolve", true);
        YGG_AWAKENING_QUEUE_SUBORDINATES = b.comment("Queue worthy subordinates for immediate AI evaluation.")
                .define("awakeningQueueSubordinates", true);
        YGG_AWAKENING_QUEUE_THRESHOLD = b.comment("Minimum composite score (level + EP/1000 + playHours) before a subordinate is queued.")
                .defineInRange("awakeningQueueThreshold", 55.0d, 0.0d, 500.0d);

        b.push("ai");
        YGG_OPENROUTER_MODEL = b.comment("OpenRouter model identifier to use when AI integration is enabled.")
                .define("openRouterModel", "openrouter/anthropic/claude-3.5-sonnet");
        YGG_OPENROUTER_KEY = b.comment("OpenRouter API key (optional; leave blank to disable remote calls).")
                .define("openRouterKey", "");
        YGG_OPENROUTER_MAX_TOKENS = b.comment("Maximum tokens per OpenRouter completion.")
                .defineInRange("openRouterMaxTokens", 600, 32, 32000);
        YGG_OPENROUTER_TEMPERATURE = b.comment("Sampling temperature for OpenRouter completions.")
                .defineInRange("openRouterTemperature", 0.2d, 0.0d, 2.0d);
        YGG_OPENROUTER_REFERER = b.comment("Optional HTTP-Referer header for OpenRouter requests (used for rankings).")
                .define("openRouterReferer", "");
        YGG_OPENROUTER_TITLE = b.comment("Optional X-Title header for OpenRouter requests (used for rankings).")
                .define("openRouterTitle", "");
        b.pop();

        b.push("wishes");
        YGG_WISH_ENABLE = b.comment("Enable the wish adjudication system (\"I wish ...\" chat).")
                .define("enabled", true);
        YGG_WISH_COOLDOWN_MINUTES = b.comment("Minutes between accepted wish evaluations per player.")
                .defineInRange("cooldownMinutes", 120, 1, 7 * 24 * 60);
        YGG_WISH_STRICTNESS = b.comment("0.0 = lenient, 1.0 = harsh. Controls denial/backlash probability.")
                .defineInRange("strictness", 0.85d, 0.0d, 1.0d);
        YGG_WISH_REQUIRE_FAVOR = b.comment("If true, only players with active favor windows may receive positive wish outcomes.")
                .define("requireFavor", true);
        YGG_WISH_ALLOW_SKILL_GRANTS = b.comment("Allow wishes to grant skills when approved.")
                .define("allowSkillGrants", true);
        YGG_WISH_ALLOW_ITEM_GRANTS = b.comment("Allow wishes to grant physical items when approved.")
                .define("allowItemGrants", true);
        YGG_WISH_PRIVATE = b.comment("If true, wish chat is hidden from public chat and echoed only to the sender.")
                .define("private", false);
        b.pop();

        b.pop(); // yggdrasil
        SPEC = b.build();
    }
}
