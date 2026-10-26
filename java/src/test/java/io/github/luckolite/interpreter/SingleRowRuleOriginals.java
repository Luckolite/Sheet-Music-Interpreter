// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.util.function.LongConsumer;

/** Original procedural fields; no score coordinates, font, model, or labels. */
public final class SingleRowRuleOriginals {
    static int checks;

    static void check(boolean v, String s) {
        checks++;
        if (!v) throw new AssertionError(s);
    }

    static final class Paid implements LongConsumer {
        long used, limit;

        Paid(long n) {
            limit = n;
        }

        public void accept(long n) {
            if (n < 0 || n > limit - used) throw new Refusal();
            used += n;
        }
    }

    static final class Refusal extends RuntimeException {}

    static final class Field {
        final int w = 128, h = 256, x = 64, g, top;
        final byte[] raw = new byte[w * h];

        Field(int gap, int phase) {
            g = gap;
            top = 64 + phase;
            Arrays.fill(raw, (byte) 255);
            for (int i = 0; i < 5; i++)
                for (int xx = 0; xx < w; xx++) {
                    int y = top + i * g;
                    raw[(y - 1) * w + xx] = (byte) 180;
                    raw[y * w + xx] = 100;
                    raw[(y + 1) * w + xx] = (byte) 180;
                }
            for (int i : new int[] {0, 3})
                for (int y = top + i * g - 4; y <= top + i * g + 4; y++)
                    for (int xx = 0; xx < w; xx++) raw[y * w + xx] = 80;
        }

        PrintedBeamLocalRuleInk helper(LongConsumer q) {
            return new PrintedBeamLocalRuleInk(null, raw, w, h, top + 4 * g, g, q);
        }

        void removeFlank(int side, int line) {
            int sx = x + side * Math.max(2, Math.round(g * .45f));
            for (int y = top + line * g - 2; y <= top + line * g + 2; y++)
                raw[y * w + sx] = (byte) 255;
        }
    }

    static void positive(int gap, int phase) {
        Field f = new Field(gap, phase);
        byte[] old = f.raw.clone();
        var legacy = new PrintedBeamLocalRuleInk(null, f.raw, f.w, f.h, f.top + 4 * f.g, f.g);
        check(!legacy.carrier(f.x, f.top), "legacy min2 baseline");
        check(!legacy.provedAt(f.x, f.top), "legacy remains unproved");
        Paid q = new Paid(100000);
        var next = f.helper(q);
        check(next.carrier(f.x, f.top), "actual staff component admitted");
        check(!next.carrier(f.x, f.top - 3), "literal thick beam core retained");
        check(next.provedAt(f.x, f.top - 3), "noncarrier proof comes from real references");
        check(q.used > 0, "new sampling paid");
        check(Arrays.equals(old, f.raw), "raw immutable");
    }

    static void negative(Field f, String label) {
        Paid q = new Paid(100000);
        var h = f.helper(q);
        check(!h.carrier(f.x, f.top), label + " carrier");
        check(!h.provedAt(f.x, f.top), label + " no profile");
    }

    public static void main(String[] args) {
        for (int gap : new int[] {8, 12, 16, 24})
            for (int phase : new int[] {0, 1, 2}) positive(gap, phase);
        for (int side : new int[] {-1, 1}) {
            Field f = new Field(16, 0);
            f.removeFlank(side, 1);
            negative(f, "missing flank " + side);
        }
        {
            Field f = new Field(16, 0);
            f.removeFlank(-1, 1);
            f.removeFlank(1, 1);
            negative(f, "isolated singleton");
        }
        {
            Field f = new Field(16, 0);
            int sx = f.x + Math.round(f.g * .45f), y = f.top + f.g;
            f.raw[(y + 3) * f.w + sx] = 100;
            negative(f, "second thin rival");
        }
        {
            Field f = new Field(16, 0);
            int sx = f.x + Math.round(f.g * .45f), y = f.top + f.g;
            for (int yy = y - 5; yy <= y + 5; yy++) f.raw[yy * f.w + sx] = 100;
            negative(f, "thick rival");
        }
        {
            Field f = new Field(16, 0);
            int sx = f.x + Math.round(f.g * .45f), y = f.top + f.g;
            for (int yy = y - 6; yy <= y + 6; yy++) f.raw[yy * f.w + sx] = (byte) 255;
            f.raw[(y + 3) * f.w + sx] = 100;
            negative(f, "wrong phase");
        }
        {
            Field f = new Field(16, 0);
            for (int yy = f.top + f.g - 1; yy <= f.top + f.g + 1; yy++)
                f.raw[yy * f.w + f.x] = (byte) 255;
            negative(f, "only two references");
        }
        {
            Field f = new Field(16, 0);
            Paid p = new Paid(100000);
            check(f.helper(p).carrier(f.x, f.top), "budget control");
            long exact = p.used;
            Paid q = new Paid(exact);
            check(f.helper(q).carrier(f.x, f.top) && q.used == exact, "exact budget");
            try {
                f.helper(new Paid(exact - 1)).carrier(f.x, f.top);
                throw new AssertionError("underpaid accepted");
            } catch (Refusal expected) {
                checks++;
            }
            try {
                f.helper(new Paid(0)).carrier(f.x, f.top);
                throw new AssertionError("zero accepted");
            } catch (Refusal expected) {
                checks++;
            }
        }
        System.out.println("PASS " + checks + " original assertions");
    }
}
