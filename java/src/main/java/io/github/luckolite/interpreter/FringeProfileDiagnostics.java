// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Private bounded trace channel; independent of the saturated head diagnostic buffer. */
final class FringeProfileDiagnostics {
    private static final int MAX_LINES = 4096;
    private static final int MAX_UTF8_BYTES = 8 * 1024 * 1024;
    private static final List<String> LINES = new ArrayList<>();
    private static int bytes;
    private static long dropped;

    private FringeProfileDiagnostics() {}

    static boolean enabled() {
        return Boolean.getBoolean("sheet.interpreter.physicalDiagnostics");
    }

    static synchronized void record(String line) {
        if (!enabled() || line == null) return;
        int size = line.getBytes(StandardCharsets.UTF_8).length + 1;
        if (LINES.size() >= MAX_LINES || size > MAX_UTF8_BYTES - bytes) {
            dropped++;
            return;
        }
        LINES.add(line);
        bytes += size;
    }

    static synchronized void flush(Path path) throws IOException {
        StringBuilder out = new StringBuilder(bytes + 64);
        for (String line : LINES) out.append(line).append('\n');
        out.append("TRACE_TERMINAL\tlines=")
                .append(LINES.size())
                .append("\tbytes=")
                .append(bytes)
                .append("\tdropped=")
                .append(dropped)
                .append("\tmaxLines=")
                .append(MAX_LINES)
                .append("\tmaxBytes=")
                .append(MAX_UTF8_BYTES)
                .append('\n');
        Files.write(path, out.toString().getBytes(StandardCharsets.UTF_8));
    }
}
