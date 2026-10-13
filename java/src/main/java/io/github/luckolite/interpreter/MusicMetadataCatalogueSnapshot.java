// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.util.*;

/** Portable immutable snapshot loader shared by Android and the standalone page worker. */
public final class MusicMetadataCatalogueSnapshot {
    private MusicMetadataCatalogueSnapshot() {}

    @FunctionalInterface
    public interface Source {
        InputStream open(String name) throws IOException;
    }

    private record Asset(String name, int length, String sha) {}

    private static final String MANIFEST_SHA =
            "f11cb6437e299246705ab221ae0f80c2a2d6f83740f68adf344f1ff15dd5f297";
    private static final int MANIFEST_SIZE = 7214;
    private static final List<Asset> ASSETS =
            List.of(
                    new Asset(
                            "numbered-work-aliases.tsv",
                            1825435,
                            "d3bdfc63d70608f7de19a56fdcd00d47b303f47cfad2f289bcfe2092334ba7bc"),
                    new Asset(
                            "musical-person-labels.tsv",
                            3599252,
                            "789f8147bd86ebe57b8559c49a2ef1b328210d6c41c7b10e73fafa59a460e475"),
                    new Asset(
                            "instrument-kind-labels.tsv",
                            295999,
                            "808240f5093d46476b4a75ff75304664d732927a719cb9780297a1c06c6bcdc4"));

    public static String manifestSha256() {
        return MANIFEST_SHA;
    }

    private static void interrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }

    private static byte[] read(Source source, String name, int length, String expected)
            throws Exception {
        interrupted();
        if (length < 1 || length > 20_000_000)
            throw new IOException("Invalid catalogue asset bound");
        try (var input = source.open(name);
                var output = new ByteArrayOutputStream(length)) {
            if (input == null) throw new IOException("Missing catalogue asset");
            byte[] block = new byte[32768];
            int count;
            long total = 0;
            while ((count = input.read(block)) != -1) {
                interrupted();
                total += count;
                if (total > length) throw new IOException("Catalogue asset exceeds bound");
                output.write(block, 0, count);
            }
            interrupted();
            byte[] bytes = output.toByteArray();
            var hash = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes))
                hash.append(String.format(Locale.ROOT, "%02x", value & 255));
            String actual = hash.toString();
            if (bytes.length != length || !actual.equals(expected))
                throw new IOException("Catalogue asset checksum differs");
            return bytes;
        }
    }

    private static Reader utf8(byte[] bytes) throws Exception {
        String text =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
        return new StringReader(text);
    }

    public static MusicMetadataCatalogue load(Source source) throws Exception {
        if (source == null) throw new IOException("Missing catalogue asset source");
        read(source, "manifest.json", MANIFEST_SIZE, MANIFEST_SHA);
        var readers = new ArrayList<Reader>();
        try {
            for (var asset : ASSETS) {
                interrupted();
                readers.add(utf8(read(source, asset.name(), asset.length(), asset.sha())));
            }
            var result =
                    MusicMetadataCatalogue.load(readers.get(0), readers.get(1), readers.get(2));
            interrupted();
            return result;
        } finally {
            for (var reader : readers) reader.close();
        }
    }
}
