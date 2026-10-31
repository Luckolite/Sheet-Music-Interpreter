// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original compact fixture builders. No commercial pixels or actual-score coordinates. */
public final class PrimaryRayScenes {
    record C(
            String name,
            int gap,
            int direction,
            float slope,
            String kind,
            boolean old,
            boolean next) {}

    static final List<C> CASES =
            List.of(
                    new C("aaUp", 16, 1, 0, "aa", false, true),
                    new C("aaDown", 16, -1, 0, "aa", false, true),
                    new C("aaUpSlopePlus", 16, 1, .125f, "aa", false, true),
                    new C("aaUpSlopeMinus", 16, 1, -.125f, "aa", false, true),
                    new C("aaDownSlopePlus", 16, -1, .125f, "aa", false, true),
                    new C("aaDownSlopeMinus", 16, -1, -.125f, "aa", false, true),
                    new C("aaScale2", 32, 1, .125f, "aa", false, true),
                    new C("aaDownScale2", 32, -1, -.125f, "aa", false, true),
                    new C("aaScale3", 48, 1, -.125f, "aa", false, true),
                    new C("aaInsetRounds1Core2", 10, -1, 0, "aa", false, true),
                    new C("oldNarrowCore", 16, 1, 0, "narrow", true, true),
                    new C("oldNarrowScale2", 32, -1, .125f, "narrow", true, true),
                    new C("thinInnerUp", 16, 1, .125f, "thin", false, false),
                    new C("thinInnerDownScale2", 32, -1, -.125f, "thin", false, false),
                    new C("alternatingUp", 16, 1, 0, "alternating", false, false),
                    new C("alternatingDown", 32, -1, 0, "alternating", false, false),
                    new C("brokenAA", 16, 1, 0, "broken", false, false),
                    new C("exteriorAA", 16, 1, 0, "exterior", false, false),
                    new C("unownedAA", 16, 1, 0, "unowned", false, false));

    static final class P {
        int w, h, gap, direction, inset, core, headY, base;
        int[] stem;
        float[] xs, ys;
        byte[] gray;

        int edge(int x) {
            return Math.round(base + (x - stem[0]) * currentSlope);
        }

        float currentSlope;

        void pixel(int x, int y, int v) {
            if (x >= 0 && x < w && y >= 0 && y < h) gray[y * w + x] = (byte) v;
        }
    }

    static P page(C c) {
        P p = new P();
        p.gap = c.gap;
        p.direction = c.direction;
        p.currentSlope = c.slope;
        p.w = 25 * c.gap;
        p.h = Math.round(17.5f * c.gap);
        p.gray = new byte[p.w * p.h];
        Arrays.fill(p.gray, (byte) 255);
        p.inset = Math.max(1, Math.round(c.gap * .12f));
        p.core = Math.max(2, Math.round(c.gap * .12f));
        p.headY = Math.round((c.direction > 0 ? 12.5f : 5f) * c.gap);
        p.base = Math.round((c.direction > 0 ? 6.25f : 11.25f) * c.gap);
        int first = Math.round(6.25f * c.gap);
        p.stem = new int[] {first, first + 5 * c.gap, first + 10 * c.gap};
        p.xs = new float[3];
        p.ys = new float[3];
        for (int i = 0; i < 3; i++) {
            p.xs[i] = p.stem[i] - c.direction * Math.round(.625f * c.gap);
            p.ys[i] = p.headY;
            int rx = Math.round(.6875f * c.gap), ry = Math.round(.5f * c.gap);
            for (int y = -ry; y <= ry; y++)
                for (int x = -rx; x <= rx; x++)
                    if (x * x / (double) (rx * rx) + y * y / (double) (ry * ry) <= 1)
                        p.pixel((int) p.xs[i] + x, p.headY + y, 50);
        }
        int lo = c.kind.equals("exterior") ? first - c.gap : first,
                hi = c.kind.equals("exterior") ? p.stem[2] + c.gap : p.stem[2];
        for (int x = lo; x <= hi; x++) {
            int edge = p.edge(x), start = 0, length = p.inset + p.core + 2;
            if (c.kind.equals("narrow")) length = p.core;
            if (c.kind.equals("thin")) {
                start = p.inset;
                length = 1;
            }
            if (c.kind.equals("alternating")) {
                start = x < p.stem[1] ? 0 : p.inset;
                length = p.core;
            }
            for (int d = start; d < start + length; d++) p.pixel(x, edge + c.direction * d, 35);
            if (Set.of("aa", "broken", "exterior", "unowned").contains(c.kind))
                p.pixel(x, edge, 180);
        }
        // Unchanged endpoint coordinates and one real physical shaft per supplied head.
        for (int x : p.stem)
            for (int y = Math.min(p.headY, p.edge(x)); y <= Math.max(p.headY, p.edge(x)); y++)
                p.pixel(x, y, 35);
        if (c.kind.equals("broken")) {
            int cut = (first + p.stem[1]) / 2;
            for (int x = cut - 4; x <= cut + 4; x++)
                for (int d = -1; d <= p.inset + p.core + 3; d++)
                    p.pixel(x, p.edge(x) + c.direction * d, 255);
        }
        if (c.kind.equals("unowned")) {
            int x = (first + p.stem[1]) / 2;
            for (int y = Math.min(p.headY, p.edge(x)); y <= Math.max(p.headY, p.edge(x)); y++)
                p.pixel(x, y, 35);
        }
        return p;
    }

    static String metrics(P p) {
        var stems = new ArrayList<PairedTremoloInk.Stem>();
        for (int i = 0; i < 3; i++) {
            var s = PairedTremoloInk.stem(p.gray, p.w, p.h, p.xs[i], p.ys[i], p.gap, p.direction);
            if (s == null
                    || s.x() != p.stem[i]
                    || s.headY() != p.headY
                    || s.endY() != p.edge(p.stem[i]))
                throw new AssertionError("fixture source stem mismatch " + s);
            stems.add(s);
        }
        boolean bfs = true;
        for (int i = 1; i < 3; i++) {
            var a = stems.get(i - 1);
            var b = stems.get(i);
            bfs &=
                    BeamInkConnectivity.connected(
                            p.gray,
                            p.w,
                            p.h,
                            a.x(),
                            a.endY() + p.direction * p.inset,
                            b.x(),
                            b.endY() + p.direction * p.inset,
                            p.gap);
        }
        var a = stems.get(0);
        var b = stems.get(2);
        float slope = (b.endY() - a.endY()) / (float) (b.x() - a.x());
        int total = 0, legacy = 0, inner = 0, union = 0;
        for (int x = a.x() + 2; x < b.x() - 1; x++) {
            int edge = Math.round(a.endY() + (x - a.x()) * slope);
            boolean old = true, next = true;
            total++;
            for (int d = 0; d < p.core; d++) {
                int y = edge + p.direction * d, yy = edge + p.direction * (p.inset + d);
                old &= y >= 0 && y < p.h && (p.gray[y * p.w + x] & 255) < 165;
                next &= yy >= 0 && yy < p.h && (p.gray[yy * p.w + x] & 255) < 165;
            }
            if (old) legacy++;
            if (next) inner++;
            if (old || next) union++;
        }
        return "{\"total\":"
                + total
                + ",\"legacy\":"
                + legacy
                + ",\"inset\":"
                + inner
                + ",\"union\":"
                + union
                + ",\"bfs\":"
                + bfs
                + "}";
    }

    static boolean read(P p) {
        var before = p.gray.clone();
        boolean r = CompleteBeamGroupInk.proves(p.gray, p.w, p.h, p.xs, p.ys, p.gap, p.direction);
        if (!Arrays.equals(before, p.gray)) throw new AssertionError("gray mutation");
        return r;
    }

    static final List<String> CONSUMERS =
            List.of(
                    "aaValid",
                    "narrow",
                    "missingHeader",
                    "ambiguousHeader",
                    "conflictMeter",
                    "unownedHead",
                    "unownedShaft",
                    "tie",
                    "companionFull",
                    "companionAA",
                    "companionMissing",
                    "companionLiteral",
                    "companionWrongCount");

    static boolean expectedConsumer(String mode, boolean candidate) {
        return mode.equals("narrow")
                || mode.equals("companionFull")
                || candidate && (mode.equals("aaValid") || mode.equals("companionAA"));
    }

    static ScorePageInterpretation consumer(String mode) {
        int w = OpeningBeamPickupTest.W,
                h = OpeningBeamPickupTest.H,
                count = mode.startsWith("companion") ? 2 : 1;
        var gray = new byte[w * h];
        Arrays.fill(gray, (byte) 255);
        var labels = new byte[w * h];
        boolean aa = Set.of("aaValid", "companionAA", "unownedShaft").contains(mode);
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int i = 0; i < 4; i++) {
            int x = OpeningBeamPickupTest.STEMS[i];
            OpeningBeamPickupTest.box(gray, x - 1, 90, x + 1, 172, 0);
            OpeningBeamPickupTest.box(gray, x + 1, 86, x + 15, 94, 0);
            OpeningBeamPickupTest.box(labels, x + 1, 86, x + 15, 94, 2);
            notes.add(
                    new ScoreNoteEvent(
                                    0,
                                    (x + 8 - 80) / 210f,
                                    0,
                                    0,
                                    count,
                                    90f / h,
                                    mode.equals("tie") && i == 0,
                                    0,
                                    i == 1 || i == 2 ? 2 : 1,
                                    2,
                                    0)
                            .withStemDirection(-1));
        }
        OpeningBeamPickupTest.box(gray, 99, mode.equals("narrow") ? 171 : 168, 251, 172, 0);
        if (aa)
            for (int x = 102; x <= 248; x++) {
                boolean own = false;
                for (int shaft : OpeningBeamPickupTest.STEMS)
                    if (Math.abs(x - shaft) <= 1) own = true;
                if (!own) gray[172 * w + x] = (byte) 180;
            }
        if (mode.equals("unownedHead")) OpeningBeamPickupTest.box(labels, 270, 120, 274, 124, 2);
        if (mode.equals("unownedShaft")) OpeningBeamPickupTest.box(gray, 124, 90, 125, 172, 0);
        var rests = new ArrayList<ScoreRestEvent>();
        if (count == 2 && !mode.equals("companionMissing")) {
            for (int y : new int[] {200, 216, 232, 248, 264})
                OpeningBeamPickupTest.box(gray, 80, y, 290, y, 60);
            OpeningBeamPickupTest.box(gray, 180, 232, 196, 236, 35);
            if (mode.equals("companionLiteral"))
                rests.add(new ScoreRestEvent(0, .5f, 232f / h, .02f, 1, 2, 4));
            else
                rests.add(
                        ScoreRestEvent.fullMeasure(
                                0,
                                .5f,
                                232f / h,
                                .02f,
                                1,
                                mode.equals("companionWrongCount") ? 1 : 2));
        }
        var score =
                new ScorePageInterpretation(
                        OpeningBeamPickupTest.REGIONS,
                        notes,
                        1,
                        List.of(),
                        List.of(),
                        OpeningBeamPickupTest.METERS,
                        rests);
        List<MeterChangeDetector.Crop> headers =
                mode.equals("missingHeader")
                        ? List.of()
                        : mode.equals("ambiguousHeader")
                                ? List.of(
                                        OpeningBeamPickupTest.HEADERS.get(0),
                                        new MeterChangeDetector.Crop(70, 97, 91, 167, 100, 16))
                                : OpeningBeamPickupTest.HEADERS;
        var meters =
                mode.equals("conflictMeter")
                        ? List.of(new ScoreMeterChange(0, 12, 8), new ScoreMeterChange(0, 4, 4))
                        : OpeningBeamPickupTest.METERS;
        var beforeG = gray.clone();
        var beforeL = labels.clone();
        var out =
                OpeningBeamPickup.apply(
                        score,
                        labels,
                        gray,
                        w,
                        h,
                        List.of(new PlayingTechniqueDetector.Staff(100, 164, 16, 0, count)),
                        meters,
                        headers);
        if (!Arrays.equals(beforeG, gray) || !Arrays.equals(beforeL, labels))
            throw new AssertionError("consumer inputs mutated");
        boolean accepted = out != score;
        double beats = ScoreOpeningDuration.provedQuarterBeats(out, 6);
        if (accepted && (!Double.isFinite(beats) || Math.abs(beats - 1.5) > .0001))
            throw new AssertionError("consumer wrong actualclock");
        return accepted ? out : null;
    }
}
