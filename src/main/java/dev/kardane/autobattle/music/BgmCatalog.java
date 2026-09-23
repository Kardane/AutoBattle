package dev.kardane.autobattle.music;

import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public record BgmCatalog(
    Map<String, Track> tracks,
    Map<String, Playlist> playlists
) {
    public record Track(
        String id,
        String path,
        String name,
        double seconds
    ) {
    }

    public record Playlist(List<String> tracks, boolean repeat) {
        public Playlist {
            tracks = List.copyOf(tracks);
            if (tracks.isEmpty()) {
                throw new IllegalArgumentException(
                    "BGM playlist cannot be empty"
                );
            }
        }
    }

    public record Scan(
        BgmCatalog catalog,
        Map<String, byte[]> assets
    ) {
    }

    public BgmCatalog {
        tracks = Collections.unmodifiableMap(
            new LinkedHashMap<>(tracks)
        );
        playlists = Collections.unmodifiableMap(
            new LinkedHashMap<>(playlists)
        );
    }

    public static BgmCatalog empty() {
        return new BgmCatalog(Map.of(), Map.of());
    }

    public static Scan scan(
        Path directory,
        Consumer<String> warning
    ) throws IOException {
        Files.createDirectories(directory);

        var tracks = new LinkedHashMap<String, Track>();
        var assets = new LinkedHashMap<String, byte[]>();
        var byPath = new HashMap<String, String>();

        try (var paths = Files.walk(directory)) {
            List<Path> audioFiles = paths
                .filter(path -> Files.isRegularFile(
                    path,
                    LinkOption.NOFOLLOW_LINKS
                ))
                .filter(path -> path.toString()
                    .toLowerCase(Locale.ROOT)
                    .endsWith(".ogg"))
                .sorted(Comparator.comparing(
                    path -> directory.relativize(path).toString()
                ))
                .toList();

            for (Path path : audioFiles) {
                String relative = directory.relativize(path)
                    .toString()
                    .replace('\\', '/');

                try {
                    byte[] bytes = Files.readAllBytes(path);
                    double seconds = OggVorbis.duration(
                        new ByteArrayInputStream(bytes)
                    );
                    String id = "track_" + hash(
                        relative.getBytes(StandardCharsets.UTF_8)
                    );
                    String filename = path.getFileName().toString();
                    String name = filename.substring(
                        0,
                        filename.length() - 4
                    );
                    var track = new Track(
                        id,
                        relative,
                        name,
                        seconds
                    );
                    tracks.put(id, track);
                    byPath.put(relative, id);
                    assets.put(
                        "assets/autobattle/sounds/music/"
                            + id
                            + ".ogg",
                        bytes
                    );
                } catch (IOException | IllegalArgumentException error) {
                    warning.accept(relative + ": " + error.getMessage());
                }
            }
        }

        var playlists = new LinkedHashMap<String, Playlist>();

        String lobbyTrack = byPath.get("lobby.ogg");
        if (lobbyTrack != null) {
            playlists.put(
                "lobby",
                new Playlist(List.of(lobbyTrack), true)
            );
        }

        var matchTracks = new ArrayList<String>();
        for (int index = 1; index <= 5; index++) {
            String track = byPath.get("bgm" + index + ".ogg");
            if (track != null) {
                matchTracks.add(track);
            }
        }
        if (!matchTracks.isEmpty()) {
            playlists.put(
                "match",
                new Playlist(matchTracks, true)
            );
        }

        if (!tracks.isEmpty()) {
            playlists.put(
                "all",
                new Playlist(List.copyOf(tracks.keySet()), true)
            );
        }

        Path playlistPath = directory.resolve("playlists.json");
        if (Files.exists(playlistPath)) {
            try {
                var object = JsonParser.parseString(
                    Files.readString(playlistPath)
                ).getAsJsonObject();
                for (var entry : object.entrySet()) {
                    try {
                        String id = playlistId(entry.getKey());
                        if ("all".equals(id)) {
                            throw new IllegalArgumentException(
                                "all is generated automatically"
                            );
                        }

                        var value = entry.getValue().getAsJsonObject();
                        var ids = new ArrayList<String>();
                        for (var item : value.getAsJsonArray("tracks")) {
                            String reference = item.getAsString();
                            String resolved = byPath.getOrDefault(
                                reference,
                                reference
                            );
                            if (!tracks.containsKey(resolved)) {
                                throw new IllegalArgumentException(
                                    "Unknown track: " + reference
                                );
                            }
                            ids.add(resolved);
                        }
                        playlists.put(
                            id,
                            new Playlist(
                                ids,
                                value.has("repeat")
                                    && value.get("repeat")
                                        .getAsBoolean()
                            )
                        );
                    } catch (RuntimeException error) {
                        warning.accept(
                            "playlists.json/"
                                + entry.getKey()
                                + ": "
                                + error.getMessage()
                        );
                    }
                }
            } catch (RuntimeException error) {
                warning.accept(
                    "playlists.json: " + error.getMessage()
                );
            }
        }

        var sounds = new com.google.gson.JsonObject();
        for (Track track : tracks.values()) {
            var sound = new com.google.gson.JsonObject();
            sound.addProperty(
                "name",
                "autobattle:music/" + track.id()
            );
            sound.addProperty("stream", true);
            sound.addProperty("attenuation_distance", 0);
            var values = new com.google.gson.JsonArray();
            values.add(sound);
            var event = new com.google.gson.JsonObject();
            event.add("sounds", values);
            sounds.add("music/" + track.id(), event);
        }
        assets.put(
            "assets/autobattle/sounds.json",
            sounds.toString().getBytes(StandardCharsets.UTF_8)
        );

        return new Scan(
            new BgmCatalog(tracks, playlists),
            assets
        );
    }

    private static String playlistId(String value) {
        if (!value.matches("[a-z0-9][a-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException(
                "playlist id must use lowercase letters, digits, _ or -"
            );
        }
        return value;
    }

    private static String hash(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }
}
