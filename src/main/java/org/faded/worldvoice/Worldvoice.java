package org.faded.worldvoice;

import com.github.hvnbael.trnightmare.util.PacketHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.faded.worldvoice.quest.ServerQuestManager;
import org.faded.worldvoice.quest.objective.QuestObjectiveType;
import org.faded.worldvoice.quest.reward.QuestRewardType;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Worldvoice.MODID)
public class Worldvoice {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "worldvoice";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    public Worldvoice() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onConfigLoading);
        modEventBus.addListener(this::onConfigReloading);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register configs
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, org.faded.worldvoice.config.WVConfig.SPEC);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, org.faded.worldvoice.config.WVServerConfig.SERVER_SPEC);

        // Register event handlers
        MinecraftForge.EVENT_BUS.register(new org.faded.worldvoice.events.CommonEvents());
        MinecraftForge.EVENT_BUS.register(new org.faded.worldvoice.command.CommandRegistrar());
        MinecraftForge.EVENT_BUS.register(new org.faded.worldvoice.service.RefreshService());
        MinecraftForge.EVENT_BUS.register(org.faded.worldvoice.quest.QuestEventHandler.class);
        var yggdrasil = new org.faded.worldvoice.ai.YggdrasilService();
        org.faded.worldvoice.ai.YggdrasilHooks.bind(yggdrasil);
        MinecraftForge.EVENT_BUS.register(yggdrasil);
        MinecraftForge.EVENT_BUS.register(new org.faded.worldvoice.ai.WishService(yggdrasil));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");
        event.enqueueWork(org.faded.worldvoice.world.WorldVoiceGameRules::register);

        // Register quest network packets
        event.enqueueWork(org.faded.worldvoice.network.WorldVoicePacketHandler::register);

        // Initialize quest objective, reward, and requirement types
        event.enqueueWork(() -> {
            QuestObjectiveType.init();
            QuestRewardType.init();
            org.faded.worldvoice.quest.requirement.QuestRequirementType.init();
        });
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
        org.faded.worldvoice.core.SkillConfigManager.INSTANCE.reload();
        org.faded.worldvoice.core.SkillConfigManager.INSTANCE.applyToLedger(event.getServer().overworld());

        // Load quests from serverconfig (per-world) with fallback to global config
        org.faded.worldvoice.quest.QuestLoader.loadQuests(event.getServer());
    }

    private void onConfigLoading(final ModConfigEvent.Loading event) {
        if (event.getConfig().getType() == ModConfig.Type.SERVER) {
            org.faded.worldvoice.config.WVServerConfig.bind(event.getConfig());
            org.faded.worldvoice.core.SkillConfigManager.INSTANCE.reload();
        }
    }

    private void onConfigReloading(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getType() == ModConfig.Type.SERVER && net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() != null) {
            var level = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld();
            org.faded.worldvoice.config.WVServerConfig.bind(event.getConfig());
            org.faded.worldvoice.core.SkillConfigManager.INSTANCE.reload();
            org.faded.worldvoice.core.SkillConfigManager.INSTANCE.applyToLedger(level);
        }
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.DEDICATED_SERVER)
    public static class ServerModEvents {
        @SubscribeEvent
        public static void onServerSetup(FMLDedicatedServerSetupEvent event) {
            QuestObjectiveType.init();
            QuestRewardType.init();
        }
    }
}
