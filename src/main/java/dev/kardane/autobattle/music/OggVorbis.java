package dev.kardane.autobattle.music;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Reads Ogg Vorbis framing without FFmpeg or a native audio decoder. */
final class OggVorbis {
    private OggVorbis() {
    }

    static double duration(Path path) throws IOException {
        try (var input = new BufferedInputStream(
            Files.newInputStream(path)
        )) {
            return duration(input);
        }
    }

    static double duration(InputStream input) throws IOException {
        int serial = 0;
        int sequence = 0;
        int packets = 0;
        int rate = 0;
        boolean first = true;
        boolean ended = false;
        boolean continued = false;
        long granule = -1L;
        var header = new ByteArrayOutputStream();

        while (true) {
            byte[] prefix = input.readNBytes(27);
            if (prefix.length == 0) {
                break;
            }

            if (ended
                || prefix.length != 27
                || !"OggS".equals(
                    new String(
                        prefix,
                        0,
                        4,
                        StandardCharsets.US_ASCII
                    )
                )
                || prefix[4] != 0) {
                throw new IOException(
                    "Invalid or truncated Ogg page"
                );
            }

            var buffer = ByteBuffer.wrap(prefix)
                .order(ByteOrder.LITTLE_ENDIAN);
            int flags = prefix[5] & 255;
            int pageSerial = buffer.getInt(14);
            int pageSequence = buffer.getInt(18);
            int checksum = buffer.getInt(22);

            if ((flags & ~7) != 0
                || ((flags & 1) != 0) != continued) {
                throw new IOException("Invalid Ogg continuation");
            }

            if (first) {
                serial = pageSerial;
                if ((flags & 2) == 0 || pageSequence != 0) {
                    throw new IOException("Missing Ogg stream start");
                }
            } else if ((flags & 2) != 0) {
                throw new IOException(
                    "Multiple logical Ogg streams are unsupported"
                );
            }

            if (pageSerial != serial || pageSequence != sequence++) {
                throw new IOException("Missing Ogg page");
            }

            int count = prefix[26] & 255;
            byte[] lacing = input.readNBytes(count);
            if (lacing.length != count) {
                throw new IOException("Truncated Ogg page table");
            }

            int size = 0;
            for (byte value : lacing) {
                size += value & 255;
            }

            byte[] payload = input.readNBytes(size);
            if (payload.length != size) {
                throw new IOException("Truncated Ogg page payload");
            }

            Arrays.fill(prefix, 22, 26, (byte) 0);
            int computed = crc(
                crc(crc(0, prefix), lacing),
                payload
            );
            if (computed != checksum) {
                throw new IOException("Ogg checksum mismatch");
            }

            int offset = 0;
            for (byte value : lacing) {
                int length = value & 255;
                if (packets < 3) {
                    if (header.size() + length > 16 * 1024 * 1024) {
                        throw new IOException(
                            "Vorbis header is too large"
                        );
                    }
                    header.write(payload, offset, length);
                }
                offset += length;
                continued = length == 255;

                if (!continued) {
                    if (packets < 3) {
                        byte[] bytes = header.toByteArray();
                        if (bytes.length < 7
                            || bytes[0] != (byte) (packets * 2 + 1)
                            || !"vorbis".equals(
                                new String(
                                    bytes,
                                    1,
                                    6,
                                    StandardCharsets.US_ASCII
                                )
                            )) {
                            throw new IOException(
                                "Expected Ogg Vorbis header"
                            );
                        }

                        if (packets == 0) {
                            if (bytes.length != 30) {
                                throw new IOException(
                                    "Invalid Vorbis identification header"
                                );
                            }

                            var info = ByteBuffer.wrap(bytes)
                                .order(ByteOrder.LITTLE_ENDIAN);
                            rate = info.getInt(12);
                            int small = bytes[28] & 15;
                            int large = (bytes[28] & 255) >>> 4;
                            if (info.getInt(7) != 0
                                || bytes[11] == 0
                                || rate <= 0
                                || small < 6
                                || large > 13
                                || small > large
                                || bytes[29] != 1) {
                                throw new IOException(
                                    "Unsupported Vorbis parameters"
                                );
                            }
                        }
                        header.reset();
                    }
                    packets++;
                }
            }

            long pageGranule = buffer.getLong(6);
            if (pageGranule >= 0L) {
                if (pageGranule < granule) {
                    throw new IOException(
                        "Decreasing Ogg granule position"
                    );
                }
                granule = pageGranule;
            }

            ended = (flags & 4) != 0;
            first = false;
        }

        if (!ended
            || continued
            || packets < 4
            || granule <= 0L
            || rate <= 0) {
            throw new IOException("Incomplete or empty Vorbis stream");
        }

        return (double) granule / rate;
    }

    private static int crc(int crc, byte[] bytes) {
        for (byte value : bytes) {
            crc ^= (value & 255) << 24;
            for (int index = 0; index < 8; index++) {
                crc = (crc << 1)
                    ^ (crc < 0 ? 0x04C11DB7 : 0);
            }
        }
        return crc;
    }
}
