package dev.kardane.autobattle.mixin;

import com.mojang.authlib.GameProfile;
import dev.kardane.autobattle.AutoBattleMod;
import dev.kardane.autobattle.doctrine.DoctrineEditResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
    private static final ResourceLocation DOCTRINE_SUBMIT =
        ResourceLocation.fromNamespaceAndPath(
            AutoBattleMod.MOD_ID,
            "doctrine_submit"
        );

    private static final ResourceLocation DOCTRINE_REPLACE_1 =
        ResourceLocation.fromNamespaceAndPath(
            AutoBattleMod.MOD_ID,
            "doctrine_replace_1"
        );

    private static final ResourceLocation DOCTRINE_REPLACE_2 =
        ResourceLocation.fromNamespaceAndPath(
            AutoBattleMod.MOD_ID,
            "doctrine_replace_2"
        );

    private static final ResourceLocation DOCTRINE_REPLACE_3 =
        ResourceLocation.fromNamespaceAndPath(
            AutoBattleMod.MOD_ID,
            "doctrine_replace_3"
        );

    @Shadow
    @Final
    protected MinecraftServer server;

    @Shadow
    protected abstract GameProfile playerProfile();

    @Inject(
        method = "handleCustomClickAction",
        at = @At("HEAD"),
        cancellable = true
    )
    private void autobattle$handleCustomClickAction(
        ServerboundCustomClickActionPacket packet,
        CallbackInfo ci
    ) {
        int replaceIndex = replacementIndex(packet.id());

        if (!DOCTRINE_SUBMIT.equals(packet.id())
            && replaceIndex < 0) {
            return;
        }

        ci.cancel();

        ServerPlayer player = server.getPlayerList()
            .getPlayer(playerProfile().getId());

        if (player == null) {
            return;
        }

        CompoundTag payload = packet.payload()
            .filter(CompoundTag.class::isInstance)
            .map(CompoundTag.class::cast)
            .orElse(null);

        if (payload == null) {
            player.sendSystemMessage(
                Component.literal(
                    "[AutoBattle] Invalid Doctrine dialog payload."
                )
            );
            return;
        }

        if (DOCTRINE_SUBMIT.equals(packet.id())) {
            handleInitialDoctrine(player, payload);
            return;
        }

        handleDoctrineReplacement(
            player,
            payload,
            replaceIndex
        );
    }

    private void handleInitialDoctrine(
        ServerPlayer player,
        CompoundTag payload
    ) {
        DoctrineEditResult result =
            AutoBattleMod.doctrineService().submitInitial(
                AutoBattleMod.matchManager().session(),
                player,
                payload.getStringOr("d1", ""),
                payload.getStringOr("d2", ""),
                payload.getStringOr("d3", "")
            );

        sendResult(
            player,
            result,
            "Doctrine saved."
        );

        if (result.success()) {
            AutoBattleMod.matchManager()
                .beginCountdownIfDoctrinesReady();
        }
    }

    private void handleDoctrineReplacement(
        ServerPlayer player,
        CompoundTag payload,
        int index
    ) {
        DoctrineEditResult result =
            AutoBattleMod.doctrineService().replaceLine(
                AutoBattleMod.matchManager().session(),
                player,
                index,
                payload.getStringOr("line", "")
            );

        sendResult(
            player,
            result,
            "Doctrine line " + (index + 1) + " updated."
        );
    }

    private void sendResult(
        ServerPlayer player,
        DoctrineEditResult result,
        String successMessage
    ) {
        if (result.success()) {
            player.sendSystemMessage(
                Component.literal(
                    "[AutoBattle] "
                        + successMessage
                        + " Version "
                        + result.doctrine().version()
                        + "."
                )
            );
            return;
        }

        player.sendSystemMessage(
            Component.literal(
                "[AutoBattle] Doctrine rejected: "
                    + result.error().name()
            )
        );
    }

    private int replacementIndex(ResourceLocation id) {
        if (DOCTRINE_REPLACE_1.equals(id)) {
            return 0;
        }

        if (DOCTRINE_REPLACE_2.equals(id)) {
            return 1;
        }

        if (DOCTRINE_REPLACE_3.equals(id)) {
            return 2;
        }

        return -1;
    }
}
