// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original multi-view prefix and staff-separated curved continuation geometry. */
public class FiniteSlideCompleteViewsOriginalChecks {
    static int checks;

    static void check(boolean ok, String m) {
        checks++;
        if (!ok) throw new AssertionError(m);
        System.out.println("PASS " + m);
    }

    static void prefixes(int dir) {
        var s = new FiniteConnectedSlidePortsOriginalChecks.Scene(1, false, dir, -dir);
        s.line();
        var full = NoteSlideDetector.removeStaffLines(s.gray, s.w, s.h, s.staffs);
        var suffix = full.clone();
        for (int x = 124; x < 144; x++)
            for (int y = 108; y < 123; y++) suffix[y * s.w + x] = (byte) 255;
        var only =
                FiniteConnectedSlidePorts.find(
                        s.gray, s.w, s.h, s.staffs, s.heads, suffix, null, null);
        check(
                only.size() == 1 && only.get(0).left() == 144,
                "isolated suffix fixture demonstrates hidden prefix dir=" + dir);
        for (boolean reverse : new boolean[] {false, true}) {
            var out =
                    FiniteConnectedSlidePorts.find(
                            s.gray,
                            s.w,
                            s.h,
                            s.staffs,
                            s.heads,
                            reverse ? full : suffix,
                            reverse ? suffix : full,
                            null);
            check(
                    out.size() == 1 && out.get(0).left() == 132 && out.get(0).right() == 160,
                    "both view orders select whole corroborated port dir="
                            + dir
                            + " reverse="
                            + reverse);
        }
    }

    static List<NoteSlideDetector.Stroke> continuation(
            int dir, boolean crossView, boolean bridge, boolean phase) {
        var s = new FiniteConnectedSlidePortsOriginalChecks.Scene(1, false, dir, -dir);
        byte[] primary = new byte[s.w * s.h], tail = new byte[s.w * s.h];
        Arrays.fill(primary, (byte) 255);
        Arrays.fill(tail, (byte) 255);
        for (int x = 132; x <= 148; x++) {
            float cy = 119 + (x - 132) * .3f;
            int lo = phase && x < 134 ? (int) Math.floor(cy) - 1 : (int) Math.floor(cy),
                    hi = phase && x < 134 ? lo : (int) Math.ceil(cy) + 1;
            for (int y = lo; y <= hi; y++) {
                primary[y * s.w + x] = 35;
                s.gray[y * s.w + x] = 35;
            }
        }
        for (int x = 147; x <= 150; x++)
            for (int y = 128; y <= 132; y++) {
                tail[y * s.w + x] = 35;
                s.gray[y * s.w + x] = 35;
            }
        if (!bridge)
            for (int x = 147; x <= 148; x++)
                for (int y = 126; y <= 127; y++) s.gray[y * s.w + x] = (byte) 255;
        if (!crossView)
            for (int i = 0; i < primary.length; i++)
                if ((tail[i] & 255) < 150) primary[i] = tail[i];
        return FiniteConnectedSlidePorts.find(
                s.gray, s.w, s.h, s.staffs, s.heads, primary, crossView ? tail : null, null);
    }

    public static void main(String[] a) {
        for (int dir : new int[] {-1, 1}) {
            prefixes(dir);
            for (boolean cross : new boolean[] {false, true})
                for (boolean phase : new boolean[] {false, true}) {
                    check(
                            continuation(dir, cross, true, phase).isEmpty(),
                            "verified raw-rule overlapping continuation refuses partial component dir="
                                    + dir
                                    + " crossView="
                                    + cross
                                    + " phase="
                                    + phase);
                    check(
                            !continuation(dir, cross, false, phase).isEmpty(),
                            "disconnected foreign fragment cannot fabricate a verified-rule continuation dir="
                                    + dir
                                    + " crossView="
                                    + cross
                                    + " phase="
                                    + phase);
                }
        }
        System.out.println("CHECKS=" + checks);
    }
}
