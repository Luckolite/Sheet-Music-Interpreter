// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Complete overlapping-envelope dispatch, enumerated per-column tilted ends. */
public final class OverlappingRivalOriginals {
    static int assertions, cases;

    static void check(boolean v, String text) {
        assertions++;
        if (!v) throw new AssertionError(text);
    }

    static PrintedBeamGeometry.Column node(
            int id, int left, int width, int cap, int start, int direction, int h, boolean mirror) {
        var ends = new ArrayList<PrintedBeamGeometry.End>();
        for (int x = left; x < left + width; x++) {
            int y = cap + (x - left) % 3 * (direction < 0 ? 1 : -1);
            ends.add(new PrintedBeamGeometry.End(x, mirror ? h - 1 - y : y));
        }
        return new PrintedBeamGeometry.Column(
                id,
                id,
                id % 2,
                2,
                mirror ? -direction : direction,
                left,
                left + width - 1,
                mirror ? h - 1 - cap : cap,
                14,
                1,
                ends,
                mirror ? h - 1 - start : start);
    }

    public static void main(String[] args) {
        int w = 420, h = 480;
        for (int aw : new int[] {1, 3, 9})
            for (int bw : new int[] {1, 3, 9})
                for (int offset = -8; offset <= 8; offset++) {
                    int ax = 200, bx = ax + offset;
                    if (bx > ax + aw - 1 || bx + bw - 1 < ax) continue;
                    for (boolean opposite : new boolean[] {false, true})
                        for (boolean mirror : new boolean[] {false, true})
                            for (boolean joined : new boolean[] {false, true}) {
                                byte[] raw = new byte[w * h];
                                Arrays.fill(raw, (byte) 255);
                                var a = node(0, ax, aw, 100, 160, -1, h, mirror);
                                var b =
                                        node(
                                                1,
                                                bx,
                                                bw,
                                                300,
                                                opposite ? 240 : 360,
                                                opposite ? 1 : -1,
                                                h,
                                                mirror);
                                for (var n : List.of(a, b))
                                    for (var end : n.shaftColumns())
                                        for (int y = Math.min(end.endY(), n.shaftStart());
                                                y <= Math.max(end.endY(), n.shaftStart());
                                                y++) raw[y * w + end.x()] = 0;
                                if (joined) {
                                    int x = Math.max(a.left(), b.left()),
                                            first = Math.min(a.shaftStart(), b.shaftStart()),
                                            last = Math.max(a.shaftStart(), b.shaftStart());
                                    for (int y = first; y <= last; y++) raw[y * w + x] = 0;
                                }
                                var plane =
                                        new FiniteRawBeamStrip.Carrier() {
                                            public boolean at(int x, int y) {
                                                return true;
                                            }

                                            public boolean unknown(int x, int y) {
                                                return true;
                                            }
                                        };
                                var budget = new PrintedIsolatedFlags.Budget(64000000);
                                var result =
                                        new RawPrimaryBeamAbsence(raw, w, h, plane, budget)
                                                .prove(a, List.of(a, b));
                                check(
                                        !budget.exhausted() && result.isPresent() != joined,
                                        "Overlap relation lost aw="
                                                + aw
                                                + " bw="
                                                + bw
                                                + " offset="
                                                + offset
                                                + " opposite="
                                                + opposite
                                                + " mirror="
                                                + mirror
                                                + " joined="
                                                + joined);
                                if (result.isPresent())
                                    for (var certificate : result.get())
                                        check(
                                                certificate.checkedOverlappingRivalShafts() == 1
                                                        && certificate.checkedRivalShafts() == 0,
                                                "Each distinct overlap must be dispatched once, not silently skipped or counted as a separated rival");
                                cases++;
                            }
                }
        System.out.println(
                "{\"status\":\"ALL_OVERLAP_BACKBONE_CONTACTS_PASS\",\"assertions\":"
                        + assertions
                        + ",\"cases\":"
                        + cases
                        + "}");
    }
}
