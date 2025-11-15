package org.faded.worldvoice.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.faded.worldvoice.service.SkillService;
import com.github.manasmods.tensura.event.SkillPlunderEvent;
import org.faded.worldvoice.core.SkillCategory;
import org.faded.worldvoice.core.SkillConfigManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.faded.worldvoice.service.LearnSourceTracker;

public class CommonEvents {
    private final SkillService skills = new SkillService();

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity().level instanceof ServerLevel level)) return;
        var player = e.getEntity();
        skills.updateLastSeen(level, player.getUUID(), player.getGameProfile().getName(), System.currentTimeMillis());
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public void onSkillPlunder(SkillPlunderEvent e) {
        if (!(e.getPlunderer() instanceof ServerPlayer sp)) return;
        var skill = e.getSkill();
        if (skill == null) return;
        ResourceLocation id = skill.getRegistryName();
        SkillCategory cat = id != null ? SkillConfigManager.INSTANCE.classify(id) : null;

        if (SkillConfigManager.INSTANCE.isPlunderRestricted(cat, id)) {
            e.setCanceled(true);
            e.setSkill(null);
            return;
        }

        LearnSourceTracker.recordPlunder(sp.getUUID());
    }
}
