package org.faded.worldvoice.quest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;
import org.faded.worldvoice.Worldvoice;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;


public class QuestLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final LevelResource SERVER_CONFIG_RESOURCE = new LevelResource("serverconfig");
    private static final List<AdvancedQuestData> loadedQuests = new ArrayList<>();
    private static MinecraftServer currentServer = null;

    public static void loadQuests(MinecraftServer server) {
        currentServer = server;
        loadedQuests.clear();

        loadedQuests.addAll(KubeJSQuestRegistry.getRegisteredQuests());

        Path serverQuestDir = resolveServerQuestDir(server);
        Path globalQuestDir = resolveGlobalQuestDir();

        createQuestDirectories(serverQuestDir, globalQuestDir);

        boolean loadedFromServer = loadQuestsFromDirectory(serverQuestDir);
        boolean loadedFromGlobal = false;

        if (!loadedFromServer) {
            Worldvoice.LOGGER.info("No quests found in per-world serverconfig, loading from global config");
            loadedFromGlobal = loadQuestsFromDirectory(globalQuestDir);
        }

        int totalQuests = loadedQuests.size();
        String source;
        if (loadedFromServer) {
            source = "per-world serverconfig (" + serverQuestDir + ")";
        } else if (loadedFromGlobal) {
            source = "global configs (" + globalQuestDir + ")";
        } else {
            source = "KubeJS only";
        }
        Worldvoice.LOGGER.info("Loaded {} quests from {}", totalQuests, source);
    }

    private static boolean loadQuestsFromDirectory(Path baseDir) {
        if (baseDir == null) {
            return false;
        }
        int beforeCount = loadedQuests.size();

        loadQuestsFromFolder(baseDir, "daily", QuestTab.DAILY, loadedQuests);
        loadQuestsFromFolder(baseDir, "weekly", QuestTab.WEEKLY, loadedQuests);
        loadQuestsFromFolder(baseDir, "one_time", QuestTab.ONE_TIME, loadedQuests);
        loadQuestsFromFolder(baseDir, "special", QuestTab.SPECIAL, loadedQuests);
        loadQuestsFromFolder(baseDir, "evolution", QuestTab.EVOLUTION, loadedQuests);

        int afterCount = loadedQuests.size();
        boolean loaded = afterCount > beforeCount;

        if (loaded) {
            Worldvoice.LOGGER.debug("Loaded {} quests from {}", afterCount - beforeCount, baseDir);
        }

        return loaded;
    }

    private static void createQuestDirectories(Path serverQuestDir, Path globalQuestDir) {
        createDirectoriesForPath(serverQuestDir);
        createDirectoriesForPath(globalQuestDir);

        createExampleQuests(globalQuestDir);
    }

    private static void createDirectoriesForPath(Path questDir) {
        if (questDir == null) {
            return;
        }
        try {
            Files.createDirectories(questDir.resolve("daily"));
            Files.createDirectories(questDir.resolve("weekly"));
            Files.createDirectories(questDir.resolve("one_time"));
            Files.createDirectories(questDir.resolve("special"));
            Files.createDirectories(questDir.resolve("evolution"));
            Worldvoice.LOGGER.debug("Created quest directories in {}", questDir);
        } catch (Exception e) {
            Worldvoice.LOGGER.error("Failed to create quest directories in {}", questDir, e);
        }
    }

    private static void createExampleQuests(Path questDir) {
        if (questDir == null) {
            return;
        }
        Path dailyDir = questDir.resolve("daily");
        try {
            if (!Files.exists(dailyDir)) {
                return;
            }

            if (Files.list(dailyDir).findAny().isEmpty()) {
                String exampleQuest = """
                {
                  "quest_id": "worldvoice:example_daily_quest",
                  "title": "Daily Monster Hunt",
                  "description": "Defeat 10 zombies to earn rewards",
                  "category": "DAILY",
                  "status": "AVAILABLE",
                  "auto_appear": true,
                  "objectives": [
                    {
                      "type": "kill_mob",
                      "display": "Cull shambling zombies",
                      "mob_type": "minecraft:zombie",
                      "required": 10,
                      "current": 0
                    }
                  ],
                  "rewards": [
                    {
                      "type": "item",
                      "display": "3 Precious Diamonds",
                      "item_id": "minecraft:diamond",
                      "count": 3
                    },
                    {
                      "type": "experience",
                      "display": "Bonus Experience",
                      "levels": 5
                    }
                  ],
                  "requirements": []
                }
                """;

                Files.writeString(questDir.resolve("daily").resolve("example_quest.json"), exampleQuest);
                Worldvoice.LOGGER.info("Created example daily quest in {}", questDir);
            }
        } catch (Exception e) {
            Worldvoice.LOGGER.warn("Failed to create example quest in {}", questDir, e);
        }
    }

    private static void loadQuestsFromFolder(Path baseDir, String folderName, QuestTab category, List<AdvancedQuestData> questList) {
        if (baseDir == null) {
            return;
        }
        Path folderPath = baseDir.resolve(folderName);

        if (!Files.exists(folderPath)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(folderPath)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            loadQuestFromFile(path.toFile(), category, questList);
                        } catch (Exception e) {
                            Worldvoice.LOGGER.error("Failed to load quest from {}: {}", path, e.getMessage());
                        }
                    });
        } catch (Exception e) {
            Worldvoice.LOGGER.error("Failed to read quests from folder {}", folderName, e);
        }
    }

    private static void loadQuestFromFile(File file, QuestTab category, List<AdvancedQuestData> questList) {
        try (FileReader reader = new FileReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            json.addProperty("category", category.name());

            AdvancedQuestData quest = AdvancedQuestData.fromJson(json);
            questList.add(quest);

            Worldvoice.LOGGER.debug("Loaded quest: {} from {}", quest.getTitle(), file.getName());
        } catch (Exception e) {
            Worldvoice.LOGGER.error("Failed to parse quest file {}: {}", file.getName(), e.getMessage());
        }
    }

    public static List<AdvancedQuestData> getLoadedQuests() {
        return new ArrayList<>(loadedQuests);
    }

    public static void assignQuestsToPlayer(ServerPlayer player) {
        int assignedCount = 0;

        for (AdvancedQuestData quest : loadedQuests) {
            if (ServerQuestManager.getQuest(player.getUUID(), quest.getQuestId()) != null) {
                continue;
            }

            if (!quest.isAutoAppear()) {
                continue;
            }

            if (!quest.areRequirementsMet(player)) {
                continue;
            }

            AdvancedQuestData playerQuest = cloneQuest(quest);
            playerQuest.setStatus(QuestStatus.AVAILABLE);
            playerQuest.setTracked(false);
            playerQuest.setCompletedTimestamp(0);
            playerQuest.setLastResetTimestamp(0);
            ServerQuestManager.addQuestToPlayer(player.getUUID(), playerQuest);
            assignedCount++;
        }

        if (assignedCount > 0) {
            ServerQuestManager.syncQuests(player);
            Worldvoice.LOGGER.info("Assigned {} quests to player {}", assignedCount, player.getGameProfile().getName());
        }
    }

    public static void registerQuest(JsonObject questJson) {
        try {
            AdvancedQuestData quest = AdvancedQuestData.fromJson(questJson);
            KubeJSQuestRegistry.register(quest);
            Worldvoice.LOGGER.info("Registered quest dynamically: {}", quest.getTitle());
        } catch (Exception e) {
            Worldvoice.LOGGER.error("Failed to register quest from JSON: {}", e.getMessage());
        }
    }

    private static AdvancedQuestData cloneQuest(AdvancedQuestData original) {

        JsonObject json = original.toJson();
        return AdvancedQuestData.fromJson(json);
    }

    public static void reload() {
        if (currentServer != null) {
            Worldvoice.LOGGER.info("Reloading quests from configuration...");
            loadQuests(currentServer);
        } else {
            Worldvoice.LOGGER.warn("Cannot reload quests: server instance not available");
        }
    }

    private static Path resolveServerQuestDir(MinecraftServer server) {
        try {
            Path serverConfigDir = server.getWorldPath(SERVER_CONFIG_RESOURCE);
            return serverConfigDir.resolve("worldvoice").resolve("quests");
        } catch (Exception e) {
            Worldvoice.LOGGER.error("Failed to resolve per-world quest directory", e);
            return null;
        }
    }

    private static Path resolveGlobalQuestDir() {
        try {
            Path gameDir = FMLPaths.GAMEDIR.get();
            Path configsDir = gameDir.resolve("configs");
            if (!Files.exists(configsDir)) {
                configsDir = FMLPaths.CONFIGDIR.get();
            }
            return configsDir.resolve("worldvoice").resolve("quests");
        } catch (Exception e) {
            Worldvoice.LOGGER.error("Failed to resolve global quest directory", e);
            return null;
        }
    }
}
