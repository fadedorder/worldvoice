package org.faded.worldvoice.command;

import com.github.manasmods.manascore.api.skills.ManasSkill;
import com.github.manasmods.manascore.api.skills.SkillAPI;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.faded.worldvoice.ai.YggdrasilHooks;
import org.faded.worldvoice.ai.YggdrasilSavedData;
import org.faded.worldvoice.ai.YggdrasilService;
import org.faded.worldvoice.config.WVConfig;
import org.faded.worldvoice.core.SkillCategory;
import org.faded.worldvoice.core.SkillConfigManager;
import org.faded.worldvoice.core.SlotLedger;
import org.faded.worldvoice.service.SkillService;
import org.faded.worldvoice.util.LogJsonL;
import org.faded.worldvoice.world.WorldVoiceGameRules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

public class CommandRegistrar {

    private static final SuggestionProvider<CommandSourceStack> SKILL_SUGGEST = (ctx, builder) ->
            SkillConfigManager.INSTANCE.suggestSkills(ctx, builder);

    private static final SuggestionProvider<CommandSourceStack> WHITELIST_SCOPE_SUGGEST = (ctx, builder) -> {
        builder.suggest("everything");
        builder.suggest("unique");
        builder.suggest("ultimate");
        for (ResourceLocation id : SkillConfigManager.INSTANCE.getManagedSkills()) {
            builder.suggest(id.toString());
        }
        return builder.buildFuture();
    };

    private static final SimpleCommandExceptionType INVALID_SCOPE = new SimpleCommandExceptionType(
            Component.literal("Unknown whitelist scope; expected everything, unique, ultimate, or a managed skill id.")
    );
    private static final SimpleCommandExceptionType NO_YGG = new SimpleCommandExceptionType(
            Component.literal("Yggdrasil service is not available.")
    );

    private final SkillService skills = new SkillService();

    @SubscribeEvent
    public void onRegister(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> dispatcher = evt.getDispatcher();

        // Register quest commands
        QuestCommand.register(dispatcher);

        dispatcher.register(
                Commands.literal("worldvoice")
                        .then(buildSkillCommand())
                        .then(buildRefreshCommand())
                        .then(buildWhitelistCommand())
                        .then(buildBlacklistCommand())
                        .then(buildSettingsCommand())
                        .then(buildPlunderCommand())
                        .then(buildYggdrasilCommand())
        );
    }

    // duplicate method removed

    private ArgumentBuilder<CommandSourceStack, ?> buildRefreshCommand() {
        return Commands.literal("refresh")
                .then(Commands.literal("taken")
                        .executes(ctx -> refreshTaken(ctx.getSource()))
                        .then(Commands.argument("skill", ResourceLocationArgument.id()).suggests(SKILL_SUGGEST)
                                .executes(ctx -> refreshSkill(ctx.getSource(), ResourceLocationArgument.getId(ctx, "skill")))))
                .then(Commands.literal("config").executes(ctx -> refreshConfig(ctx.getSource())))
                .then(Commands.literal("inactive")
                        .then(Commands.argument("days", IntegerArgumentType.integer(1, 3650))
                                .executes(ctx -> refreshInactive(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "days")))))
                ;
    }

    private ArgumentBuilder<CommandSourceStack, ?> buildWhitelistCommand() {
        return Commands.literal("whitelist")
                // slots whitelist (existing behavior)
                .then(Commands.literal("add")
                        .then(Commands.argument("profile", GameProfileArgument.gameProfile())
                                .then(Commands.argument("scope", ResourceLocationArgument.id()).suggests(WHITELIST_SCOPE_SUGGEST)
                                        .executes(ctx -> whitelist(ctx.getSource(),
                                                GameProfileArgument.getGameProfiles(ctx, "profile"),
                                                ResourceLocationArgument.getId(ctx, "scope"),
                                                true)))))
                .then(Commands.literal("revoke")
                        .then(Commands.argument("profile", GameProfileArgument.gameProfile())
                                .then(Commands.argument("scope", ResourceLocationArgument.id()).suggests(WHITELIST_SCOPE_SUGGEST)
                                        .executes(ctx -> whitelist(ctx.getSource(),
                                                GameProfileArgument.getGameProfiles(ctx, "profile"),
                                                ResourceLocationArgument.getId(ctx, "scope"),
                                                false)))))
                .then(Commands.literal("list").executes(ctx -> whitelistList(ctx.getSource())))
                .then(Commands.literal("inactive")
                        .then(Commands.literal("add")
                                .then(Commands.argument("profile", GameProfileArgument.gameProfile())
                                        .executes(ctx -> whitelistInactive(ctx.getSource(),
                                                GameProfileArgument.getGameProfiles(ctx, "profile"), true))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("profile", GameProfileArgument.gameProfile())
                                        .executes(ctx -> whitelistInactive(ctx.getSource(),
                                                GameProfileArgument.getGameProfiles(ctx, "profile"), false))))
                        .then(Commands.literal("list").executes(ctx -> whitelistInactiveList(ctx.getSource()))))
                // blacklist whitelist (bypass learning blacklists)
                .then(Commands.literal("blacklist")
                        .then(Commands.literal("add")
                                .then(Commands.argument("profile", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("scope", ResourceLocationArgument.id()).suggests(WHITELIST_SCOPE_SUGGEST)
                                                .executes(ctx -> whitelistBlacklist(ctx.getSource(),
                                                        GameProfileArgument.getGameProfiles(ctx, "profile"),
                                                        ResourceLocationArgument.getId(ctx, "scope"), true)))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("profile", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("scope", ResourceLocationArgument.id()).suggests(WHITELIST_SCOPE_SUGGEST)
                                                .executes(ctx -> whitelistBlacklist(ctx.getSource(),
                                                        GameProfileArgument.getGameProfiles(ctx, "profile"),
                                                        ResourceLocationArgument.getId(ctx, "scope"), false)))))
                        .then(Commands.literal("list").executes(ctx -> whitelistBlacklistList(ctx.getSource()))))
                ;
    }

    private int assign(CommandContext<CommandSourceStack> ctx, boolean bypass) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "skill");
        boolean ok = skills.assign(player, id, bypass, true);
        if (!ok) {
            ctx.getSource().sendFailure(Component.literal("Failed to assign " + id + " to " + player.getGameProfile().getName() + "."));
            return 0;
        }
        ctx.getSource().sendSuccess(Component.literal("Assigned " + id + " to " + player.getGameProfile().getName() + (bypass ? " (bypass)" : "")), true);
        return 1;
    }

    private int revoke(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "skill");
        boolean ok = skills.revoke(player, id, "command");
        if (!ok) {
            ctx.getSource().sendFailure(Component.literal("Failed to revoke " + id + " from " + player.getGameProfile().getName() + "."));
            return 0;
        }
        ctx.getSource().sendSuccess(Component.literal("Revoked " + id + " from " + player.getGameProfile().getName()), true);
        return 1;
    }

    private int holders(CommandSourceStack src, ResourceLocation skillId) {
        ServerLevel level = src.getLevel();
        SkillCategory cat = SkillConfigManager.INSTANCE.classify(skillId);
        if (cat == null) {
            src.sendFailure(Component.literal("WorldVoice does not manage skill " + skillId + "."));
            return 0;
        }

        SlotLedger ledger = SlotLedger.get(level);
        SlotLedger.SkillState state = ledger.getOrCreateSkill(cat, skillId);
        if (state.holders.isEmpty()) {
            src.sendSuccess(Component.literal("No holders recorded for " + skillId + "."), false);
            return 1;
        }

        src.sendSuccess(Component.literal("Holders for " + skillId + ":"), false);
        for (UUID holder : state.holders) {
            SlotLedger.PlayerRecord pr = ledger.players.get(holder);
            String name = resolveName(pr, holder);
            src.sendSuccess(Component.literal(" - " + name + " (" + holder + ")"), false);
        }
        return state.holders.size();
    }

    private int listTaken(CommandSourceStack src, boolean includePlayers) {
        ServerLevel level = src.getLevel();
        SlotLedger ledger = SlotLedger.get(level);
        List<String> segments = new ArrayList<>();
        for (SkillCategory cat : SkillCategory.values()) {
            if (!WorldVoiceGameRules.isEnabled(level, cat)) continue;
            for (Map.Entry<ResourceLocation, SlotLedger.SkillState> entry : ledger.categories.get(cat).entrySet()) {
                if (entry.getValue().holders.isEmpty()) continue;
                StringBuilder sb = new StringBuilder(entry.getKey().toString());
                if (includePlayers) {
                    sb.append(" - ");
                    sb.append(formatHoldersList(ledger, entry.getValue().holders));
                }
                segments.add(sb.toString());
            }
        }

        if (segments.isEmpty()) {
            src.sendSuccess(Component.literal("No skills are currently taken."), false);
        } else {
            src.sendSuccess(Component.literal(String.join(" | ", segments)), false);
        }
        return segments.size();
    }

    // ----- Slots subcommands -----
    private ArgumentBuilder<CommandSourceStack, ?> buildSlotsCommands() {
        var root = Commands.literal("slots");

        // view unique/ultimate
        root.then(Commands.literal("view")
                .then(Commands.literal("unique").executes(ctx -> slotsView(ctx.getSource(), SkillCategory.UNIQUE)))
                .then(Commands.literal("ultimate").executes(ctx -> slotsView(ctx.getSource(), SkillCategory.ULTIMATE))));

        // set/add/remove unique/ultimate <skill> <n>
        root.then(Commands.literal("set")
                .then(Commands.literal("unique")
                        .then(Commands.literal("all")
                                .then(Commands.argument("n", IntegerArgumentType.integer(0, 9999))
                                        .executes(ctx -> slotsSetAll(ctx.getSource(), SkillCategory.UNIQUE,
                                                IntegerArgumentType.getInteger(ctx, "n")))))
                        .then(Commands.argument("skill", ResourceLocationArgument.id()).suggests(SKILL_SUGGEST)
                                .then(Commands.argument("n", IntegerArgumentType.integer(0, 9999))
                                        .executes(ctx -> slotsSet(ctx.getSource(), SkillCategory.UNIQUE,
                                                ResourceLocationArgument.getId(ctx, "skill"),
                                                IntegerArgumentType.getInteger(ctx, "n"))))))
                .then(Commands.literal("ultimate")
                        .then(Commands.literal("all")
                                .then(Commands.argument("n", IntegerArgumentType.integer(0, 9999))
                                        .executes(ctx -> slotsSetAll(ctx.getSource(), SkillCategory.ULTIMATE,
                                                IntegerArgumentType.getInteger(ctx, "n")))))
                        .then(Commands.argument("skill", ResourceLocationArgument.id()).suggests(SKILL_SUGGEST)
                                .then(Commands.argument("n", IntegerArgumentType.integer(0, 9999))
                                        .executes(ctx -> slotsSet(ctx.getSource(), SkillCategory.ULTIMATE,
                                                ResourceLocationArgument.getId(ctx, "skill"),
                                                IntegerArgumentType.getInteger(ctx, "n")))))));

        root.then(Commands.literal("add")
                .then(Commands.literal("unique")
                        .then(Commands.literal("all")
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 9999))
                                        .executes(ctx -> slotsAddAll(ctx.getSource(), SkillCategory.UNIQUE,
                                                IntegerArgumentType.getInteger(ctx, "n")))))
                        .then(Commands.argument("skill", ResourceLocationArgument.id()).suggests(SKILL_SUGGEST)
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 9999))
                                        .executes(ctx -> slotsAdd(ctx.getSource(), SkillCategory.UNIQUE,
                                                ResourceLocationArgument.getId(ctx, "skill"),
                                                IntegerArgumentType.getInteger(ctx, "n"))))))
                .then(Commands.literal("ultimate")
                        .then(Commands.literal("all")
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 9999))
                                        .executes(ctx -> slotsAddAll(ctx.getSource(), SkillCategory.ULTIMATE,
                                                IntegerArgumentType.getInteger(ctx, "n")))))
                        .then(Commands.argument("skill", ResourceLocationArgument.id()).suggests(SKILL_SUGGEST)
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 9999))
                                        .executes(ctx -> slotsAdd(ctx.getSource(), SkillCategory.ULTIMATE,
                                                ResourceLocationArgument.getId(ctx, "skill"),
                                                IntegerArgumentType.getInteger(ctx, "n")))))));

        root.then(Commands.literal("remove")
                .then(Commands.literal("unique")
                        .then(Commands.literal("all")
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 9999))
                                        .executes(ctx -> slotsRemoveAll(ctx.getSource(), SkillCategory.UNIQUE,
                                                IntegerArgumentType.getInteger(ctx, "n")))))
                        .then(Commands.argument("skill", ResourceLocationArgument.id()).suggests(SKILL_SUGGEST)
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 9999))
                                        .executes(ctx -> slotsRemove(ctx.getSource(), SkillCategory.UNIQUE,
                                                ResourceLocationArgument.getId(ctx, "skill"),
                                                IntegerArgumentType.getInteger(ctx, "n"))))))
                .then(Commands.literal("ultimate")
                        .then(Commands.literal("all")
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 9999))
                                        .executes(ctx -> slotsRemoveAll(ctx.getSource(), SkillCategory.ULTIMATE,
                                                IntegerArgumentType.getInteger(ctx, "n")))))
                        .then(Commands.argument("skill", ResourceLocationArgument.id()).suggests(SKILL_SUGGEST)
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 9999))
                                        .executes(ctx -> slotsRemove(ctx.getSource(), SkillCategory.ULTIMATE,
                                                ResourceLocationArgument.getId(ctx, "skill"),
                                                IntegerArgumentType.getInteger(ctx, "n")))))));

        return root;
    }

    private ArgumentBuilder<CommandSourceStack, ?> buildBlacklistCommand() {
        return Commands.literal("blacklist")
                .then(Commands.literal("view").executes(ctx -> blacklistView(ctx.getSource())))
                .then(Commands.literal("add")
                        .then(Commands.literal("unique").executes(ctx -> {
                            SkillConfigManager.INSTANCE.setBlacklistCategory(SkillCategory.UNIQUE, true);
                            ctx.getSource().sendSuccess(Component.literal("Blacklisted all unique skills."), true);
                            return 1;
                        }))
                        .then(Commands.literal("ultimate").executes(ctx -> {
                            SkillConfigManager.INSTANCE.setBlacklistCategory(SkillCategory.ULTIMATE, true);
                            ctx.getSource().sendSuccess(Component.literal("Blacklisted all ultimate skills."), true);
                            return 1;
                        }))
                        .then(Commands.literal("skill")
                                .then(Commands.argument("skill", ResourceLocationArgument.id()).suggests(SKILL_SUGGEST)
                                        .executes(ctx -> {
                                            boolean changed = SkillConfigManager.INSTANCE.addBlacklistedSkill(ResourceLocationArgument.getId(ctx, "skill"));
                                            if (changed) ctx.getSource().sendSuccess(Component.literal("Blacklisted skill."), true);
                                            else ctx.getSource().sendFailure(Component.literal("Skill already blacklisted."));
                                            return changed ? 1 : 0;
                                        }))))
                .then(Commands.literal("remove")
                        .then(Commands.literal("unique").executes(ctx -> {
                            SkillConfigManager.INSTANCE.setBlacklistCategory(SkillCategory.UNIQUE, false);
                            ctx.getSource().sendSuccess(Component.literal("Un-blacklisted all unique skills."), true);
                            return 1;
                        }))
                        .then(Commands.literal("ultimate").executes(ctx -> {
                            SkillConfigManager.INSTANCE.setBlacklistCategory(SkillCategory.ULTIMATE, false);
                            ctx.getSource().sendSuccess(Component.literal("Un-blacklisted all ultimate skills."), true);
                            return 1;
                        }))
                .then(Commands.literal("skill")
                        .then(Commands.argument("skill", ResourceLocationArgument.id()).suggests(SKILL_SUGGEST)
                                .executes(ctx -> {
                                    boolean changed = SkillConfigManager.INSTANCE.removeBlacklistedSkill(ResourceLocationArgument.getId(ctx, "skill"));
                                    if (changed) ctx.getSource().sendSuccess(Component.literal("Removed skill from blacklist."), true);
                                    else ctx.getSource().sendFailure(Component.literal("Skill was not blacklisted."));
                                    return changed ? 1 : 0;
                                }))))
                ;
    }

    private ArgumentBuilder<CommandSourceStack, ?> buildSettingsCommand() {
        return Commands.literal("settings")
                .then(Commands.literal("registerPlunderAsOwned")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean v = BoolArgumentType.getBool(ctx, "value");
                                    org.faded.worldvoice.config.WVConfig.REGISTER_PLUNDER_AS_OWNED.set(v);
                                    ctx.getSource().sendSuccess(Component.literal("registerPlunderAsOwned set to " + v), true);
                                    return 1;
                                })))
                .then(Commands.literal("registerCreationAsOwned")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean v = BoolArgumentType.getBool(ctx, "value");
                                    org.faded.worldvoice.config.WVConfig.REGISTER_CREATION_AS_OWNED.set(v);
                                    ctx.getSource().sendSuccess(Component.literal("registerCreationAsOwned set to " + v), true);
                                    return 1;
                                })))
                .then(Commands.literal("revokeSkillsOnRefresh")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean v = BoolArgumentType.getBool(ctx, "value");
                                    org.faded.worldvoice.config.WVConfig.REVOKE_SKILLS_ON_REFRESH.set(v);
                                    ctx.getSource().sendSuccess(Component.literal("revokeSkillsOnRefresh set to " + v), true);
                                    return 1;
                                })))
                ;
    }

    private ArgumentBuilder<CommandSourceStack, ?> buildPlunderCommand() {
        return Commands.literal("plunder")
                .then(Commands.literal("restrict")
                        .then(Commands.literal("unique").executes(ctx -> {
                            SkillConfigManager.INSTANCE.setPlunderCategory(SkillCategory.UNIQUE, true);
                            ctx.getSource().sendSuccess(Component.literal("Restricted plundering for unique skills."), true);
                            return 1;
                        }))
                        .then(Commands.literal("ultimate").executes(ctx -> {
                            SkillConfigManager.INSTANCE.setPlunderCategory(SkillCategory.ULTIMATE, true);
                            ctx.getSource().sendSuccess(Component.literal("Restricted plundering for ultimate skills."), true);
                            return 1;
                        }))
                        .then(Commands.literal("skill")
                                .then(Commands.argument("skill", ResourceLocationArgument.id()).suggests(SKILL_SUGGEST)
                                        .executes(ctx -> {
                                            boolean changed = SkillConfigManager.INSTANCE.addPlunderRestrictedSkill(ResourceLocationArgument.getId(ctx, "skill"));
                                            if (changed) ctx.getSource().sendSuccess(Component.literal("Restricted plundering for skill."), true);
                                            else ctx.getSource().sendFailure(Component.literal("Skill already restricted."));
                                            return changed ? 1 : 0;
                                        }))))
                .then(Commands.literal("unrestrict")
                        .then(Commands.literal("unique").executes(ctx -> {
                            SkillConfigManager.INSTANCE.setPlunderCategory(SkillCategory.UNIQUE, false);
                            ctx.getSource().sendSuccess(Component.literal("Unrestricted plundering for unique skills."), true);
                            return 1;
                        }))
                        .then(Commands.literal("ultimate").executes(ctx -> {
                            SkillConfigManager.INSTANCE.setPlunderCategory(SkillCategory.ULTIMATE, false);
                            ctx.getSource().sendSuccess(Component.literal("Unrestricted plundering for ultimate skills."), true);
                            return 1;
                        }))
                        .then(Commands.literal("skill")
                                .then(Commands.argument("skill", ResourceLocationArgument.id()).suggests(SKILL_SUGGEST)
                                        .executes(ctx -> {
                                            boolean changed = SkillConfigManager.INSTANCE.removePlunderRestrictedSkill(ResourceLocationArgument.getId(ctx, "skill"));
                                            if (changed) ctx.getSource().sendSuccess(Component.literal("Removed skill plunder restriction."), true);
                                            else ctx.getSource().sendFailure(Component.literal("Skill was not restricted."));
                                            return changed ? 1 : 0;
                        }))))
                ;
    }

    private ArgumentBuilder<CommandSourceStack, ?> buildYggdrasilCommand() {
        return Commands.literal("yggdrasil")
                .then(Commands.literal("invoke")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    YggdrasilService svc = requireYggdrasil();
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    boolean ok = svc.forceEvaluate(target, YggdrasilService.QueueReason.MANUAL, ctx.getSource());
                                    if (ok) {
                                        ctx.getSource().sendSuccess(Component.literal("Invoked Yggdrasil evaluation for " + target.getGameProfile().getName() + "."), true);
                                    } else {
                                        ctx.getSource().sendFailure(Component.literal("Failed to evaluate " + target.getGameProfile().getName() + "."));
                                    }
                                    return ok ? 1 : 0;
                                })))
                .then(Commands.literal("players")
                        .then(Commands.literal("get")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> {
                                            YggdrasilService svc = requireYggdrasil();
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            sendPlayerSummary(ctx.getSource(), target, svc.getOrCreateRecord(target));
                                            return 1;
                                        })))
                        .then(Commands.literal("edit")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.literal("cap")
                                                .then(Commands.argument("value", IntegerArgumentType.integer(-1, 64))
                                                        .executes(ctx -> {
                                                            YggdrasilService svc = requireYggdrasil();
                                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                            int value = IntegerArgumentType.getInteger(ctx, "value");
                                                            svc.setPlayerUniqueCap(target, value);
                                                            ctx.getSource().sendSuccess(Component.literal("Set unique cap override for " + target.getGameProfile().getName() + " to " + value + ". (-1 to use global)"), true);
                                                            return 1;
                                                        })))
                                        .then(Commands.literal("favor")
                                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 24 * 60 * 60))
                                                        .then(Commands.argument("strength", DoubleArgumentType.doubleArg(0.0d, 10.0d))
                                                                .executes(ctx -> {
                                                                    YggdrasilService svc = requireYggdrasil();
                                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                                    int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                                                    double strength = DoubleArgumentType.getDouble(ctx, "strength");
                                                                    svc.setFavor(target, seconds * 1000L, strength, "command");
                                                                    ctx.getSource().sendSuccess(Component.literal("Set favor for " + target.getGameProfile().getName() + " (" + seconds + "s, strength " + strength + ")."), true);
                                                                    return 1;
                                                                }))))
                                        .then(Commands.literal("cooldown")
                                                .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 7 * 24 * 60))
                                                        .executes(ctx -> {
                                                            YggdrasilService svc = requireYggdrasil();
                                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                            int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                                                            svc.setWishCooldown(target, minutes);
                                                            ctx.getSource().sendSuccess(Component.literal("Set wish cooldown for " + target.getGameProfile().getName() + " to " + minutes + " minute(s)."), true);
                                                            return 1;
                                                        })))))
                )
                .then(Commands.literal("settings")
                        .then(buildYggCoreSettings())
                        .then(buildYggWishSettings())
                        .then(buildYggAiSettings())
                );
    }

    private ArgumentBuilder<CommandSourceStack, ?> buildYggCoreSettings() {
        return Commands.literal("core")
                .then(Commands.literal("enabled")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean value = BoolArgumentType.getBool(ctx, "value");
                                    WVConfig.YGG_ENABLE.set(value);
                                    ctx.getSource().sendSuccess(Component.literal("Yggdrasil core enabled set to " + value), true);
                                    return 1;
                                })))
                .then(Commands.literal("minPlayers")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 1000))
                                .executes(ctx -> {
                                    int value = IntegerArgumentType.getInteger(ctx, "value");
                                    WVConfig.YGG_MIN_PLAYERS.set(value);
                                    ctx.getSource().sendSuccess(Component.literal("Yggdrasil minimum players set to " + value), true);
                                    return 1;
                                })))
                .then(Commands.literal("interval")
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 24 * 60))
                                .executes(ctx -> {
                                    int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                                    WVConfig.YGG_INTERVAL_MINUTES.set(minutes);
                                    ctx.getSource().sendSuccess(Component.literal("Yggdrasil interval set to " + minutes + " minute(s)."), true);
                                    return 1;
                                })))
                .then(Commands.literal("perPlayerUniqueCap")
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 64))
                                .executes(ctx -> {
                                    int value = IntegerArgumentType.getInteger(ctx, "value");
                                    WVConfig.YGG_PER_PLAYER_UNIQUE_CAP.set(value);
                                    ctx.getSource().sendSuccess(Component.literal("Yggdrasil per-player unique cap set to " + value), true);
                                    return 1;
                                })));
    }

    private ArgumentBuilder<CommandSourceStack, ?> buildYggWishSettings() {
        return Commands.literal("wish")
                .then(Commands.literal("get")
                        .then(Commands.literal("strictness")
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(Component.literal("Wish strictness: " + WVConfig.YGG_WISH_STRICTNESS.get()), false);
                                    return 1;
                                }))
                        .then(Commands.literal("skills-allowed")
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(Component.literal("Wish skill grants allowed: " + WVConfig.YGG_WISH_ALLOW_SKILL_GRANTS.get()), false);
                                    return 1;
                                }))
                        .then(Commands.literal("items-allowed")
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(Component.literal("Wish item grants allowed: " + WVConfig.YGG_WISH_ALLOW_ITEM_GRANTS.get()), false);
                                    return 1;
                                }))
                        .then(Commands.literal("private")
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(Component.literal("Wish chat private: " + WVConfig.YGG_WISH_PRIVATE.get()), false);
                                    return 1;
                                })))
                .then(Commands.literal("enabled")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean value = BoolArgumentType.getBool(ctx, "value");
                                    WVConfig.YGG_WISH_ENABLE.set(value);
                                    ctx.getSource().sendSuccess(Component.literal("Wish system enabled set to " + value), true);
                                    return 1;
                                })))
                .then(Commands.literal("strictness")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0d, 1.0d))
                                .executes(ctx -> {
                                    double value = DoubleArgumentType.getDouble(ctx, "value");
                                    WVConfig.YGG_WISH_STRICTNESS.set(value);
                                    ctx.getSource().sendSuccess(Component.literal("Wish strictness set to " + value), true);
                                    return 1;
                                })))
                .then(Commands.literal("skills-allowed")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean value = BoolArgumentType.getBool(ctx, "value");
                                    WVConfig.YGG_WISH_ALLOW_SKILL_GRANTS.set(value);
                                    ctx.getSource().sendSuccess(Component.literal("Wish skill grants allowed set to " + value), true);
                                    return 1;
                                })))
                .then(Commands.literal("items-allowed")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean value = BoolArgumentType.getBool(ctx, "value");
                                    WVConfig.YGG_WISH_ALLOW_ITEM_GRANTS.set(value);
                                    ctx.getSource().sendSuccess(Component.literal("Wish item grants allowed set to " + value), true);
                                    return 1;
                                })))
                .then(Commands.literal("private")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean value = BoolArgumentType.getBool(ctx, "value");
                                    WVConfig.YGG_WISH_PRIVATE.set(value);
                                    ctx.getSource().sendSuccess(Component.literal("Wish chat privacy set to " + value), true);
                                    return 1;
                                })));
    }

    private ArgumentBuilder<CommandSourceStack, ?> buildYggAiSettings() {
        return Commands.literal("ai")
                .then(Commands.literal("get")
                        .then(Commands.literal("model")
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(Component.literal("Current model: " + WVConfig.YGG_OPENROUTER_MODEL.get()), false);
                                    return 1;
                                })))
                .then(Commands.literal("edit")
                        .then(Commands.literal("model")
                                .then(Commands.argument("model", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String model = StringArgumentType.getString(ctx, "model");
                                            WVConfig.YGG_OPENROUTER_MODEL.set(model);
                                            ctx.getSource().sendSuccess(Component.literal("Yggdrasil model set to " + model + "."), true);
                                            return 1;
                                        }))));
    }

    private ArgumentBuilder<CommandSourceStack, ?> buildSkillCommand() {
        return Commands.literal("skill")
                .then(Commands.literal("holders")
                        .then(Commands.argument("skill", ResourceLocationArgument.id()).suggests(SKILL_SUGGEST)
                                .executes(ctx -> holders(ctx.getSource(), ResourceLocationArgument.getId(ctx, "skill")))))
                .then(Commands.literal("list")
                        .then(Commands.literal("taken")
                                .executes(ctx -> listTaken(ctx.getSource(), false))
                                .then(Commands.argument("includePlayers", BoolArgumentType.bool())
                                        .executes(ctx -> listTaken(ctx.getSource(), BoolArgumentType.getBool(ctx, "includePlayers")))))
                        .then(Commands.literal("remaining")
                                .executes(ctx -> listRemaining(ctx.getSource()))))
                .then(Commands.literal("assign")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("skill", ResourceLocationArgument.id()).suggests(SKILL_SUGGEST)
                                        .executes(ctx -> assign(ctx, false))
                                        .then(Commands.argument("bypass", BoolArgumentType.bool())
                                                .executes(ctx -> assign(ctx, BoolArgumentType.getBool(ctx, "bypass")))))))
                .then(Commands.literal("revoke")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("skill", ResourceLocationArgument.id()).suggests(SKILL_SUGGEST)
                                        .executes(this::revoke))))
                .then(buildSlotsCommands())
                .then(Commands.argument("profile", GameProfileArgument.gameProfile())
                        .then(Commands.literal("list")
                                .executes(ctx -> listPlayerSkills(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "profile")))));
    }

    private int slotsView(CommandSourceStack src, SkillCategory cat) {
        ServerLevel level = src.getLevel();
        SlotLedger ledger = SlotLedger.get(level);
        List<String> lines = new ArrayList<>();
        for (Map.Entry<ResourceLocation, SlotLedger.SkillState> e : ledger.categories.get(cat).entrySet()) {
            lines.add(e.getKey() + "=" + e.getValue().capacity);
        }
        if (lines.isEmpty()) {
            src.sendSuccess(Component.literal("No skills registered for " + (cat == SkillCategory.UNIQUE ? "unique" : "ultimate") + "."), false);
            return 0;
        }
        lines.sort(String::compareTo);
        src.sendSuccess(Component.literal(String.join(" | ", lines)), false);
        return lines.size();
    }

    private int slotsSet(CommandSourceStack src, SkillCategory cat, ResourceLocation id, int n) {
        // Persist in manager + ledger
        ServerLevel level = src.getLevel();
        SkillCategory detected = SkillConfigManager.INSTANCE.classify(id);
        if (detected != null) cat = detected;
        SkillConfigManager.INSTANCE.setCapacity(id, n);
        SlotLedger ledger = SlotLedger.get(level);
        SlotLedger.SkillState state = ledger.getOrCreateSkill(cat, id);
        if (state.capacity != n) {
            state.capacity = n;
            state.epoch++;
            ledger.setDirty();
        }
        src.sendSuccess(Component.literal("Set capacity of " + id + " to " + n + "."), true);
        return 1;
    }

    private int slotsAdd(CommandSourceStack src, SkillCategory cat, ResourceLocation id, int n) {
        ServerLevel level = src.getLevel();
        SkillCategory detected = SkillConfigManager.INSTANCE.classify(id);
        if (detected != null) cat = detected;
        SlotLedger ledger = SlotLedger.get(level);
        SlotLedger.SkillState state = ledger.getOrCreateSkill(cat, id);
        int newCap = Math.max(0, state.capacity + n);
        return slotsSet(src, cat, id, newCap);
    }

    private int slotsRemove(CommandSourceStack src, SkillCategory cat, ResourceLocation id, int n) {
        ServerLevel level = src.getLevel();
        SkillCategory detected = SkillConfigManager.INSTANCE.classify(id);
        if (detected != null) cat = detected;
        SlotLedger ledger = SlotLedger.get(level);
        SlotLedger.SkillState state = ledger.getOrCreateSkill(cat, id);
        int newCap = Math.max(0, state.capacity - n);
        return slotsSet(src, cat, id, newCap);
    }

    private int slotsSetAll(CommandSourceStack src, SkillCategory cat, int n) {
        ServerLevel level = src.getLevel();
        SlotLedger ledger = SlotLedger.get(level);
        int changed = 0;
        for (Map.Entry<ResourceLocation, SlotLedger.SkillState> e : ledger.categories.get(cat).entrySet()) {
            ResourceLocation id = e.getKey();
            SlotLedger.SkillState state = e.getValue();
            if (state.capacity != n) {
                SkillConfigManager.INSTANCE.setCapacity(id, n);
                state.capacity = n;
                state.epoch++;
                changed++;
            }
        }
        if (changed > 0) ledger.setDirty();
        src.sendSuccess(Component.literal("Set capacity to " + n + " for " + changed + " " + (cat == SkillCategory.UNIQUE ? "unique" : "ultimate") + " skills."), true);
        return changed;
    }

    private int slotsAddAll(CommandSourceStack src, SkillCategory cat, int n) {
        ServerLevel level = src.getLevel();
        SlotLedger ledger = SlotLedger.get(level);
        int changed = 0;
        for (Map.Entry<ResourceLocation, SlotLedger.SkillState> e : ledger.categories.get(cat).entrySet()) {
            ResourceLocation id = e.getKey();
            SlotLedger.SkillState state = e.getValue();
            int newCap = Math.max(0, state.capacity + n);
            if (newCap != state.capacity) {
                SkillConfigManager.INSTANCE.setCapacity(id, newCap);
                state.capacity = newCap;
                state.epoch++;
                changed++;
            }
        }
        if (changed > 0) ledger.setDirty();
        src.sendSuccess(Component.literal("Added " + n + " slots to " + changed + " " + (cat == SkillCategory.UNIQUE ? "unique" : "ultimate") + " skills."), true);
        return changed;
    }

    private int slotsRemoveAll(CommandSourceStack src, SkillCategory cat, int n) {
        ServerLevel level = src.getLevel();
        SlotLedger ledger = SlotLedger.get(level);
        int changed = 0;
        for (Map.Entry<ResourceLocation, SlotLedger.SkillState> e : ledger.categories.get(cat).entrySet()) {
            ResourceLocation id = e.getKey();
            SlotLedger.SkillState state = e.getValue();
            int newCap = Math.max(0, state.capacity - n);
            if (newCap != state.capacity) {
                SkillConfigManager.INSTANCE.setCapacity(id, newCap);
                state.capacity = newCap;
                state.epoch++;
                changed++;
            }
        }
        if (changed > 0) ledger.setDirty();
        src.sendSuccess(Component.literal("Removed " + n + " slots from " + changed + " " + (cat == SkillCategory.UNIQUE ? "unique" : "ultimate") + " skills."), true);
        return changed;
    }

    private int blacklistView(CommandSourceStack src) {
        SkillConfigManager mgr = SkillConfigManager.INSTANCE;
        List<String> lines = new ArrayList<>();
        lines.add("uniqueAll=" + mgr.isBlacklistUniqueAll());
        lines.add("ultimateAll=" + mgr.isBlacklistUltimateAll());
        var skills = new ArrayList<>(mgr.getBlacklistedSkills());
        skills.sort(Comparator.comparing(ResourceLocation::toString));
        if (skills.isEmpty()) {
            lines.add("skills=[]");
        } else {
            lines.add("skills=" + skills.stream().map(ResourceLocation::toString).toList());
        }
        src.sendSuccess(Component.literal(String.join(" | ", lines)), false);
        return lines.size();
    }

    private int listRemaining(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        SlotLedger ledger = SlotLedger.get(level);
        List<String> segments = new ArrayList<>();
        for (SkillCategory cat : SkillCategory.values()) {
            if (!WorldVoiceGameRules.isEnabled(level, cat)) continue;
            for (Map.Entry<ResourceLocation, SlotLedger.SkillState> entry : ledger.categories.get(cat).entrySet()) {
                SlotLedger.SkillState state = entry.getValue();
                int remaining = Math.max(state.capacity - state.holders.size(), 0);
                if (remaining <= 0) continue;
                segments.add(entry.getKey() + " - " + remaining);
            }
        }
        if (segments.isEmpty()) {
            src.sendSuccess(Component.literal("No open slots remain."), false);
        } else {
            src.sendSuccess(Component.literal(String.join(" | ", segments)), false);
        }
        return segments.size();
    }

    private int listPlayerSkills(CommandSourceStack src, Collection<GameProfile> profiles) {
        if (profiles.isEmpty()) {
            src.sendFailure(Component.literal("No players matched the selector."));
            return 0;
        }
        ServerLevel level = src.getLevel();
        SlotLedger ledger = SlotLedger.get(level);
        int shown = 0;
        for (GameProfile profile : profiles) {
            UUID id = profile.getId();
            if (id == null) continue;
            SlotLedger.PlayerRecord record = ledger.getOrCreatePlayer(id, profile.getName());
            List<String> owned = new ArrayList<>();
            for (SkillCategory cat : SkillCategory.values()) {
                for (Map.Entry<ResourceLocation, SlotLedger.SkillState> entry : ledger.categories.get(cat).entrySet()) {
                    if (entry.getValue().holders.contains(id)) {
                        owned.add(entry.getKey().toString());
                    }
                }
            }
            if (owned.isEmpty()) {
                src.sendSuccess(Component.literal(profile.getName() + " - none"), false);
            } else {
                src.sendSuccess(Component.literal(profile.getName() + " - " + String.join(", ", owned)), false);
            }
            shown++;
        }
        return shown;
    }

    private int refreshTaken(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        SlotLedger ledger = SlotLedger.get(level);
        int cleared = 0;
        for (SkillCategory cat : SkillCategory.values()) {
            for (Map.Entry<ResourceLocation, SlotLedger.SkillState> entry : ledger.categories.get(cat).entrySet()) {
                if (entry.getValue().holders.isEmpty()) continue;
                List<UUID> holders = new ArrayList<>(entry.getValue().holders);
                cleared += skills.clearSkill(level, cat, entry.getKey(), "refresh-taken");
                if (org.faded.worldvoice.config.WVConfig.REVOKE_SKILLS_ON_REFRESH.get())
                    forgetOnline(level, entry.getKey(), holders);
            }
        }
        src.sendSuccess(Component.literal("Cleared " + cleared + " slot holder(s)."), true);
        return cleared;
    }

    private int refreshSkill(CommandSourceStack src, ResourceLocation skillId) {
        ServerLevel level = src.getLevel();
        SkillCategory cat = SkillConfigManager.INSTANCE.classify(skillId);
        if (cat == null) {
            src.sendFailure(Component.literal("WorldVoice does not manage skill " + skillId + "."));
            return 0;
        }
        SlotLedger.SkillState state = SlotLedger.get(level).getOrCreateSkill(cat, skillId);
        List<UUID> holders = new ArrayList<>(state.holders);
        int cleared = skills.clearSkill(level, cat, skillId, "refresh-skill");
        if (org.faded.worldvoice.config.WVConfig.REVOKE_SKILLS_ON_REFRESH.get())
            forgetOnline(level, skillId, holders);
        src.sendSuccess(Component.literal("Cleared " + cleared + " holder(s) from " + skillId + "."), true);
        return cleared;
    }

    private int refreshConfig(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        SkillConfigManager.INSTANCE.reload();
        SkillConfigManager.INSTANCE.applyToLedger(level);
        src.sendSuccess(Component.literal("WorldVoice config reloaded and applied."), true);
        return 1;
    }

    private int refreshInactive(CommandSourceStack src, int days) {
        ServerLevel level = src.getLevel();
        SlotLedger ledger = SlotLedger.get(level);
        long now = System.currentTimeMillis();
        long ttlMs = days * 24L * 60L * 60L * 1000L;
        int removed = 0;
        for (SkillCategory cat : SkillCategory.values()) {
            if (!WorldVoiceGameRules.isEnabled(level, cat)) continue;
            for (Map.Entry<ResourceLocation, SlotLedger.SkillState> entry : ledger.categories.get(cat).entrySet()) {
                var state = entry.getValue();
                var iterator = state.holders.iterator();
                while (iterator.hasNext()) {
                    UUID holder = iterator.next();
                    SlotLedger.PlayerRecord pr = ledger.players.get(holder);
                    if (pr == null || pr.refreshExempt) continue;
                    if (now - pr.lastSeenMs > ttlMs) {
                        iterator.remove();
                        removed++;
                        LogJsonL.append(String.format("{\\\"ts\\\":\\\"%s\\\",\\\"event\\\":\\\"expire\\\",\\\"skillId\\\":\\\"%s\\\",\\\"category\\\":\\\"%s\\\",\\\"player\\\":\\\"%s\\\",\\\"reason\\\":\\\"inactive-%dd\\\"}",
                                LogJsonL.iso8601(now), entry.getKey(), cat, holder, days));
                        if (org.faded.worldvoice.config.WVConfig.REVOKE_SKILLS_ON_REFRESH.get())
                            forgetOnline(level, entry.getKey(), List.of(holder));
                    }
                }
            }
        }
        if (removed > 0) {
            ledger.setDirty();
        }
        src.sendSuccess(Component.literal("Expired " + removed + " holder(s) inactive for " + days + " day(s)."), true);
        return removed;
    }

    private int whitelist(CommandSourceStack src, Collection<GameProfile> profiles, ResourceLocation scopeId, boolean add) throws CommandSyntaxException {
        if (profiles.isEmpty()) {
            src.sendFailure(Component.literal("No players matched the selector."));
            return 0;
        }
        Scope scope = resolveScope(scopeId);
        ServerLevel level = src.getLevel();
        SlotLedger ledger = SlotLedger.get(level);
        int updated = 0;
        for (GameProfile profile : profiles) {
            UUID id = profile.getId();
            if (id == null) continue;
            SlotLedger.PlayerRecord record = ledger.getOrCreatePlayer(id, profile.getName());
            boolean changed = applyScope(record, scope, add);
            if (changed) {
                updated++;
                ledger.setDirty();
                src.sendSuccess(Component.literal((add ? "Added " : "Removed ") + describeScope(scope)
                        + " for " + profile.getName()), true);
            }
        }
        if (updated == 0) {
            src.sendFailure(Component.literal("No whitelist state changed."));
        }
        return updated;
    }

    private int whitelistList(CommandSourceStack src) {
        SlotLedger ledger = SlotLedger.get(src.getLevel());
        int count = 0;
        for (Map.Entry<UUID, SlotLedger.PlayerRecord> entry : ledger.players.entrySet()) {
            SlotLedger.PlayerRecord record = entry.getValue();
            List<String> tokens = new ArrayList<>();
            if (record.bypassAll) tokens.add("Everything");
            for (SkillCategory cat : record.bypassCategories) {
                tokens.add(capitalize(cat.name().toLowerCase(Locale.ROOT)) + "s");
            }
            for (ResourceLocation id : record.bypassSkills) {
                tokens.add(id.toString());
            }
            if (tokens.isEmpty()) continue;
            String name = resolveName(record, entry.getKey());
            src.sendSuccess(Component.literal(name + " - " + String.join("/", tokens)), false);
            count++;
        }
        if (count == 0) {
            src.sendSuccess(Component.literal("No players are slot-whitelisted."), false);
        }
        return count;
    }

    private int whitelistBlacklist(CommandSourceStack src, Collection<GameProfile> profiles, ResourceLocation scopeId, boolean add) throws CommandSyntaxException {
        if (profiles.isEmpty()) {
            src.sendFailure(Component.literal("No players matched the selector."));
            return 0;
        }
        Scope scope = resolveScope(scopeId);
        ServerLevel level = src.getLevel();
        SlotLedger ledger = SlotLedger.get(level);
        int updated = 0;
        for (GameProfile profile : profiles) {
            UUID id = profile.getId();
            if (id == null) continue;
            SlotLedger.PlayerRecord record = ledger.getOrCreatePlayer(id, profile.getName());
            boolean changed = applyBlacklistScope(record, scope, add);
            if (changed) {
                updated++;
                ledger.setDirty();
                src.sendSuccess(Component.literal((add ? "Added " : "Removed ") + describeScope(scope)
                        + " blacklist bypass for " + profile.getName()), true);
            }
        }
        if (updated == 0) {
            src.sendFailure(Component.literal("No blacklist bypass state changed."));
        }
        return updated;
    }

    private int whitelistBlacklistList(CommandSourceStack src) {
        SlotLedger ledger = SlotLedger.get(src.getLevel());
        int count = 0;
        for (Map.Entry<UUID, SlotLedger.PlayerRecord> entry : ledger.players.entrySet()) {
            SlotLedger.PlayerRecord record = entry.getValue();
            List<String> tokens = new ArrayList<>();
            if (record.blacklistBypassAll) tokens.add("Everything");
            for (SkillCategory cat : record.blacklistBypassCategories) tokens.add(capitalize(cat.name().toLowerCase(Locale.ROOT)) + "s");
            for (ResourceLocation id : record.blacklistBypassSkills) tokens.add(id.toString());
            if (tokens.isEmpty()) continue;
            String name = resolveName(record, entry.getKey());
            src.sendSuccess(Component.literal("[Blacklist] " + name + " - " + String.join("/", tokens)), false);
            count++;
        }
        if (count == 0) src.sendSuccess(Component.literal("No players are blacklists-whitelisted."), false);
        return count;
    }

    private boolean applyBlacklistScope(SlotLedger.PlayerRecord record, Scope scope, boolean add) {
        switch (scope.type) {
            case EVERYTHING -> {
                boolean before = record.blacklistBypassAll;
                if (add) { record.addBlacklistBypassAll(); return !before; }
                else { record.removeBlacklistBypassAll(); return before; }
            }
            case CATEGORY -> {
                boolean before = record.blacklistBypassCategories.contains(scope.category);
                if (add) { record.addBlacklistBypassCategory(scope.category); return !before; }
                else { record.removeBlacklistBypassCategory(scope.category); return before; }
            }
            case SKILL -> {
                boolean before = record.blacklistBypassSkills.contains(scope.skillId);
                if (add) { record.addBlacklistBypassSkill(scope.skillId); return !before; }
                else { record.removeBlacklistBypassSkill(scope.skillId); return before; }
            }
        }
        return false;
    }

    private int whitelistInactive(CommandSourceStack src, Collection<GameProfile> profiles, boolean add) {
        if (profiles.isEmpty()) {
            src.sendFailure(Component.literal("No players matched the selector."));
            return 0;
        }
        ServerLevel level = src.getLevel();
        SlotLedger ledger = SlotLedger.get(level);
        int updated = 0;
        for (GameProfile profile : profiles) {
            UUID id = profile.getId();
            if (id == null) continue;
            SlotLedger.PlayerRecord record = ledger.getOrCreatePlayer(id, profile.getName());
            if (record.refreshExempt != add) {
                record.refreshExempt = add;
                ledger.setDirty();
                updated++;
                src.sendSuccess(Component.literal((add ? "Added " : "Removed ") + "inactive refresh exemption for " + profile.getName()), true);
            }
        }
        if (updated == 0) {
            src.sendFailure(Component.literal("No refresh exemption state changed."));
        }
        return updated;
    }

    private int whitelistInactiveList(CommandSourceStack src) {
        SlotLedger ledger = SlotLedger.get(src.getLevel());
        int count = 0;
        for (Map.Entry<UUID, SlotLedger.PlayerRecord> entry : ledger.players.entrySet()) {
            if (!entry.getValue().refreshExempt) continue;
            String name = resolveName(entry.getValue(), entry.getKey());
            src.sendSuccess(Component.literal(name + " (" + entry.getKey() + ")"), false);
            count++;
        }
        if (count == 0) {
            src.sendSuccess(Component.literal("No players are exempt from inactivity refresh."), false);
        }
        return count;
    }

    private String formatHoldersList(SlotLedger ledger, Collection<UUID> holders) {
        StringJoiner joiner = new StringJoiner(", ");
        for (UUID holder : holders) {
            joiner.add(resolveName(ledger.players.get(holder), holder));
        }
        return joiner.toString();
    }

    private void forgetOnline(ServerLevel level, ResourceLocation skillId, Collection<UUID> holders) {
        ManasSkill skill = SkillAPI.getSkillRegistry().getValue(skillId);
        if (skill == null) return;
        for (UUID holder : holders) {
            ServerPlayer online = level.getServer().getPlayerList().getPlayer(holder);
            if (online != null) {
                SkillAPI.getSkillsFrom(online).forgetSkill(skill);
            }
        }
    }

    private Scope resolveScope(ResourceLocation id) throws CommandSyntaxException {
        String path = id.getPath().toLowerCase(Locale.ROOT);
        if (path.equals("everything") || path.equals("all") || path.equals("any")) {
            return new Scope(ScopeType.EVERYTHING, null, null);
        }
        if (path.startsWith("unique")) {
            return new Scope(ScopeType.CATEGORY, SkillCategory.UNIQUE, null);
        }
        if (path.startsWith("ultimate") || path.startsWith("ult")) {
            return new Scope(ScopeType.CATEGORY, SkillCategory.ULTIMATE, null);
        }
        SkillCategory cat = SkillConfigManager.INSTANCE.classify(id);
        if (cat == null) {
            throw INVALID_SCOPE.create();
        }
        return new Scope(ScopeType.SKILL, cat, id);
    }

    private boolean applyScope(SlotLedger.PlayerRecord record, Scope scope, boolean add) {
        switch (scope.type) {
            case EVERYTHING -> {
                boolean before = record.bypassAll;
                if (add) {
                    record.addBypassAll();
                    return !before;
                } else {
                    record.removeBypassAll();
                    return before;
                }
            }
            case CATEGORY -> {
                if (scope.category == null) return false;
                boolean before = record.bypassCategories.contains(scope.category);
                if (add) {
                    record.addBypassCategory(scope.category);
                    return !before;
                } else {
                    record.removeBypassCategory(scope.category);
                    return before;
                }
            }
            case SKILL -> {
                if (scope.skillId == null) return false;
                boolean before = record.bypassSkills.contains(scope.skillId);
                if (add) {
                    record.addBypassSkill(scope.skillId);
                    return !before;
                } else {
                    record.removeBypassSkill(scope.skillId);
                    return before;
                }
            }
            default -> {
                return false;
            }
        }
    }

    private YggdrasilService requireYggdrasil() throws CommandSyntaxException {
        return YggdrasilHooks.instance().orElseThrow(() -> NO_YGG.create());
    }

    private void sendPlayerSummary(CommandSourceStack src, ServerPlayer player, YggdrasilSavedData.PlayerRecord record) {
        long now = System.currentTimeMillis();
        long favorMs = Math.max(0L, record.favorUntilMs - now);
        long cooldownMs = Math.max(0L, record.lastWishMs + WVConfig.YGG_WISH_COOLDOWN_MINUTES.get() * 60_000L - now);
        src.sendSuccess(Component.literal(player.getGameProfile().getName()
                        + " | favorRemaining=" + favorMs / 1000L + "s"
                        + " | favorStrength=" + String.format("%.2f", record.favorStrength)
                        + " | wishCooldown=" + cooldownMs / 1000L + "s"
                        + " | uniqueGranted=" + record.uniqueGranted),
                false);
    }

    private String describeScope(Scope scope) {
        return switch (scope.type) {
            case EVERYTHING -> "everything";
            case CATEGORY -> scope.category == SkillCategory.UNIQUE ? "unique skills" : "ultimate skills";
            case SKILL -> scope.skillId != null ? scope.skillId.toString() : "skill";
        };
    }

    private String resolveName(SlotLedger.PlayerRecord record, UUID uuid) {
        if (record != null && record.lastKnownName != null && !record.lastKnownName.isBlank()) {
            return record.lastKnownName;
        }
        return uuid.toString();
    }

    private String capitalize(String value) {
        if (value.isEmpty()) return value;
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private enum ScopeType {
        EVERYTHING,
        CATEGORY,
        SKILL
    }

    private record Scope(ScopeType type, SkillCategory category, ResourceLocation skillId) {
    }
}
