// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Optional failure-isolated scalar-only diagnostic; never reads pixels or evaluates a guard. */
final class JoinedTupletObservation {
    private static final int MAX_EVENTS = 4096, MAX_BYTES = 1048576;
    private static BufferedWriter writer;
    private static Path path;
    private static boolean initialized, failed, overflow;
    private static int sequence, bytes, queries, ends, open;

    private JoinedTupletObservation() {}

    private static void init() {
        if (initialized) return;
        initialized = true;
        String value = System.getProperty("joinedTuplet.trace");
        if (value == null || value.isBlank()) return;
        try {
            path = Paths.get(value);
            if (!path.isAbsolute())
                throw new IllegalArgumentException("absolute trace path required");
            writer =
                    Files.newBufferedWriter(
                            path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (IOException | RuntimeException error) {
            failed = true;
        }
    }

    private static void emit(String code, String fields) {
        init();
        if (writer == null || failed || overflow) return;
        String text =
                "{\"sequence\":"
                        + (sequence + 1)
                        + ",\"query\":"
                        + queries
                        + ",\"code\":\""
                        + code
                        + "\""
                        + fields
                        + "}\n";
        int length = text.getBytes(StandardCharsets.UTF_8).length;
        if (sequence >= MAX_EVENTS || length > MAX_BYTES - bytes) {
            overflow = true;
            return;
        }
        try {
            writer.write(text);
            sequence++;
            bytes += length;
        } catch (IOException | RuntimeException error) {
            failed = true;
        }
    }

    static synchronized void begin(int width, int height, int first, int middle, int last) {
        init();
        if (path == null) return;
        if (open != 0) failed = true;
        queries++;
        open++;
        event("BEGIN", width, height, first, middle, last);
    }

    static synchronized void end(int status, String reason) {
        init();
        if (path == null) return;
        if (open != 1) failed = true;
        // reason is a literal or an exception class name, never page text or input-controlled JSON.
        emit("END", ",\"status\":" + status + ",\"reason\":\"" + reason + "\"");
        ends++;
        open--;
    }

    static synchronized void event(String code, int... values) {
        init();
        if (path == null || failed || overflow) return;
        StringBuilder fields = new StringBuilder(",\"values\":[");
        for (int i = 0; i < values.length; i++) {
            if (i != 0) fields.append(',');
            fields.append(values[i]);
        }
        emit(code, fields.append(']').toString());
    }

    static boolean reject(String code, boolean condition) {
        if (condition) event(code);
        return condition;
    }

    static synchronized void finish() {
        if (!initialized || path == null) return;
        if (open != 0 || queries != ends) failed = true;
        emit("SUMMARY", ",\"queries\":" + queries + ",\"ends\":" + ends + ",\"open\":" + open);
        try {
            if (writer != null) writer.close();
            Path pending = Paths.get(path.toString() + ".health.pending");
            String health =
                    "{\"status\":\""
                            + (!failed && !overflow && open == 0 ? "COMPLETE" : "HOLD")
                            + "\",\"failed\":"
                            + failed
                            + ",\"overflow\":"
                            + overflow
                            + ",\"queries\":"
                            + queries
                            + ",\"ends\":"
                            + ends
                            + ",\"openQueries\":"
                            + open
                            + ",\"events\":"
                            + sequence
                            + ",\"bytes\":"
                            + bytes
                            + "}\n";
            Files.write(
                    pending,
                    health.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE_NEW);
            Files.move(
                    pending,
                    Paths.get(path.toString() + ".health.json"),
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException error) {
            failed = true;
        }
    }
}
