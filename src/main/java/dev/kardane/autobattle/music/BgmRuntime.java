package dev.kardane.autobattle.music;

import com.google.gson.JsonParser;
import dev.kardane.autobattle.AutoBattleMod;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

public final class BgmRuntime {
    public static final Path CONFIG_ROOT = FabricLoader.getInstance()
        .getConfigDir()
        .resolve(AutoBattleMod.MOD_ID);
    public static final Path MUSIC_ROOT = CONFIG_ROOT.resolve("music");
    private static final Path SETTINGS = CONFIG_ROOT.resolve("music.json");
    private static final List<String> DEFAULT_TRACKS = List.of(
        "lobby.ogg",
        "bgm1.ogg",
        "bgm2.ogg",
        "bgm3.ogg",
        "bgm4.ogg",
        "bgm5.ogg"
    );
    private static final String BUNDLED_MUSIC_ROOT =
        "/autobattle/default_music/";
    private static final Map<MinecraftServer, BgmService> SERVICES =
        Collections.synchronizedMap(new IdentityHashMap<>());
    private static boolean registered;

    private BgmRuntime() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        seedBundledMusic();
        BgmPackManager.init(MUSIC_ROOT);
        BgmCommands.register();

        ServerLifecycleEvents.SERVER_STARTING.register(server ->
            SERVICES.put(
                server,
                new BgmService(server, loadAutoStart())
            )
        );
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
            ensureGlobal(server, "lobby")
        );
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            BgmService service = SERVICES.get(server);
            if (service != null) {
                service.close();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(
            SERVICES::remove
        );

        ServerPlayConnectionEvents.JOIN.register(
            (handler, sender, server) -> service(server)
                .joined(handler.player)
        );
        ServerPlayConnectionEvents.DISCONNECT.register(
            (handler, server) -> service(server)
                .left(handler.player)
        );
        ServerPlayerEvents.AFTER_RESPAWN.register(
            (oldPlayer, newPlayer, alive) -> service(
                newPlayer.getServer()
            ).joined(newPlayer)
        );
    }

    public static BgmService service(MinecraftServer server) {
        synchronized (SERVICES) {
            return SERVICES.computeIfAbsent(
                server,
                value -> new BgmService(value, loadAutoStart())
            );
        }
    }

    public static Optional<BgmService> serviceIfPresent(
        MinecraftServer server
    ) {
        return Optional.ofNullable(SERVICES.get(server));
    }

    public static void ensureGlobal(
        MinecraftServer server,
        String playlist
    ) {
        try {
            service(server).ensureGlobal(playlist);
        } catch (RuntimeException error) {
            AutoBattleMod.LOGGER.warn(
                "Cannot start BGM playlist {}: {}",
                playlist,
                error.getMessage()
            );
        }
    }

    private static void seedBundledMusic() {
        try {
            Files.createDirectories(MUSIC_ROOT);
            int copied = 0;

            for (String name : DEFAULT_TRACKS) {
                Path target = MUSIC_ROOT.resolve(name);
                if (Files.exists(target)) {
                    continue;
                }

                try (InputStream input = bundledMusic(name)) {
                    if (input == null) {
                        AutoBattleMod.LOGGER.warn(
                            "Bundled BGM asset is missing: {}",
                            name
                        );
                        continue;
                    }
                    Files.copy(input, target);
                    copied++;
                }
            }

            if (copied > 0) {
                AutoBattleMod.LOGGER.info(
                    "Seeded {} bundled BGM track(s) into {}",
                    copied,
                    MUSIC_ROOT
                );
            }
        } catch (IOException error) {
            AutoBattleMod.LOGGER.warn(
                "Cannot seed bundled BGM tracks",
                error
            );
        }
    }

    private static InputStream bundledMusic(String name)
        throws IOException {
        InputStream bundled = BgmRuntime.class.getResourceAsStream(
            BUNDLED_MUSIC_ROOT + name
        );
        if (bundled != null) {
            return bundled;
        }

        Path[] developmentPaths = {
            Path.of("docs", name),
            Path.of("..", "docs", name)
        };
        for (Path path : developmentPaths) {
            if (Files.isRegularFile(path)) {
                return Files.newInputStream(path);
            }
        }
        return null;
    }

    private static boolean loadAutoStart() {
        try {
            Files.createDirectories(CONFIG_ROOT);
            if (!Files.exists(SETTINGS)) {
                Files.writeString(
                    SETTINGS,
                    "{\n  \"autoStart\": true\n}\n"
                );
                return true;
            }

            var json = JsonParser.parseString(
                Files.readString(SETTINGS)
            ).getAsJsonObject();
            return json.has("autoStart")
                && json.get("autoStart").getAsBoolean();
        } catch (IOException | RuntimeException error) {
            AutoBattleMod.LOGGER.warn(
                "Cannot load BGM settings; autoStart is disabled",
                error
            );
            return false;
        }
    }
}
