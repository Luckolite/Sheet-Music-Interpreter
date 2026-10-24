// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.nio.file.*;
import java.util.*;

/** Literal primary at a true flag junction remains possible, including UNKNOWN core. */
public final class RawPrimaryBeamAbsenceOriginals {
    public static void main(String[] a) throws Exception {
        int assertions = 0;
        for (String line : Files.readAllLines(Path.of(a[0]))) {
            String[] f = line.split("\\t", -1);
            if (!f[0].contains("beam_through")) continue;
            int w = Integer.parseInt(f[3]),
                    h = Integer.parseInt(f[4]),
                    direction = Integer.parseInt(f[6]),
                    left = Integer.parseInt(f[7]),
                    right = Integer.parseInt(f[8]),
                    cap = Integer.parseInt(f[9]),
                    start = (int) Math.round(Double.parseDouble(f[10]));
            double gap = Double.parseDouble(f[5]);
            byte[] raw = Files.readAllBytes(resolve(a[0], f[1]));
            var ends = new ArrayList<PrintedBeamGeometry.End>();
            for (int x = left; x <= right; x++) ends.add(new PrintedBeamGeometry.End(x, cap));
            var shaft =
                    new PrintedBeamGeometry.Column(
                            0, 0, 0, 1, direction, left, right, cap, gap, 1, ends, start);
            var nodes = new ArrayList<PrintedBeamGeometry.Column>();
            nodes.add(shaft);
            for (String text : f[14].split(";")) {
                String[] z = text.split(",");
                int rl = Integer.parseInt(z[0]),
                        rr = Integer.parseInt(z[1]),
                        rc = Integer.parseInt(z[2]);
                var es = new ArrayList<PrintedBeamGeometry.End>();
                for (int x = rl; x <= rr; x++) es.add(new PrintedBeamGeometry.End(x, rc));
                nodes.add(
                        new PrintedBeamGeometry.Column(
                                nodes.size(),
                                0,
                                0,
                                1,
                                direction,
                                rl,
                                rr,
                                rc,
                                gap,
                                1,
                                es,
                                (int) Math.round(Double.parseDouble(z[4]))));
            }
            for (boolean unknown : new boolean[] {false, true}) {
                var plane =
                        new FiniteRawBeamStrip.Carrier() {
                            public boolean at(int x, int y) {
                                return false;
                            }

                            public boolean unknown(int x, int y) {
                                return unknown;
                            }
                        };
                var budget = new PrintedIsolatedFlags.Budget(64000000);
                if (new RawPrimaryBeamAbsence(raw, w, h, plane, budget)
                                .prove(shaft, nodes)
                                .isPresent()
                        || budget.exhausted())
                    throw new AssertionError(f[0] + " false beam absence unknown=" + unknown);
                assertions++;
            }
        }
        System.out.println(
                "{\"status\":\"RAW_PRIMARY_POSSIBILITY_PASS\",\"assertions\":" + assertions + "}");
    }

    static Path resolve(String manifest, String file) {
        Path path = Path.of(file);
        return path.isAbsolute()
                ? path
                : Path.of(manifest).toAbsolutePath().getParent().resolve(path);
    }
}
