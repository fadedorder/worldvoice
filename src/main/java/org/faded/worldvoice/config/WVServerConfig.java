package org.faded.worldvoice.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;

public class WVServerConfig {

    public static final ForgeConfigSpec SERVER_SPEC;

    public static final ForgeConfigSpec.BooleanValue AUTO_ADD_NEW_SKILLS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> UNIQUE_SLOTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ULTIMATE_SLOTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MANUAL_SLOTS;
    // Blacklist (learning)
    public static final ForgeConfigSpec.BooleanValue BL_UNIQUE_ALL;
    public static final ForgeConfigSpec.BooleanValue BL_ULTIMATE_ALL;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BL_SKILLS;
    // Plunder restrictions
    public static final ForgeConfigSpec.BooleanValue PL_UNIQUE_ALL;
    public static final ForgeConfigSpec.BooleanValue PL_ULTIMATE_ALL;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PL_SKILLS;

    private static volatile ModConfig SERVER_CONFIG;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("skills");
        AUTO_ADD_NEW_SKILLS = builder.comment("Automatically add newly discovered unique/ultimate skills with default capacity.")
                .define("autoAddNewSkills", true);
        UNIQUE_SLOTS = builder.comment("Slot allocations for unique skills. Format: namespace:path=slots")
                .defineList("uniqueSlots", SkillConfigDefaults.uniqueDefaults(), obj -> obj instanceof String);
        ULTIMATE_SLOTS = builder.comment("Slot allocations for ultimate skills. Format: namespace:path=slots")
                .defineList("ultimateSlots", SkillConfigDefaults.ultimateDefaults(), obj -> obj instanceof String);
        MANUAL_SLOTS = builder.comment("Additional non-unique/ultimate skills managed by WorldVoice. Format: namespace:path=slots")
                .defineList("manualSlots", SkillConfigDefaults.manualDefaults(), obj -> obj instanceof String);
        builder.pop();

        builder.push("blacklist");
        BL_UNIQUE_ALL = builder.comment("If true, all UNIQUE skills are blacklisted from learning")
                .define("uniqueAll", false);
        BL_ULTIMATE_ALL = builder.comment("If true, all ULTIMATE skills are blacklisted from learning")
                .define("ultimateAll", false);
        BL_SKILLS = builder.comment("List of blacklisted skills (namespace:path)")
                .defineList("skills", java.util.Collections.emptyList(), obj -> obj instanceof String);
        builder.pop();

        builder.push("plunder");
        PL_UNIQUE_ALL = builder.comment("If true, UNIQUE skills cannot be plundered/copied")
                .define("restrictUniqueAll", false);
        PL_ULTIMATE_ALL = builder.comment("If true, ULTIMATE skills cannot be plundered/copied")
                .define("restrictUltimateAll", false);
        PL_SKILLS = builder.comment("List of skills that cannot be plundered/copied")
                .defineList("restrictSkills", java.util.Collections.emptyList(), obj -> obj instanceof String);
        builder.pop();

        SERVER_SPEC = builder.build();
    }

    public static void bind(ModConfig cfg) {
        if (cfg != null && cfg.getSpec() == SERVER_SPEC) {
            SERVER_CONFIG = cfg;
        }
    }

    public static void saveIfPossible() {
        ModConfig cfg = SERVER_CONFIG;
        if (cfg != null) {
            cfg.save();
        }
    }
}
