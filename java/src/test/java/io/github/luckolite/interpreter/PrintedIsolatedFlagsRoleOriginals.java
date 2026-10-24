// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.nio.file.*;
import java.util.*;

public final class PrintedIsolatedFlagsRoleOriginals {
    public static void main(String[] args) throws Exception {
        int cases = 0;
        for (String line : Files.readAllLines(Path.of(args[0]))) {
            String[] f = line.split("\\t", -1);
            String name = f[0], base = name.substring(0, name.indexOf('-'));
            if (!Set.of("single", "straight_beamlet", "sloping_beamlet", "tuplet_contact")
                    .contains(base)) continue;
            int w = Integer.parseInt(f[3]),
                    h = Integer.parseInt(f[4]),
                    direction = Integer.parseInt(f[6]),
                    left = Integer.parseInt(f[7]),
                    right = Integer.parseInt(f[8]),
                    cap = Integer.parseInt(f[9]),
                    start = (int) Math.round(Double.parseDouble(f[10]));
            double gap = Double.parseDouble(f[5]),
                    hx = Double.parseDouble(f[11]),
                    hy = Double.parseDouble(f[12]);
            byte[] raw = Files.readAllBytes(resolve(args[0], f[1])),
                    mask = Files.readAllBytes(resolve(args[0], f[2]));
            var ends = new ArrayList<PrintedBeamGeometry.End>();
            for (int x = left; x <= right; x++) ends.add(new PrintedBeamGeometry.End(x, cap));
            var shaft =
                    new PrintedBeamGeometry.Column(
                            0, 0, 0, 1, direction, left, right, cap, gap, 2, ends, start);
            var heads = List.of(new PrintedIsolatedFlags.Head(0, hx, hy, gap));
            var nodes = List.of(shaft);
            for (int y = Math.min(cap, (int) Math.round(hy));
                    y <= Math.max(cap, (int) Math.round(hy));
                    y++) raw[y * w + right + 1] = (byte) 150;
            var plain =
                    new FiniteRawBeamStrip.Carrier() {
                        public boolean at(int x, int y) {
                            return mask[y * w + x] != 0;
                        }
                    };
            boolean accepted =
                    PrintedIsolatedFlags.prove(
                                    raw,
                                    w,
                                    h,
                                    shaft,
                                    List.of(0),
                                    heads,
                                    nodes,
                                    plain,
                                    new PrintedIsolatedFlags.Budget(64000000))
                            .isPresent();
            if (accepted != base.equals("single"))
                throw new AssertionError(name + " fringe " + accepted);
            cases++;
            if (base.equals("single")) {
                int headward = -direction,
                        rootLow = Math.min(cap, (int) (cap + headward * gap * 1.3)),
                        rootHigh = Math.max(cap, (int) (cap + headward * gap * 1.3)),
                        collar = Math.max(2, (int) Math.ceil(gap * .25));
                var uncertain =
                        new FiniteRawBeamStrip.Carrier() {
                            public boolean at(int x, int y) {
                                return mask[y * w + x] != 0;
                            }

                            public boolean unknown(int x, int y) {
                                return x >= left
                                        && x <= right + collar
                                        && y >= rootLow
                                        && y <= rootHigh;
                            }
                        };
                if (PrintedIsolatedFlags.prove(
                                raw,
                                w,
                                h,
                                shaft,
                                List.of(0),
                                heads,
                                nodes,
                                uncertain,
                                new PrintedIsolatedFlags.Budget(64000000))
                        .isPresent()) throw new AssertionError(name + " unknown root");
                cases++;
                byte[] broken = raw.clone();
                for (int y = 0; y < h; y++) broken[y * w + right + 1] = (byte) 255;
                if (PrintedIsolatedFlags.prove(
                                broken,
                                w,
                                h,
                                shaft,
                                List.of(0),
                                heads,
                                nodes,
                                plain,
                                new PrintedIsolatedFlags.Budget(64000000))
                        .isPresent()) throw new AssertionError(name + " root white gutter");
                cases++;
                int scale = w / 160;
                double secondY = hy + headward * -16 * scale;
                byte[] chordRaw = raw.clone();
                for (int y = Math.max(0, (int) secondY - 4 * scale);
                        y <= Math.min(h - 1, (int) secondY + 4 * scale);
                        y++)
                    for (int x = Math.max(0, (int) hx - 6 * scale);
                            x <= Math.min(w - 1, (int) hx + 6 * scale);
                            x++)
                        if (Math.pow((x - hx) / (6 * scale), 2)
                                        + Math.pow((y - secondY) / (4 * scale), 2)
                                <= 1) chordRaw[y * w + x] = 0;
                var chordHeads =
                        List.of(heads.get(0), new PrintedIsolatedFlags.Head(1, hx, secondY, gap));
                var chord =
                        PrintedIsolatedFlags.prove(
                                chordRaw,
                                w,
                                h,
                                shaft,
                                List.of(0, 1),
                                chordHeads,
                                nodes,
                                plain,
                                new PrintedIsolatedFlags.Budget(64000000));
                if (chord.isEmpty() || !chord.get().owners().equals(List.of(0, 1)))
                    throw new AssertionError(name + " shared chord ownership");
                cases++;
                if (name.equals("single-up-1x")) {
                    var budget = new PrintedIsolatedFlags.Budget(1);
                    if (PrintedIsolatedFlags.prove(
                                            raw,
                                            w,
                                            h,
                                            shaft,
                                            List.of(0),
                                            heads,
                                            nodes,
                                            plain,
                                            budget)
                                    .isPresent()
                            || !budget.exhausted())
                        throw new AssertionError("Flag budget admission");
                    cases++;
                    if (PrintedIsolatedFlags.prove(
                                    raw, w, h, shaft, List.of(0), heads, nodes, plain, budget)
                            .isPresent()) throw new AssertionError("Flag budget not sticky");
                    cases++;
                }
            }
        }
        System.out.println(
                "{\"status\":\"FLAG_ROLE_ROOT_NEGATIVES_PASS\",\"assertions\":" + cases + "}");
    }

    static Path resolve(String manifest, String file) {
        Path path = Path.of(file);
        return path.isAbsolute()
                ? path
                : Path.of(manifest).toAbsolutePath().getParent().resolve(path);
    }
}
