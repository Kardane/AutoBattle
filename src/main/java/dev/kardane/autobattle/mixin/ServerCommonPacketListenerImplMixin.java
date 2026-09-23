package dev.kardane.autobattle.mixin;

import dev.kardane.autobattle.AutoBattleMod;
import dev.kardane.autobattle.music.BgmPackManager;
import dev.kardane.autobattle.music.BgmRuntime;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
    @Inject(
        method = "onDisconnect",
        at = @At("HEAD")
    )
    private void autobattle$forgetBgm(CallbackInfo ci) {
        BgmPackManager.forget(
            ((ServerCommonPacketListenerImpl) (Object) this)
                .getOwner()
                .getId()
        );
    }

    @Inject(
        method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void autobattle$routeBgmPack(
        Packet<?> packet,
        ChannelFutureListener listener,
        CallbackInfo ci
    ) {
        var common = (ServerCommonPacketListenerImpl) (Object) this;
        if (packet instanceof ClientboundResourcePackPushPacket push) {
            var mapped = BgmPackManager.sent(common, push);
            if (mapped != push) {
                common.send(mapped, listener);
                ci.cancel();
                return;
            }
        }

        if (packet instanceof ClientboundResourcePackPopPacket pop) {
            var mapped = BgmPackManager.mapPop(common, pop);
            if (mapped != pop) {
                common.send(mapped, listener);
                ci.cancel();
                return;
            }
            BgmPackManager.popped(common, pop.id());
        }

        if ((Object) this instanceof ServerGamePacketListenerImpl game
            && packet instanceof net.minecraft.network.protocol.game
                .ClientboundRespawnPacket) {
            BgmRuntime.serviceIfPresent(
                game.player.getServer()
            ).ifPresent(service -> service.interrupted(game.player));
        }
    }

    @ModifyVariable(
        method = "handleResourcePackResponse",
        at = @At("HEAD"),
        argsOnly = true,
        order = 900
    )
    private ServerboundResourcePackPacket autobattle$mapBgmPackResponse(
        ServerboundResourcePackPacket packet
    ) {
        return BgmPackManager.response(
            (ServerCommonPacketListenerImpl) (Object) this,
            packet
        );
    }

    @Inject(
        method = "handleCustomClickAction",
        // The vanilla handler performs its server-thread handoff first.
        // Injecting at HEAD handles the same packet once on the network
        // thread and again on the server thread.
        at = @At("TAIL")
    )
    private void autobattle$handleCustomClickAction(
        ServerboundCustomClickActionPacket packet,
        CallbackInfo ci
    ) {
        if (!AutoBattleMod.MOD_ID.equals(
            packet.id().getNamespace()
        )) {
            return;
        }

        if ((Object) this
            instanceof ServerGamePacketListenerImpl game) {
            AutoBattleMod.dialogActionRouter().handle(
                game.player,
                packet.id(),
                packet.payload()
            );
        }
    }
}
