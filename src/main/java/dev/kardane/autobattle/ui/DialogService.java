package dev.kardane.autobattle.ui;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;

public final class DialogService {
    public boolean openDoctrineSetup(ServerPlayer player) {
        return open(player, "doctrine_setup");
    }

    public boolean openDoctrineReview(ServerPlayer player) {
        return open(player, "doctrine_review");
    }

    public boolean openDoctrineEdit(
        ServerPlayer player,
        int zeroBasedIndex
    ) {
        if (zeroBasedIndex < 0 || zeroBasedIndex > 2) {
            throw new IllegalArgumentException(
                "Doctrine index must be in range 0..2"
            );
        }

        return open(
            player,
            "doctrine_edit_" + (zeroBasedIndex + 1)
        );
    }

    private boolean open(
        ServerPlayer player,
        String path
    ) {
        Registry<Dialog> dialogs = player.level()
            .registryAccess()
            .lookupOrThrow(Registries.DIALOG);

        ResourceLocation id =
            ResourceLocation.fromNamespaceAndPath(
                "autobattle",
                path
            );

        return dialogs.get(id).map(holder -> {
            player.openDialog(holder);
            return true;
        }).orElse(false);
    }
}
