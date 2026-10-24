// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.nio.file.*;
import java.util.*;

/** Original procedural inputs only; paths are supplied by a portable manifest. */
public final class PrintedIsolatedFlagsOriginals {
    public static void main(String[] args) throws Exception {
        int cases = 0, positive = 0, negative = 0;
        long work = 0;
        for (String line : Files.readAllLines(Path.of(args[0]))) {
            String[] f = line.split("\\t", -1);
            String name = f[0];
            int w = Integer.parseInt(f[3]),
                    h = Integer.parseInt(f[4]),
                    direction = Integer.parseInt(f[6]),
                    left = Integer.parseInt(f[7]),
                    right = Integer.parseInt(f[8]),
                    cap = Integer.parseInt(f[9]),
                    start = (int) Math.round(Double.parseDouble(f[10]));
            double gap = Double.parseDouble(f[5]);
            byte[] raw = Files.readAllBytes(resolve(args[0], f[1])),
                    carrier = Files.readAllBytes(resolve(args[0], f[2]));
            var heads = new ArrayList<PrintedIsolatedFlags.Head>();
            heads.add(
                    new PrintedIsolatedFlags.Head(
                            0, Double.parseDouble(f[11]), Double.parseDouble(f[12]), gap));
            var ends = new ArrayList<PrintedBeamGeometry.End>();
            for (int x = left; x <= right; x++) ends.add(new PrintedBeamGeometry.End(x, cap));
            var shaft =
                    new PrintedBeamGeometry.Column(
                            0, 0, 0, 1, direction, left, right, cap, gap, 2, ends, start);
            var nodes = new ArrayList<PrintedBeamGeometry.Column>();
            nodes.add(shaft);
            if (!f[14].isEmpty())
                for (String text : f[14].split(";")) {
                    String[] z = text.split(",");
                    int id = nodes.size(),
                            rl = Integer.parseInt(z[0]),
                            rr = Integer.parseInt(z[1]),
                            rc = Integer.parseInt(z[2]);
                    double hx = Double.parseDouble(z[3]), hy = Double.parseDouble(z[4]);
                    heads.add(new PrintedIsolatedFlags.Head(id, hx, hy, gap));
                    var re = new ArrayList<PrintedBeamGeometry.End>();
                    for (int x = rl; x <= rr; x++) re.add(new PrintedBeamGeometry.End(x, rc));
                    nodes.add(
                            new PrintedBeamGeometry.Column(
                                    id,
                                    0,
                                    0,
                                    1,
                                    direction,
                                    rl,
                                    rr,
                                    rc,
                                    gap,
                                    1,
                                    re,
                                    (int) Math.round(hy)));
                }
            var budget = new PrintedIsolatedFlags.Budget(64000000);
            var result =
                    PrintedIsolatedFlags.prove(
                            raw,
                            w,
                            h,
                            shaft,
                            List.of(0),
                            heads,
                            nodes,
                            (x, y) -> carrier[y * w + x] != 0,
                            budget);
            boolean expected = Integer.parseInt(f[13]) > 0;
            if (budget.exhausted() || result.isPresent() != expected)
                throw new AssertionError(name + " expected=" + expected + " actual=" + result);
            cases++;
            if (expected) positive++;
            else negative++;
            work += budget.used();
        }
        System.out.println(
                "{\"status\":\"ORIGINAL_FLAG_CASES_PASS\",\"cases\":"
                        + cases
                        + ",\"positive\":"
                        + positive
                        + ",\"negative\":"
                        + negative
                        + ",\"work\":"
                        + work
                        + "}");
    }

    static Path resolve(String manifest, String file) {
        Path path = Path.of(file);
        return path.isAbsolute()
                ? path
                : Path.of(manifest).toAbsolutePath().getParent().resolve(path);
    }
}
