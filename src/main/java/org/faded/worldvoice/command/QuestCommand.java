package org.faded.worldvoice.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.faded.worldvoice.quest.*;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;


public class QuestCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wvquest")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("give")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("questId", StringArgumentType.string())
                                        .executes(QuestCommand::giveQuest)
                                )
                        )
                )
                .then(Commands.literal("remove")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("questId", StringArgumentType.string())
                                        .executes(QuestCommand::removeQuest)
                                )
                        )
                )
                .then(Commands.literal("complete")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("questId", StringArgumentType.string())
                                        .executes(QuestCommand::completeQuest)
                                )
                        )
                )
                .then(Commands.literal("reset")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("questId", StringArgumentType.string())
                                        .executes(QuestCommand::resetQuest)
                                )
                        )
                )
                .then(Commands.literal("resetall")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(QuestCommand::resetAllQuests)
                        )
                )
                .then(Commands.literal("list")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(QuestCommand::listQuests)
                        )
                )
                .then(Commands.literal("reload")
                        .executes(QuestCommand::reloadQuests)
                )
                .then(Commands.literal("forcerefresh")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("type", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            builder.suggest("daily");
                                            builder.suggest("weekly");
                                            return builder.buildFuture();
                                        })
                                        .executes(QuestCommand::forceRefresh)
                                )
                        )
                )
        );
    }

    private static int giveQuest(CommandContext<CommandSourceStack> context) {
        try {
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
            String questIdStr = StringArgumentType.getString(context, "questId");
            UUID questId = parseQuestId(questIdStr);

            int successCount = 0;
            for (ServerPlayer player : targets) {
                AdvancedQuestData sourceQuest = QuestLoader.getLoadedQuests().stream()
                        .filter(q -> q.getQuestId().equals(questId))
                        .findFirst()
                        .orElse(null);

                if (sourceQuest == null) {
                    context.getSource().sendFailure(Component.literal("Quest with ID " + questIdStr + " not found"));
                    continue;
                }

                AdvancedQuestData playerQuest = AdvancedQuestData.fromJson(sourceQuest.toJson());
                ServerQuestManager.addQuestToPlayer(player.getUUID(), playerQuest);
                ServerQuestManager.syncQuests(player);
                successCount++;
            }

            if (successCount > 0) {
                context.getSource().sendSuccess(Component.literal(
                        "Gave quest to " + successCount + " player(s)"), true);
                return Command.SINGLE_SUCCESS;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
        }
        return 0;
    }

    private static UUID parseQuestId(String questIdStr) {
        try {
            return UUID.fromString(questIdStr);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(questIdStr.getBytes());
        }
    }

    private static int removeQuest(CommandContext<CommandSourceStack> context) {
        try {
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
            String questIdStr = StringArgumentType.getString(context, "questId");
            UUID questId = parseQuestId(questIdStr);

            int successCount = 0;
            for (ServerPlayer player : targets) {
                Map<UUID, AdvancedQuestData> quests = ServerQuestManager.getPlayerQuests(player.getUUID());
                if (quests.remove(questId) != null) {
                    ServerQuestManager.syncQuests(player);
                    successCount++;
                }
            }

            if (successCount > 0) {
                context.getSource().sendSuccess(Component.literal(
                        "Removed quest from " + successCount + " player(s)"), true);
                return Command.SINGLE_SUCCESS;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
        }
        return 0;
    }

    private static int completeQuest(CommandContext<CommandSourceStack> context) {
        try {
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
            String questIdStr = StringArgumentType.getString(context, "questId");
            UUID questId = parseQuestId(questIdStr);

            int successCount = 0;
            for (ServerPlayer player : targets) {
                AdvancedQuestData quest = ServerQuestManager.getQuest(player.getUUID(), questId);
                if (quest != null && !quest.isCompleted()) {
                    for (var objective : quest.getObjectives()) {
                        try {
                            objective.getClass().getMethod("setCurrentCount", int.class)
                                    .invoke(objective, Integer.MAX_VALUE);
                        } catch (Exception ignored) {
                        }
                    }

                    quest.setStatus(QuestStatus.COMPLETED);
                    quest.setCompletedTimestamp(System.currentTimeMillis());
                    ServerQuestManager.syncQuests(player);
                    QuestLoader.assignQuestsToPlayer(player);
                    successCount++;
                }
            }

            if (successCount > 0) {
                context.getSource().sendSuccess(Component.literal(
                        "Completed quest for " + successCount + " player(s)"), true);
                return Command.SINGLE_SUCCESS;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
        }
        return 0;
    }

    private static int resetQuest(CommandContext<CommandSourceStack> context) {
        try {
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
            String questIdStr = StringArgumentType.getString(context, "questId");
            UUID questId = parseQuestId(questIdStr);

            int successCount = 0;
            for (ServerPlayer player : targets) {
                AdvancedQuestData quest = ServerQuestManager.getQuest(player.getUUID(), questId);
                if (quest != null) {
                    quest.setStatus(QuestStatus.AVAILABLE);
                    quest.setTracked(false);
                    quest.setCompletedTimestamp(0);

                    for (var objective : quest.getObjectives()) {
                        try {
                            objective.getClass().getMethod("setCurrentCount", int.class)
                                    .invoke(objective, 0);
                        } catch (Exception ignored) {
                        }
                    }

                    ServerQuestManager.syncQuests(player);
                    successCount++;
                }
            }

            if (successCount > 0) {
                context.getSource().sendSuccess(Component.literal(
                        "Reset quest for " + successCount + " player(s)"), true);
                return Command.SINGLE_SUCCESS;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
        }
        return 0;
    }

    private static int resetAllQuests(CommandContext<CommandSourceStack> context) {
        try {
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");

            int successCount = 0;
            for (ServerPlayer player : targets) {
                ServerQuestManager.clearPlayerQuests(player.getUUID());
                ServerQuestManager.syncQuests(player);
                successCount++;
            }

            if (successCount > 0) {
                context.getSource().sendSuccess(Component.literal(
                        "Reset all quests for " + successCount + " player(s)"), true);
                return Command.SINGLE_SUCCESS;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
        }
        return 0;
    }

    private static int listQuests(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "target");
            Map<UUID, AdvancedQuestData> quests = ServerQuestManager.getPlayerQuests(target.getUUID());

            if (quests.isEmpty()) {
                context.getSource().sendSuccess(Component.literal(
                        target.getGameProfile().getName() + " has no quests"), false);
                return Command.SINGLE_SUCCESS;
            }

            context.getSource().sendSuccess(Component.literal(
                    "Quests for " + target.getGameProfile().getName() + ":"), false);

            for (AdvancedQuestData quest : quests.values()) {
                context.getSource().sendSuccess(Component.literal(
                        "  - " + quest.getTitle() + " [" + quest.getStatus() + "] (" + quest.getQuestId() + ")"), false);
            }

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
        }
        return 0;
    }

    private static int reloadQuests(CommandContext<CommandSourceStack> context) {
        try {
            QuestLoader.reload();
            context.getSource().sendSuccess(Component.literal(
                    "Reloaded quests from configuration"), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
        }
        return 0;
    }

    private static int forceRefresh(CommandContext<CommandSourceStack> context) {
        try {
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
            String type = StringArgumentType.getString(context, "type");

            context.getSource().sendSuccess(Component.literal(
                    "Force refresh for " + type + " quests is not yet implemented"), false);

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
        }
        return 0;
    }
}
