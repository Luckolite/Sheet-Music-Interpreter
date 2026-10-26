// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.util.function.LongConsumer;

/** Original separated raw components, never captured notation. */
public final class ForeignFlankOriginals {
    static int checks;

    static void check(boolean b, String message) {
        checks++;
        if (!b) throw new AssertionError(message);
    }

    static final class Meter implements LongConsumer {
        long remaining, used;

        Meter(long n) {
            remaining = n;
        }

        public void accept(long n) {
            if (n < 0 || n > remaining) throw new Exhausted();
            remaining -= n;
            used += n;
        }
    }

    static final class Exhausted extends RuntimeException {}

    static final class Field {
        final int w = 192, h = 384, x = 96, g, top = 64;
        final byte[] raw = new byte[w * h];

        Field(int gap) {
            g = gap;
            Arrays.fill(raw, (byte) 255);
            for (int line = 0; line < 5; line++)
                for (int xx = 0; xx < w; xx++) {
                    int y = top + line * g;
                    raw[(y - 1) * w + xx] = (byte) 180;
                    raw[y * w + xx] = 100;
                    raw[(y + 1) * w + xx] = (byte) 180;
                }
            for (int line : new int[] {0, 3})
                for (int y = top + line * g - (int) Math.ceil(g * .4);
                        y <= top + line * g + (int) Math.ceil(g * .4);
                        y++) for (int xx = 0; xx < w; xx++) raw[y * w + xx] = 80;
        }

        void ink(int side, int line, String kind) {
            int sx = x + side * Math.max(2, Math.round(g * .45f)),
                    cy = top + line * g,
                    r = Math.max(3, Math.round(g * .35f));
            if (kind.equals("openLow")) {
                raw[(cy - r) * w + sx] = 60;
                raw[(cy - r - 1) * w + sx] = 60;
            }
            if (kind.equals("openHigh")) {
                raw[(cy + r) * w + sx] = 60;
                raw[(cy + r + 1) * w + sx] = 60;
            }
            if (kind.equals("rival")) raw[(cy + 3) * w + sx] = 100;
            if (kind.equals("merged")) for (int y = cy - r; y <= cy; y++) raw[y * w + sx] = 100;
            if (kind.equals("missing"))
                for (int y = cy - 1; y <= cy + 1; y++) raw[y * w + sx] = (byte) 255;
        }

        PrintedBeamLocalRuleInk helper(LongConsumer meter) {
            return new PrintedBeamLocalRuleInk(null, raw, w, h, top + 4 * g, g, meter);
        }
    }

    static void run() {
        for (int gap : new int[] {8, 12, 16, 24, 32, 48, 64})
            for (int side : new int[] {-1, 1})
                for (String kind : new String[] {"openLow", "openHigh"}) {
                    Field f = new Field(gap);
                    f.ink(side, 1, kind);
                    byte[] old = f.raw.clone();
                    Meter q = new Meter(1000000);
                    var h = f.helper(q);
                    check(
                            h.carrier(f.x, f.top),
                            "separated " + kind + " gap" + gap + " side" + side);
                    check(h.provedAt(f.x, f.top - 3), "profile ready");
                    check(!h.carrier(f.x, f.top - 3), "real thick beam core retained");
                    check(Arrays.equals(old, f.raw), "raw unchanged");
                    long exact = q.used;
                    Meter eq = new Meter(exact);
                    var again = f.helper(eq);
                    check(again.carrier(f.x, f.top), "exact funded foreign scan");
                    check(again.provedAt(f.x, f.top - 3), "exact profile");
                    check(!again.carrier(f.x, f.top - 3), "exact beam core");
                    check(eq.used == exact, "same price");
                    try {
                        var under = f.helper(new Meter(exact - 1));
                        under.carrier(f.x, f.top);
                        under.provedAt(f.x, f.top - 3);
                        under.carrier(f.x, f.top - 3);
                        throw new AssertionError("underfunded");
                    } catch (Exhausted expected) {
                        checks++;
                    }
                    var legacy =
                            new PrintedBeamLocalRuleInk(
                                    null, f.raw, f.w, f.h, f.top + 4 * f.g, f.g);
                    check(!legacy.carrier(f.x, f.top), "old unpaid min2 stays unchanged");
                }
        for (int side : new int[] {-1, 1})
            for (String kind : new String[] {"rival", "merged", "missing"}) {
                Field f = new Field(16);
                f.ink(side, 1, kind);
                var h = f.helper(new Meter(1000000));
                check(!h.carrier(f.x, f.top), "unknown " + kind);
                check(!h.provedAt(f.x, f.top), "no proof " + kind);
            }
        for (int side : new int[] {-1, 1}) {
            Field f = new Field(64);
            int sx = f.x + side * Math.round(f.g * .45f),
                    cy = f.top + f.g,
                    r = Math.round(f.g * .35f);
            // One closed foreign run is wider than the eligible thin-profile bound.
            // A literal paper row separates the real rule, which is within old phase tolerance.
            for (int y = cy - r - 1; y <= cy + 4; y++) f.raw[y * f.w + sx] = (byte) 255;
            for (int y = cy - r + 1; y <= cy; y++) f.raw[y * f.w + sx] = 60;
            f.raw[(cy + 1) * f.w + sx] = (byte) 180;
            f.raw[(cy + 2) * f.w + sx] = 100;
            f.raw[(cy + 3) * f.w + sx] = (byte) 180;
            var h = f.helper(new Meter(1000000));
            check(h.carrier(f.x, f.top), "separate closed thick foreign ink");
            check(!h.carrier(f.x, f.top - 3), "foreign thick component cannot erase genuine beam");
        }
    }

    public static void main(String[] args) {
        run();
        System.out.println("ForeignFlankOriginals PASS " + checks);
    }
}
