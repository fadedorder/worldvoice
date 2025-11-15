package org.faded.worldvoice.mixin;

import com.github.manasmods.tensura.capability.race.ITensuraPlayerCapability;
import com.github.manasmods.tensura.network.play2server.RequestAwakeningPacket;
import net.minecraft.server.level.ServerPlayer;
import org.faded.worldvoice.ai.YggdrasilHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RequestAwakeningPacket.class, remap = false)
public class RequestAwakeningPacketMixin {

    @Inject(method = "lambda$handle$0", at = @At("RETURN"))
    private void worldvoice$afterAwakening(ServerPlayer player,
                                           ITensuraPlayerCapability capability,
                                           CallbackInfo ci) {
        if (player == null || capability == null) {
            return;
        }
        boolean hero = capability.isTrueHero();
        boolean demon = capability.isTrueDemonLord();
        if (!hero && !demon) {
            return;
        }
        YggdrasilHooks.instance().ifPresent(service -> service.signalAwakening(player, hero));
    }
}
