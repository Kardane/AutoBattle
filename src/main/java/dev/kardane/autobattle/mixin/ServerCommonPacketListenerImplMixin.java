package dev.kardane.autobattle.mixin;

import dev.kardane.autobattle.AutoBattleMod;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
    @Inject(
        method = "handleCustomClickAction",
        at = @At("HEAD")
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
