// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;

/** Original procedural fixtures; source geometry consumes raw ink and segmentation, not saved score data. */
public final class GeometryEndpointAdmissionTest {
    @org.junit.Test
    public void originalEndpointAdmissionAndRefusalControls() throws Exception {
        main(new String[0]);
    }

    static int checks;
    static Class<?> sr;
    static Constructor<?> ctor;
    static Method head;

    static void yes(boolean v, String name) {
        checks++;
        if (!v) throw new AssertionError(name);
    }

    static final class Fixture {
        final int w = 800, h = 320, gap, top = 100, bottom;
        final byte[] labels = new byte[w * h], gray = new byte[w * h];

        Fixture(int gap) {
            this.gap = gap;
            bottom = top + 4 * gap;
            Arrays.fill(gray, (byte) 255);
            for (int i = 0; i < 5; i++)
                for (int x = 40; x < 740; x++) {
                    gray[(top + i * gap) * w + x] = 0;
                    labels[(top + i * gap) * w + x] = 4;
                }
        }

        void ink(int x, int y, byte kind) {
            if (x >= 0 && x < w && y >= 0 && y < h) {
                labels[y * w + x] = kind;
                gray[y * w + x] = 0;
            }
        }

        void note(int cx, int cy, boolean stem, boolean beam) {
            for (int y = cy - gap; y <= cy + gap; y++)
                for (int x = cx - gap * 2; x <= cx + gap * 2; x++)
                    if (Math.pow((x - cx) / (gap * .72), 2) + Math.pow((y - cy) / (gap * .43), 2)
                            <= 1) ink(x, y, (byte) 2);
            if (stem) {
                int axis = cx - Math.round(gap * .65f);
                for (int y = cy + Math.round(gap * .25f); y < cy + gap * 3; y++)
                    for (int dx = -1; dx <= 1; dx++) ink(axis + dx, y, (byte) 1);
                if (beam)
                    for (int y = cy + gap * 3 - 4; y < cy + gap * 3; y++)
                        for (int x = axis; x < axis + gap * 3; x++) ink(x, y, (byte) 1);
            }
        }

        StaffPitchTrack track() {
            return StaffPitchTrack.forPrintedSlurs(gray, w, h, top, bottom, gap);
        }

        Object staff(int left, int right, StaffPitchTrack t) throws Exception {
            return ctor.newInstance(
                    top, bottom, (float) gap, left, right, List.of(left, right), 0f, t);
        }

        boolean admits(int side) throws Exception {
            var t = track();
            return (boolean)
                    head.invoke(
                            null, labels, gray, w, h, staff(40, 740, t), staff(200, 600, t), side);
        }
    }

    public static void main(String[] a) throws Exception {
        sr = Class.forName("io.github.luckolite.interpreter.OmrMeasurePostProcessor$StaffRun");
        ctor =
                sr.getDeclaredConstructor(
                        int.class,
                        int.class,
                        float.class,
                        int.class,
                        int.class,
                        List.class,
                        float.class,
                        StaffPitchTrack.class);
        ctor.setAccessible(true);
        head =
                OmrMeasurePostProcessor.class.getDeclaredMethod(
                        "exteriorStaffHead",
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        sr,
                        sr,
                        int.class);
        head.setAccessible(true);
        for (int gap : new int[] {10, 14, 20}) {
            Fixture f = new Fixture(gap);
            f.note(120, f.top + gap * 2, true, false);
            yes(f.admits(0), "leading quarter, gap" + gap);
            f = new Fixture(gap);
            f.note(630, f.top + gap, true, true);
            yes(f.admits(1), "final beamed head, gap" + gap);
            for (int side = 0; side < 2; side++) {
                f = new Fixture(gap);
                int cx = side == 0 ? 120 : 630, cy = f.top + gap * 2;
                f.note(cx, cy, false, false);
                int axis = cx + Math.round(gap * .65f);
                for (int y = cy - gap * 3; y < cy - Math.round(gap * .25f); y++)
                    for (int dx = -1; dx <= 1; dx++) f.ink(axis + dx, y, (byte) 1);
                yes(f.admits(side), "upstem source endpoint side" + side + ", gap" + gap);
            }
            f = new Fixture(gap);
            f.note(120, f.top + gap * 2, false, false);
            yes(!f.admits(0), "head without shaft, gap" + gap);
            f = new Fixture(gap);
            f.note(120, f.top + gap * 2, true, false);
            for (int y = f.top + gap; y < f.top + gap * 3; y++) f.labels[y * f.w + 120 - gap] = 3;
            yes(!f.admits(0), "clef-key body, gap" + gap);
            f = new Fixture(gap);
            f.note(630, f.top + gap, true, true);
            for (int i = 0; i < f.labels.length; i++) if (f.labels[i] == 2) f.labels[i] = 5;
            yes(!f.admits(1), "text instead of head, gap" + gap);
            f = new Fixture(gap);
            f.note(120, f.bottom + gap * 2, true, false);
            yes(!f.admits(0), "adjacent staff lane, gap" + gap);
            f = new Fixture(gap);
            f.note(120, f.top + gap * 2, true, false);
            for (int i = 0; i < f.labels.length; i++) if (f.labels[i] == 2) f.gray[i] = (byte) 255;
            yes(!f.admits(0), "model-only patch without raw head, gap" + gap);
            f = new Fixture(gap);
            f.note(350, f.top + gap * 2, true, false);
            yes(!f.admits(0) && !f.admits(1), "interior head cannot extend edges, gap" + gap);
            f = new Fixture(gap);
            f.note(120, f.top + gap * 2, true, false);
            for (int i = 0; i < f.labels.length; i++)
                if (f.labels[i] == 1) {
                    f.labels[i] = 0;
                    f.gray[i] = (byte) 255;
                }
            yes(!f.admits(0), "stem erased, gap" + gap);
            f = new Fixture(gap);
            f.note(120, f.top + gap * 2, false, false);
            for (int y = f.top; y < f.bottom; y++) f.ink(160, y, (byte) 1);
            yes(!f.admits(0), "disconnected text stroke, gap" + gap);
            f = new Fixture(gap);
            f.note(630, f.top + gap, true, true);
            var t = f.track();
            yes(
                    !(boolean)
                            head.invoke(
                                    null,
                                    f.labels,
                                    f.gray,
                                    f.w,
                                    f.h,
                                    f.staff(40, 740, t),
                                    f.staff(200, 600, null),
                                    1),
                    "untracked frame refuses, gap" + gap);
        }
        Fixture blank = new Fixture(14);
        Arrays.fill(blank.labels, (byte) 0);
        Arrays.fill(blank.gray, (byte) 255);
        yes(
                NativeDecoderStages.geometry(blank.labels, blank.gray, blank.w, blank.h)
                        .measures()
                        .isEmpty(),
                "actual original pipeline blank has no staff or manufactured measures");
        Fixture unsupported = new Fixture(14);
        Arrays.fill(unsupported.labels, (byte) 0);
        Arrays.fill(unsupported.gray, (byte) 255);
        unsupported.note(120, 128, true, false);
        yes(
                NativeDecoderStages.geometry(
                                unsupported.labels, unsupported.gray, unsupported.w, unsupported.h)
                        .measures()
                        .isEmpty(),
                "head and shaft without five-rule staff cannot create geometry");
        System.out.println(
                "ORIGIN "
                        + OmrMeasurePostProcessor.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation());
        System.out.println(
                "PASS "
                        + checks
                        + " original procedural controls; helper gates are DATA fixtures, whole-pipeline blank/body controls are distinct");
    }
}
