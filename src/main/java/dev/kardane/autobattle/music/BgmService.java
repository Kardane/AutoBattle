package dev.kardane.autobattle.music;

import dev.kardane.autobattle.AutoBattleMod;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Server-side BGM playback. It sends ordinary Minecraft sound packets after
 * the listener has acknowledged the current Polymer resource pack.
 */
public final class BgmService implements AutoCloseable {
    private final MinecraftServer server;
    private final Map<UUID, ServerPlayer> online = new LinkedHashMap<>();
    private final Map<UUID, Channel> owners = new HashMap<>();
    private final Set<Channel> channels = new LinkedHashSet<>();
    private final Set<UUID> muted = new HashSet<>();
    private final ScheduledExecutorService clock =
        Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "autobattle-bgm");
            thread.setDaemon(true);
            return thread;
        });
    private final boolean autoStart;
    private Channel global;
    private boolean closed;
    private boolean autoStarted;

    public BgmService(
        MinecraftServer server,
        boolean autoStart
    ) {
        this.server = server;
        this.autoStart = autoStart;
        clock.scheduleAtFixedRate(
            this::tick,
            0L,
            10L,
            TimeUnit.MILLISECONDS
        );
    }

    public BgmCatalog catalog() {
        return BgmPackManager.published().catalog();
    }

    public synchronized void joined(ServerPlayer player) {
        online.put(player.getUUID(), player);
        if (global != null) {
            global.attachReadyListeners();
        }
    }

    public synchronized void left(ServerPlayer player) {
        stop(List.of(player));
        online.remove(player.getUUID());
        muted.remove(player.getUUID());
        BgmPackManager.forget(player.getUUID());
    }

    public synchronized void interrupted(ServerPlayer player) {
        Channel channel = owners.remove(player.getUUID());
        if (channel != null) {
            channel.silence(player);
        }
    }

    public synchronized BgmPlayback play(
        String id,
        Collection<ServerPlayer> players
    ) {
        ensureOpen();
        BgmCatalog source = catalog();
        BgmCatalog.Playlist playlist = resolve(source, id);
        var targets = List.copyOf(
            new LinkedHashSet<>(players)
        );
        if (targets.isEmpty()) {
            throw new IllegalArgumentException(
                "No BGM listeners"
            );
        }
        for (ServerPlayer player : targets) {
            if (!online.containsKey(player.getUUID())) {
                throw new IllegalArgumentException(
                    "BGM listener is not connected"
                );
            }
        }

        stop(targets);
        targets.forEach(player -> muted.remove(player.getUUID()));
        var channel = new Channel(
            id,
            source,
            playlist,
            targets,
            false
        );
        channels.add(channel);
        channel.next();
        return channel.handle;
    }

    public synchronized BgmPlayback playGlobal(String id) {
        ensureOpen();
        BgmCatalog source = catalog();
        BgmCatalog.Playlist playlist = resolve(source, id);
        if (global != null) {
            global.finish(BgmPlayback.Status.REPLACED);
        }
        muted.clear();

        global = new Channel(
            id,
            source,
            playlist,
            List.of(),
            true
        );
        channels.add(global);
        global.next();
        return global.handle;
    }

    public synchronized void ensureGlobal(String id) {
        ensureOpen();
        if (global != null
            && !global.handle.done()
            && global.id.equals(id)) {
            autoStarted = true;
            muted.clear();
            global.attachReadyListeners();
            return;
        }
        playGlobal(id);
        autoStarted = true;
    }

    public synchronized void stop(
        Collection<ServerPlayer> players
    ) {
        for (ServerPlayer player : players) {
            muted.add(player.getUUID());
            interrupted(player);
            for (Channel channel : List.copyOf(channels)) {
                if (!channel.broadcast
                    && channel.targets.remove(player)
                    && channel.targets.isEmpty()) {
                    channel.finish(BgmPlayback.Status.CANCELLED);
                }
            }
        }
    }

    public synchronized void stopGlobal() {
        if (global != null) {
            global.finish(BgmPlayback.Status.CANCELLED);
        }
    }

    public synchronized void nextGlobal() {
        if (global != null) {
            global.next();
        }
    }

    public synchronized void next(
        Collection<ServerPlayer> players
    ) {
        var selected = new HashSet<Channel>();
        for (ServerPlayer player : players) {
            muted.remove(player.getUUID());
            Channel channel = owners.get(player.getUUID());
            if (channel != null) {
                selected.add(channel);
            }
        }
        selected.forEach(Channel::next);
    }

    private synchronized void tick() {
        if (closed) {
            return;
        }

        try {
            if (autoStart
                && !autoStarted
                && global == null
                && catalog().playlists().containsKey("lobby")
                && online.values().stream().anyMatch(player ->
                    BgmPackManager.ready(player.getUUID())
                )) {
                autoStarted = true;
                playGlobal("lobby");
            }

            long now = System.nanoTime();
            for (Channel channel : List.copyOf(channels)) {
                channel.attachReadyListeners();
                if (channel.timeline.elapsed(now)) {
                    channel.next();
                }
            }
        } catch (RuntimeException error) {
            AutoBattleMod.LOGGER.error(
                "BGM playback failed",
                error
            );
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        clock.shutdownNow();
        for (Channel channel : List.copyOf(channels)) {
            channel.finish(BgmPlayback.Status.CANCELLED);
        }
        online.clear();
        muted.clear();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException(
                "BGM service is closed"
            );
        }
    }

    private BgmCatalog.Playlist resolve(
        BgmCatalog source,
        String id
    ) {
        BgmCatalog.Playlist playlist = source.playlists().get(id);
        if (playlist != null) {
            return playlist;
        }

        BgmCatalog.Track track = source.tracks().get(id);
        if (track != null) {
            return new BgmCatalog.Playlist(
                List.of(track.id()),
                false
            );
        }

        throw new IllegalArgumentException(
            "Unknown BGM track or playlist: " + id
        );
    }

    private final class Channel {
        private final String id;
        private BgmCatalog source;
        private final Set<ServerPlayer> targets;
        private final boolean broadcast;
        private final Set<ServerPlayer> sounding = new HashSet<>();
        private final BgmPlayback handle;
        private BgmTimeline timeline;
        private ResourceLocation sound;

        private Channel(
            String id,
            BgmCatalog source,
            BgmCatalog.Playlist playlist,
            Collection<ServerPlayer> targets,
            boolean broadcast
        ) {
            this.id = id;
            this.source = source;
            this.targets = new LinkedHashSet<>(targets);
            this.broadcast = broadcast;
            this.timeline = new BgmTimeline(playlist);
            this.handle = new BgmPlayback(() ->
                finish(BgmPlayback.Status.CANCELLED)
            );
        }

        private void next() {
            if (handle.done()) {
                return;
            }

            for (ServerPlayer player : List.copyOf(sounding)) {
                owners.remove(player.getUUID(), this);
                silence(player);
            }

            BgmCatalog current = BgmService.this.catalog();
            if (source != current) {
                source = current;
                BgmCatalog.Playlist playlist = resolve(source, id);
                timeline = new BgmTimeline(playlist);
            }

            String next = timeline.next();
            if (next == null) {
                finish(BgmPlayback.Status.COMPLETED);
                return;
            }

            BgmCatalog.Track track = source.tracks().get(next);
            if (track == null) {
                finish(BgmPlayback.Status.FAILED);
                return;
            }

            sound = ResourceLocation.fromNamespaceAndPath(
                AutoBattleMod.MOD_ID,
                "music/" + track.id()
            );
            timeline.started(track.seconds(), System.nanoTime());

            attachReadyListeners();
        }

        private void attachReadyListeners() {
            if (sound == null) {
                return;
            }

            Collection<ServerPlayer> listeners = broadcast
                ? List.copyOf(online.values())
                : List.copyOf(targets);
            for (ServerPlayer player : listeners) {
                UUID playerUuid = player.getUUID();
                if (muted.contains(playerUuid)
                    || sounding.contains(player)
                    || !online.containsKey(playerUuid)
                    || !BgmPackManager.ready(playerUuid)) {
                    continue;
                }

                Channel previous = owners.get(playerUuid);
                if (broadcast
                    && previous != null
                    && previous != this) {
                    continue;
                }
                if (previous != null) {
                    previous.silence(player);
                }

                owners.put(playerUuid, this);
                sounding.add(player);
                player.connection.send(
                    new ClientboundSoundEntityPacket(
                        Holder.direct(
                            SoundEvent.createVariableRangeEvent(sound)
                        ),
                        SoundSource.VOICE,
                        player,
                        1.0F,
                        1.0F,
                        ThreadLocalRandom.current().nextLong()
                    )
                );
            }
        }

        private void silence(ServerPlayer player) {
            if (sounding.remove(player) && sound != null) {
                player.connection.send(
                    new ClientboundStopSoundPacket(
                        sound,
                        SoundSource.VOICE
                    )
                );
            }
        }

        private void finish(BgmPlayback.Status status) {
            for (ServerPlayer player : List.copyOf(sounding)) {
                owners.remove(player.getUUID(), this);
                silence(player);
            }
            channels.remove(this);
            if (global == this) {
                global = null;
            }
            handle.finish(status, "");
        }
    }
}
