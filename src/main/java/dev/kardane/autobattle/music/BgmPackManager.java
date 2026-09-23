package dev.kardane.autobattle.music;

import dev.kardane.autobattle.AutoBattleMod;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adds OGG assets to Polymer's main pack and binds readiness to that pack.
 * Polymer reuses one UUID, so the wire UUID is replaced for every send.
 */
public final class BgmPackManager {
    public record Published(BgmCatalog catalog, String hash) {
    }

    private record PackState(UUID id, String hash, boolean loaded) {
        PackState acknowledged(
            UUID responseId,
            boolean successfullyLoaded
        ) {
            return id.equals(responseId)
                ? new PackState(id, hash, successfullyLoaded)
                : this;
        }

        boolean ready(String activeHash) {
            return loaded
                && !activeHash.isEmpty()
                && hash.equalsIgnoreCase(activeHash);
        }
    }

    private static final ThreadLocal<BgmCatalog> BUILD =
        new ThreadLocal<>();
    private static final Map<UUID, PackState> LISTENERS =
        new ConcurrentHashMap<>();
    private static volatile Published published = new Published(
        BgmCatalog.empty(),
        ""
    );
    private static volatile boolean initialized;

    private BgmPackManager() {
    }

    public static synchronized void init(Path directory) {
        if (initialized) {
            return;
        }
        initialized = true;

        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(
            builder -> {
                try {
                    var scan = BgmCatalog.scan(
                        directory,
                        warning -> AutoBattleMod.LOGGER.warn(
                            "BGM asset ignored: {}",
                            warning
                        )
                    );
                    for (var asset : scan.assets().entrySet()) {
                        if (!builder.addData(
                            asset.getKey(),
                            asset.getValue()
                        )) {
                            throw new IllegalStateException(
                                "Cannot add " + asset.getKey()
                            );
                        }
                    }
                    BUILD.set(scan.catalog());
                } catch (IOException error) {
                    throw new IllegalStateException(
                        "Cannot scan AutoBattle BGM directory",
                        error
                    );
                }
            }
        );
    }

    public static Published published() {
        return published;
    }

    public static boolean ready(UUID playerUuid) {
        PackState state = LISTENERS.get(playerUuid);
        return state != null && state.ready(published.hash());
    }

    public static void begin() {
        BUILD.remove();
    }

    public static void finish(Path output, boolean success) {
        BgmCatalog candidate = BUILD.get();
        BUILD.remove();
        if (!success || candidate == null) {
            return;
        }

        try (var input = Files.newInputStream(output)) {
            var digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[65536];
            int size;
            while ((size = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, size);
            }
            published = new Published(
                candidate,
                HexFormat.of().formatHex(digest.digest())
            );
            AutoBattleMod.LOGGER.info(
                "Published {} BGM track(s) in the Polymer resource pack",
                candidate.tracks().size()
            );
        } catch (Exception error) {
            AutoBattleMod.LOGGER.error(
                "Cannot verify generated BGM resource pack",
                error
            );
        }
    }

    public static ClientboundResourcePackPushPacket sent(
        ServerCommonPacketListenerImpl handler,
        ClientboundResourcePackPushPacket packet
    ) {
        if (!packet.id().equals(
            PolymerResourcePackUtils.getMainUuid()
        )) {
            return packet;
        }

        UUID playerUuid = handler.getOwner().getId();
        PackState previous = LISTENERS.get(playerUuid);
        if (previous != null) {
            handler.send(
                new ClientboundResourcePackPopPacket(
                    Optional.of(previous.id())
                )
            );
        }

        var sent = new ClientboundResourcePackPushPacket(
            UUID.randomUUID(),
            packet.url(),
            packet.hash(),
            packet.required(),
            packet.prompt()
        );
        LISTENERS.put(
            playerUuid,
            new PackState(sent.id(), sent.hash(), false)
        );

        if (handler instanceof ServerGamePacketListenerImpl game) {
            interrupt(game);
        }
        return sent;
    }

    public static ServerboundResourcePackPacket response(
        ServerCommonPacketListenerImpl handler,
        ServerboundResourcePackPacket packet
    ) {
        UUID playerUuid = handler.getOwner().getId();
        PackState state = LISTENERS.get(playerUuid);
        if (state == null || !state.id().equals(packet.id())) {
            return packet;
        }

        LISTENERS.replace(
            playerUuid,
            state,
            state.acknowledged(
                packet.id(),
                packet.action()
                    == ServerboundResourcePackPacket.Action
                        .SUCCESSFULLY_LOADED
            )
        );

        return new ServerboundResourcePackPacket(
            PolymerResourcePackUtils.getMainUuid(),
            packet.action()
        );
    }

    public static ClientboundResourcePackPopPacket mapPop(
        ServerCommonPacketListenerImpl handler,
        ClientboundResourcePackPopPacket packet
    ) {
        PackState state = LISTENERS.get(handler.getOwner().getId());
        if (state != null
            && packet.id().filter(
                PolymerResourcePackUtils.getMainUuid()::equals
            ).isPresent()) {
            return new ClientboundResourcePackPopPacket(
                Optional.of(state.id())
            );
        }
        return packet;
    }

    public static void popped(
        ServerCommonPacketListenerImpl handler,
        Optional<UUID> id
    ) {
        UUID playerUuid = handler.getOwner().getId();
        PackState state = LISTENERS.get(playerUuid);
        if (state != null
            && (id.isEmpty() || state.id().equals(id.get()))) {
            LISTENERS.remove(playerUuid);
            if (handler instanceof ServerGamePacketListenerImpl game) {
                interrupt(game);
            }
        }
    }

    public static void forget(UUID playerUuid) {
        LISTENERS.remove(playerUuid);
    }

    private static void interrupt(
        ServerGamePacketListenerImpl handler
    ) {
        if (handler.player.getServer() != null) {
            BgmRuntime.serviceIfPresent(
                handler.player.getServer()
            ).ifPresent(service -> service.interrupted(handler.player));
        }
    }
}
