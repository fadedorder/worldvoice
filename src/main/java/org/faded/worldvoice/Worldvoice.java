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


@Mod(Worldvoice.MODID)
public class Worldvoice {


    public static final String MODID = "worldvoice";

    public static final Logger LOGGER = LogUtils.getLogger();
    public Worldvoice() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();


        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onConfigLoading);
        modEventBus.addListener(this::onConfigReloading);


        MinecraftForge.EVENT_BUS.register(this);


        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, org.faded.worldvoice.config.WVConfig.SPEC);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, org.faded.worldvoice.config.WVServerConfig.SERVER_SPEC);


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

        LOGGER.info("HELLO FROM COMMON SETUP");
        event.enqueueWork(org.faded.worldvoice.world.WorldVoiceGameRules::register);


        event.enqueueWork(org.faded.worldvoice.network.WorldVoicePacketHandler::register);


        event.enqueueWork(() -> {
            QuestObjectiveType.init();
            QuestRewardType.init();
            org.faded.worldvoice.quest.requirement.QuestRequirementType.init();
        });
    }


    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

        LOGGER.info("HELLO from server starting");
        org.faded.worldvoice.core.SkillConfigManager.INSTANCE.reload();
        org.faded.worldvoice.core.SkillConfigManager.INSTANCE.applyToLedger(event.getServer().overworld());


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


    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {

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
