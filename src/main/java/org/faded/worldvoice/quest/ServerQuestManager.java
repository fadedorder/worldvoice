package org.faded.worldvoice.quest;

import com.github.manasmods.manascore.api.skills.ManasSkill;
import com.github.manasmods.manascore.api.skills.SkillAPI;
import com.github.manasmods.manascore.api.skills.capability.SkillStorage;
import com.github.manasmods.tensura.ability.TensuraSkillInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import org.faded.worldvoice.Worldvoice;
import org.faded.worldvoice.integration.TensuraBridge;
import org.faded.worldvoice.network.QuestProgressUpdatePacket;
import org.faded.worldvoice.network.QuestSyncPacket;
import org.faded.worldvoice.network.WorldVoicePacketHandler;
import org.faded.worldvoice.quest.objective.*;
import org.faded.worldvoice.quest.reward.*;

import java.util.*;

public class ServerQuestManager {
    private static final Map<UUID, Map<UUID, AdvancedQuestData>> playerQuests = new HashMap<>();

    public static void addQuestToPlayer(UUID playerId, AdvancedQuestData quest) {
        playerQuests.computeIfAbsent(playerId, k -> new HashMap<>())
                .put(quest.getQuestId(), quest);
        Worldvoice.LOGGER.debug("Added quest {} to player {}", quest.getTitle(), playerId);
    }

    public static Map<UUID, AdvancedQuestData> getPlayerQuests(UUID playerId) {
        return playerQuests.getOrDefault(playerId, new HashMap<>());
    }

    public static AdvancedQuestData getQuest(UUID playerId, UUID questId) {
        return getPlayerQuests(playerId).get(questId);
    }

    public static void syncQuests(ServerPlayer player) {
        List<AdvancedQuestData> quests = new ArrayList<>(getPlayerQuests(player.getUUID()).values());
        WorldVoicePacketHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new QuestSyncPacket(quests)
        );
        Worldvoice.LOGGER.debug("Synced {} quests to player {}", quests.size(), player.getGameProfile().getName());
    }

    public static void startQuest(ServerPlayer player, UUID questId) {
        AdvancedQuestData quest = getQuest(player.getUUID(), questId);
        if (quest == null || !quest.canStart()) {
            Worldvoice.LOGGER.warn("Player {} attempted to start invalid quest {}",
                    player.getGameProfile().getName(), questId);
            return;
        }

        quest.setStatus(QuestStatus.ACTIVE);

        for (QuestObjective objective : quest.getObjectives()) {
            if (objective instanceof EPGainRequirement epGain) {
                double currentEP = TensuraBridge.getEp(player);
                epGain.setCurrentEP(currentEP);
            } else if (objective instanceof StatRequirement statReq && statReq.isGainRequirement()) {
                double currentValue = getPlayerStat(player, statReq.getStatName());
                statReq.setCurrentValue(currentValue);
            }
        }

        syncQuests(player);
        Worldvoice.LOGGER.info("Player {} started quest {}", player.getGameProfile().getName(), quest.getTitle());
    }

    public static void cancelQuest(ServerPlayer player, UUID questId) {
        AdvancedQuestData quest = getQuest(player.getUUID(), questId);
        if (quest == null || !quest.canCancel()) {
            return;
        }

        quest.setStatus(QuestStatus.AVAILABLE);
        quest.setTracked(false);

        for (QuestObjective objective : quest.getObjectives()) {
            if (objective instanceof KillMobObjective killObj) {
                killObj.setCurrentKills(0);
            } else if (objective instanceof CollectItemObjective collectObj) {
                collectObj.setCurrentCount(0);
            }
        }

        syncQuests(player);
        Worldvoice.LOGGER.info("Player {} cancelled quest {}", player.getGameProfile().getName(), quest.getTitle());
    }

    public static void trackQuest(ServerPlayer player, UUID questId) {
        AdvancedQuestData quest = getQuest(player.getUUID(), questId);
        if (quest == null || !quest.isActive()) {
            return;
        }

        quest.setTracked(true);
        quest.setStatus(QuestStatus.TRACKED);
        syncQuests(player);
    }

    public static void untrackQuest(ServerPlayer player, UUID questId) {
        AdvancedQuestData quest = getQuest(player.getUUID(), questId);
        if (quest == null) {
            return;
        }

        quest.setTracked(false);
        if (quest.getStatus() == QuestStatus.TRACKED) {
            quest.setStatus(QuestStatus.ACTIVE);
        }
        syncQuests(player);
    }

    public static void submitItems(ServerPlayer player, UUID questId, int objectiveIndex) {
        AdvancedQuestData quest = getQuest(player.getUUID(), questId);
        if (quest == null || !quest.isActive()) {
            return;
        }

        if (objectiveIndex < 0 || objectiveIndex >= quest.getObjectives().size()) {
            return;
        }

        QuestObjective objective = quest.getObjectives().get(objectiveIndex);
        if (!(objective instanceof CollectItemObjective collectObj) || !collectObj.shouldConsumeItems()) {
            return;
        }

        int required = collectObj.getRequiredCount();
        ResourceLocation itemId = collectObj.getItemId();

        int totalCount = 0;
        for (ItemStack stack : player.getInventory().items) {
            var stackItemKey = net.minecraft.core.Registry.ITEM.getKey(stack.getItem());
            if (stackItemKey != null && stackItemKey.equals(itemId)) {
                totalCount += stack.getCount();
            }
        }

        if (totalCount < required) {
            Worldvoice.LOGGER.debug("Player {} does not have enough items for quest objective",
                    player.getGameProfile().getName());
            return;
        }

        int remaining = required;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) break;
            var stackItemKey = net.minecraft.core.Registry.ITEM.getKey(stack.getItem());
            if (stackItemKey != null && stackItemKey.equals(itemId)) {
                int toRemove = Math.min(remaining, stack.getCount());
                stack.shrink(toRemove);
                remaining -= toRemove;
            }
        }

        collectObj.setCurrentCount(required);

        checkQuestCompletion(player, quest);

        WorldVoicePacketHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new QuestProgressUpdatePacket(questId, objectiveIndex, required, required)
        );

        Worldvoice.LOGGER.info("Player {} submitted items for quest {}",
                player.getGameProfile().getName(), quest.getTitle());
    }

    public static void submitEP(ServerPlayer player, UUID questId, int objectiveIndex) {
        AdvancedQuestData quest = getQuest(player.getUUID(), questId);
        if (quest == null || !quest.isActive()) {
            return;
        }

        if (objectiveIndex < 0 || objectiveIndex >= quest.getObjectives().size()) {
            return;
        }

        QuestObjective objective = quest.getObjectives().get(objectiveIndex);
        if (!(objective instanceof EPRequirement epReq) || !epReq.shouldConsumeEP()) {
            return;
        }

        double currentEP = TensuraBridge.getEp(player);
        double required = epReq.getRequiredEP();

        if (currentEP < required) {
            Worldvoice.LOGGER.debug("Player {} does not have enough EP for quest objective",
                    player.getGameProfile().getName());
            return;
        }

        TensuraBridge.addEp(player, -required);

        epReq.setCurrentEP(required);

        checkQuestCompletion(player, quest);

        WorldVoicePacketHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new QuestProgressUpdatePacket(questId, objectiveIndex, (int) required, (int) required)
        );

        Worldvoice.LOGGER.info("Player {} submitted EP for quest {}",
                player.getGameProfile().getName(), quest.getTitle());
    }

    public static void updateKillProgress(ServerPlayer player, ResourceLocation mobType) {
        for (AdvancedQuestData quest : getPlayerQuests(player.getUUID()).values()) {
            if (!quest.isActive()) continue;

            for (int i = 0; i < quest.getObjectives().size(); i++) {
                QuestObjective objective = quest.getObjectives().get(i);
                if (objective instanceof KillMobObjective killObj &&
                    killObj.getMobType().equals(mobType) &&
                    !killObj.isCompleted()) {

                    int newCount = killObj.getCurrentCount() + 1;
                    killObj.setCurrentKills(newCount);

                    WorldVoicePacketHandler.INSTANCE.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            new QuestProgressUpdatePacket(quest.getQuestId(), i, newCount, killObj.getRequiredCount())
                    );

                    checkQuestCompletion(player, quest);
                }
            }
        }
    }

    public static void updateItemProgress(ServerPlayer player, ResourceLocation itemId) {
        for (AdvancedQuestData quest : getPlayerQuests(player.getUUID()).values()) {
            if (!quest.isActive()) continue;

            for (int i = 0; i < quest.getObjectives().size(); i++) {
                QuestObjective objective = quest.getObjectives().get(i);
                if (objective instanceof CollectItemObjective collectObj &&
                    collectObj.getItemId().equals(itemId) &&
                    !collectObj.shouldConsumeItems() &&
                    !collectObj.isCompleted()) {

                    int count = 0;
                    for (ItemStack stack : player.getInventory().items) {
                        var stackItemKey = net.minecraft.core.Registry.ITEM.getKey(stack.getItem());
                        if (stackItemKey != null && stackItemKey.equals(itemId)) {
                            count += stack.getCount();
                        }
                    }

                    collectObj.setCurrentCount(Math.min(count, collectObj.getRequiredCount()));

                    WorldVoicePacketHandler.INSTANCE.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            new QuestProgressUpdatePacket(quest.getQuestId(), i, count, collectObj.getRequiredCount())
                    );

                    checkQuestCompletion(player, quest);
                }
            }
        }
    }

    private static void checkQuestCompletion(ServerPlayer player, AdvancedQuestData quest) {
        if (!quest.areObjectivesCompleted()) {
            return;
        }

        grantRewards(player, quest);

        quest.setStatus(QuestStatus.COMPLETED);
        quest.setTracked(false);
        quest.setCompletedTimestamp(System.currentTimeMillis());

        syncQuests(player);

        Worldvoice.LOGGER.info("Player {} completed quest {}!",
                player.getGameProfile().getName(), quest.getTitle());

        QuestLoader.assignQuestsToPlayer(player);
    }

    private static void grantRewards(ServerPlayer player, AdvancedQuestData quest) {
        for (QuestReward reward : quest.getRewards()) {
            try {
                if (reward instanceof ItemReward itemReward) {
                    ItemStack stack = new ItemStack(
                            net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(itemReward.getItemId()),
                            itemReward.getCount()
                    );
                    player.addItem(stack);

                } else if (reward instanceof CommandReward cmdReward) {
                    player.getServer().getCommands().performPrefixedCommand(
                            player.createCommandSourceStack(),
                            cmdReward.getCommand()
                    );

                } else if (reward instanceof SkillReward skillReward) {
                    ManasSkill skill = SkillAPI.getSkillRegistry().getValue(skillReward.getSkillId());
                    if (skill != null) {
                        SkillStorage storage = SkillAPI.getSkillsFrom(player);
                        TensuraSkillInstance instance = new TensuraSkillInstance(skill);
                        storage.learnSkill(instance);
                    }

                } else if (reward instanceof EPLevelsReward epReward) {
                    TensuraBridge.addEp(player, epReward.getLevels() * 1000.0);

                } else if (reward instanceof MaxMagiculesReward magReward) {
                    TensuraBridge.addMagicules(player, magReward.getAmount());

                } else if (reward instanceof MaxAuraReward auraReward) {
                    TensuraBridge.addAura(player, auraReward.getAmount());

                } else if (reward instanceof SoulPointsReward soulReward) {

                    Worldvoice.LOGGER.warn("Soul points reward not yet implemented");

                } else if (reward instanceof ResetCounterReward resetReward) {

                    Worldvoice.LOGGER.warn("Reset counter reward not yet implemented");

                } else if (reward instanceof ExperienceReward expReward) {
                    player.giveExperienceLevels(expReward.getLevels());
                }

                Worldvoice.LOGGER.debug("Granted reward: {}", reward.getDisplayString());
            } catch (Exception e) {
                Worldvoice.LOGGER.error("Failed to grant reward: {}", reward.getDisplayString(), e);
            }
        }
    }

    private static double getPlayerStat(ServerPlayer player, String statName) {
        return switch (statName) {
            case "max_aura" -> TensuraBridge.getAura(player);
            case "max_mp" -> TensuraBridge.getMagicules(player);
            default -> 0.0;
        };
    }

    public static void clearPlayerQuests(UUID playerId) {
        playerQuests.remove(playerId);
    }
}
