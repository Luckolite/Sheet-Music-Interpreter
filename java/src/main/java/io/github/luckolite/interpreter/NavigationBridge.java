// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Decoded-data bridge to the same navigation engine, without pixels or inference. */
public final class NavigationBridge {
    private NavigationBridge() {}

    static ScoreNavigationPlan read(DataInputStream input) throws IOException {
        if (input.readInt() != 0x4e415631) throw new IOException("Not a NAV1 request");
        int count = input.readInt();
        if (count < 0 || count > 100000) throw new IOException("Invalid source measure count");
        var meters = new ArrayList<ScoreMeterChange>();
        float opening = 4, previous = -1;
        for (int m = 0; m < count; m++) {
            float beats = input.readFloat();
            if (!Float.isFinite(beats) || beats < .125f || beats > 128)
                throw new IOException("Invalid measure beats");
            if (m == 0) opening = beats;
            if (beats != previous) {
                ScoreMeterChange change = null;
                for (int denominator = 1; denominator <= 32; denominator *= 2) {
                    double numerator = beats * denominator / 4.0;
                    if (numerator == Math.rint(numerator) && numerator >= 1 && numerator <= 32) {
                        change = new ScoreMeterChange(m, (int) numerator, denominator);
                        break;
                    }
                }
                if (change == null)
                    throw new IOException("Measure beats cannot represent a supported meter");
                meters.add(change);
                previous = beats;
            }
        }
        var directions = ScoreSemanticWire.readDirections(input, count);
        if (!ScoreSemanticWire.readExpressions(input, count).isEmpty())
            throw new IOException("NAV1 carries navigation only, not expressive realization");
        if (input.read() != -1) throw new IOException("Trailing navigation request bytes");
        return ScoreNavigationPlan.create(count, directions, new ScoreMeterMap(opening, meters));
    }

    static Object readArrangements(DataInputStream input) throws IOException {
        if (input.readInt() != 0x4e415031) throw new IOException("Not a NAP1 request");
        int pages = input.readInt();
        if (pages < 0 || pages > 100000) throw new IOException("Invalid source page count");
        int[] counts = new int[pages], starts = new int[pages];
        long total = 0;
        for (int p = 0; p < pages; p++) {
            counts[p] = input.readInt();
            starts[p] = input.readInt();
            total += counts[p];
            if (counts[p] < 0 || total > 100000 || starts[p] < 0)
                throw new IOException("Invalid source page mapping");
        }
        if (input.read() != -1) throw new IOException("Trailing page mapping bytes");
        return ScorePageTimeline.arrangements(counts, starts);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("Usage: NavigationBridge request.nav output.json");
        var path = Path.of(args[0]);
        if (Files.size(path) > 64L * 1024 * 1024)
            throw new IOException("Navigation request too large");
        Object result;
        try (var input = new DataInputStream(Files.newInputStream(path))) {
            if (input.readInt() == 0x4e415031) {
                try (var mappingInput = new DataInputStream(Files.newInputStream(path))) {
                    result = readArrangements(mappingInput);
                }
            } else {
                try (var routeInput = new DataInputStream(Files.newInputStream(path))) {
                    var route = read(routeInput).traversal();
                    route.requirePerformanceQualified();
                    result = route;
                }
            }
        }
        Files.writeString(Path.of(args[1]), Main.json(result) + "\n");
    }
}
