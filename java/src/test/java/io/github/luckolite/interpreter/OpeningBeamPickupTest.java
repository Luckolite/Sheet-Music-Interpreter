// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class OpeningBeamPickupTest {
    @org.junit.Test
    public void proceduralControls() {
        main(new String[0]);
    }

    static final int W = 1200, H = 300;
    static final int[] STEMS = {100, 150, 200, 250};
    static final List<MeasureRegion> REGIONS =
            List.of(
                    new MeasureRegion(80f / W, 290f / W, .1f, .9f),
                    new MeasureRegion(310f / W, 720f / W, .1f, .9f),
                    new MeasureRegion(740f / W, 1180f / W, .1f, .9f));
    static final List<ScoreMeterChange> METERS = List.of(new ScoreMeterChange(0, 12, 8));
    static final List<PlayingTechniqueDetector.Staff> STAFFS =
            List.of(new PlayingTechniqueDetector.Staff(100, 164, 16, 0, 1));
    static final List<MeterChangeDetector.Crop> HEADERS =
            List.of(new MeterChangeDetector.Crop(70, 97, 90, 167, 100, 16));

    static void box(byte[] g, int l, int t, int r, int b, int color) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) g[y * W + x] = (byte) color;
    }

    static ScorePageInterpretation score(int omit) {
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < 4; i++)
            if (i != omit)
                notes.add(
                        new ScoreNoteEvent(
                                        0,
                                        (STEMS[i] + 8 - 80) / 210f,
                                        0,
                                        0,
                                        1,
                                        90f / H,
                                        false,
                                        0,
                                        i == 1 || i == 2 ? 2 : 1,
                                        2,
                                        0)
                                .withStemDirection(-1));
        return new ScorePageInterpretation(
                REGIONS, notes, 1, List.of(), List.of(), METERS, List.of());
    }

    static double run(String mode, int omit) {
        var g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        var labels = new byte[W * H];
        for (int x : STEMS) {
            box(g, x - 1, 90, x + 1, 170, 0);
            box(g, x + 1, 86, x + 15, 94, 0);
            box(labels, x + 1, 86, x + 15, 94, 2);
        }
        box(g, 99, 168, 251, 172, 0);
        if (mode.equals("unowned")) box(labels, 270, 120, 274, 124, 2);
        if (mode.equals("broken")) box(g, 170, 168, 180, 172, 255);
        var meters =
                mode.equals("conflict")
                        ? List.of(new ScoreMeterChange(0, 12, 8), new ScoreMeterChange(0, 4, 4))
                        : METERS;
        var s = score(omit);
        var out =
                OpeningBeamPickup.apply(
                        s,
                        labels,
                        g,
                        W,
                        H,
                        STAFFS,
                        meters,
                        mode.equals("noheader") ? List.of() : HEADERS);
        if (!mode.equals("valid") || omit >= 0) {
            if (out != s) throw new AssertionError("marked " + mode + " omit=" + omit);
            return Double.NaN;
        }
        if (out == s) throw new AssertionError("valid source not marked");
        return ScoreOpeningDuration.provedQuarterBeats(out, 6);
    }

    public static void main(String[] args) {
        if (Math.abs(run("valid", -1) - 1.5) > .0001) throw new AssertionError("wrong clock");
        int passed = 1;
        for (String mode : List.of("unowned", "broken", "conflict", "noheader")) {
            run(mode, -1);
            passed++;
        }
        for (int i = 0; i < 4; i++) {
            run("valid", i);
            passed++;
        }
        System.out.println("PASS " + passed + " synthetic pickup ownership and timing controls");
    }
}
