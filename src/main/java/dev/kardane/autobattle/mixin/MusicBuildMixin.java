package dev.kardane.autobattle.mixin;

import dev.kardane.autobattle.music.BgmPackManager;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.function.Consumer;

@Mixin(value = PolymerResourcePackUtils.class, remap = false)
public abstract class MusicBuildMixin {
    @Inject(
        method = "buildMain(Ljava/nio/file/Path;Ljava/util/function/Consumer;)Z",
        at = @At("HEAD")
    )
    private static void autobattle$beginBgmBuild(
        Path output,
        Consumer<String> status,
        CallbackInfoReturnable<Boolean> callback
    ) {
        BgmPackManager.begin();
    }

    @Inject(
        method = "buildMain(Ljava/nio/file/Path;Ljava/util/function/Consumer;)Z",
        at = @At("RETURN")
    )
    private static void autobattle$finishBgmBuild(
        Path output,
        Consumer<String> status,
        CallbackInfoReturnable<Boolean> callback
    ) {
        BgmPackManager.finish(output, callback.getReturnValueZ());
    }
}
