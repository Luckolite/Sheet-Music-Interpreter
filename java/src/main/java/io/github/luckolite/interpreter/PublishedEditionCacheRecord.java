// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

/** Versioned bounded cache payload; expiry and checksum failures retain the printed source. */
public final class PublishedEditionCacheRecord {
    private PublishedEditionCacheRecord() {}

    public static final int MAX_BYTES = 32768;
    public static final long MAX_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000;

    public record Entry(PublishedEditionCreditRecovery.Edition edition, long fetchedAtMillis) {
        public Entry {
            if (edition == null || fetchedAtMillis <= 0)
                throw new IllegalArgumentException("Invalid cache entry");
        }
    }

    private static byte[] sha(byte[] data) throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
    }

    private static void text(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 4096) throw new IOException("Cached publication field too large");
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String text(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > 4096) throw new IOException("Invalid cache field bounds");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static void names(DataOutputStream out, List<String> values) throws IOException {
        out.writeInt(values.size());
        for (String value : values) text(out, value);
    }

    private static List<String> names(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > 20) throw new IOException("Invalid cached credit count");
        var out = new ArrayList<String>();
        for (int i = 0; i < count; i++) out.add(text(in));
        return List.copyOf(out);
    }

    public static String encode(Entry entry) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var e = entry.edition();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(1);
            out.writeLong(entry.fetchedAtMillis());
            text(out, e.productId());
            text(out, e.title());
            names(out, e.composers());
            names(out, e.arrangers());
            text(out, e.sourceUrl());
            text(out, e.responseSha256());
        }
        byte[] payload = bytes.toByteArray();
        if (payload.length + 32 > MAX_BYTES)
            throw new IOException("Publication cache exceeds bounds");
        bytes.write(sha(payload));
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    public static Entry decode(String encoded, long nowMillis) throws IOException {
        if (encoded == null || encoded.length() > ((MAX_BYTES + 2) / 3) * 4 || nowMillis <= 0)
            throw new IOException("Invalid publication cache bounds");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid publication cache encoding", invalid);
        }
        if (bytes.length < 44 || bytes.length > MAX_BYTES)
            throw new IOException("Invalid publication cache length");
        byte[] payload = Arrays.copyOf(bytes, bytes.length - 32),
                checksum = Arrays.copyOfRange(bytes, bytes.length - 32, bytes.length);
        if (!MessageDigest.isEqual(sha(payload), checksum))
            throw new IOException("Publication cache checksum differs");
        try (var in = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (in.readInt() != 1) throw new IOException("Unknown publication cache record layout");
            long fetched = in.readLong();
            if (fetched <= 0
                    || fetched > nowMillis + 300000L
                    || nowMillis - fetched > MAX_AGE_MILLIS)
                throw new IOException("Publication cache is expired or has an invalid timestamp");
            String id = text(in), title = text(in);
            var composers = names(in);
            var arrangers = names(in);
            String url = text(in), sourceSha = text(in);
            if (in.read() != -1) throw new IOException("Trailing publication cache fields");
            try {
                return new Entry(
                        new PublishedEditionCreditRecovery.Edition(
                                id, title, composers, arrangers, url, sourceSha),
                        fetched);
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Invalid cached publication provenance", invalid);
            }
        }
    }
}
