// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public final class PartialSignatureOriginals {
    static int checks;

    static void check(boolean b, String why) {
        checks++;
        if (!b) throw new AssertionError(why);
    }

    static final class Scene {
        final int w = 400, h = 320;
        final byte[] g = new byte[w * h], a = new byte[w * h];
        final float gap, lastX, lastPitch, firstHead;

        Scene(int gap, int offset) {
            this.gap = gap;
            lastX = 120 + offset;
            lastPitch = 128 + offset;
            firstHead = lastX + gap * 6;
            Arrays.fill(g, (byte) 255);
        }

        void ink(int x, int y, byte label) {
            g[y * w + x] = 0;
            a[y * w + x] = label;
        }

        void partial(boolean natural, boolean noteLabel, boolean foreign, boolean oneBar) {
            int x = Math.round(lastX + gap),
                    z = Math.round(x + gap * .5f),
                    cy = Math.round(lastPitch + gap * 1.5f);
            for (int y = Math.round(cy - gap * 1.2f); y <= Math.round(cy + gap * 1.2f); y++)
                for (int dx = 0; dx <= 1; dx++) {
                    if (y < cy - gap * .3f || y > cy + gap * .05f)
                        ink(x + dx, y, (byte) (noteLabel ? 2 : 3));
                    if (y < cy + gap * .35f || y > cy + gap * .6f)
                        ink(z + dx, y, (byte) (noteLabel ? 2 : 5));
                }
            for (int bar = 0; bar < (oneBar ? 1 : 2); bar++) {
                int yy = Math.round(cy + (bar == 0 ? -.5f : .5f) * gap);
                int l = natural ? (bar == 0 ? x : z) : Math.round(x - gap * .15f),
                        r =
                                natural
                                        ? (bar == 0 ? z : Math.round(z + gap * .2f))
                                        : Math.round(z + gap * .15f);
                if (foreign) {
                    l = 20;
                    r = 300;
                }
                for (int y = yy; y <= yy + 1; y++)
                    for (int xx = l; xx <= r; xx++) ink(xx, y, (byte) (noteLabel ? 2 : 3));
            }
        }

        PartialSignatureConfidence.Observation observe() {
            return PartialSignatureConfidence.inspect(g, a, w, h, lastX, lastPitch, gap, firstHead);
        }
    }

    public static void main(String[] args) {
        for (int gap : new int[] {10, 16, 22})
            for (int off : new int[] {0, 1})
                for (boolean note : new boolean[] {false, true}) {
                    var s = new Scene(gap, off);
                    s.partial(false, note, false, false);
                    var o = s.observe();
                    check(
                            o.state == PartialSignatureConfidence.State.PARTIAL_POSSIBLE_SHARP,
                            "partial family g"
                                    + gap
                                    + " offset"
                                    + off
                                    + " note"
                                    + note
                                    + " "
                                    + o.reason);
                    check(
                            o.refusesDecrease(2, 1, true, false, s.g, s.a, s.w, s.h),
                            "refuse unsupported2to1");
                    check(
                            !o.refusesDecrease(2, 0, true, true, s.g, s.a, s.w, s.h),
                            "natural cancellation survives");
                    check(
                            !o.refusesDecrease(1, 2, true, false, s.g, s.a, s.w, s.h),
                            "increase survives");
                    check(
                            !o.refusesDecrease(2, 1, false, false, s.g, s.a, s.w, s.h),
                            "foreign layout unqualified");
                    check(!o.sameSource(s.g.clone(), s.a, s.w, s.h), "foreign raw refused");
                    int p = o.reads.get(0).index();
                    s.g[p] ^= 1;
                    check(!o.sameSource(s.g, s.a, s.w, s.h), "changed raw refused");
                    System.out.println(
                            "PASS partial gap="
                                    + gap
                                    + " offset="
                                    + off
                                    + " noteLabel="
                                    + note
                                    + " reason="
                                    + o.reason);
                }
        for (String type :
                List.of("completeOneSharp", "natural", "oneBar", "foreignRail", "cueChord")) {
            var s = new Scene(16, 0);
            if (type.equals("natural")) s.partial(true, false, false, false);
            if (type.equals("oneBar")) s.partial(false, false, false, true);
            if (type.equals("foreignRail")) s.partial(false, false, true, false);
            if (type.equals("cueChord"))
                for (int cy : new int[] {145, 161})
                    for (int y = cy - 3; y <= cy + 3; y++)
                        for (int x = 139; x <= 147; x++)
                            if ((x - 143) * (x - 143) / 16.0 + (y - cy) * (y - cy) / 9.0 <= 1)
                                s.ink(x, y, (byte) 2);
            var o = s.observe();
            check(
                    !o.refusesDecrease(2, 1, true, false, s.g, s.a, s.w, s.h),
                    type + " was vetoed " + o.reason);
            System.out.println("PASS control=" + type + " state=" + o.state + " noNewVeto=true");
        }
        System.out.println(
                "PASS checks=" + checks + " actualQueries=0 normalCalls=0 productionEdits=0");
    }
}
