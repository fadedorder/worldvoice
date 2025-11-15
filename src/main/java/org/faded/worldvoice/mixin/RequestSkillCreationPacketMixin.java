package org.faded.worldvoice.mixin;

import com.github.manasmods.tensura.network.play2server.skill.RequestSkillCreationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.faded.worldvoice.service.LearnSourceTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(value = RequestSkillCreationPacket.class, remap = false)
public class RequestSkillCreationPacketMixin {

    @Inject(method = "handle", at = @At("HEAD"))
    private void worldvoice$handleHead(Supplier<NetworkEvent.Context> ctxSupplier, CallbackInfo ci) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        if (ctx == null) return;
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            LearnSourceTracker.recordCreation(player.getUUID());
        }
    }
}

