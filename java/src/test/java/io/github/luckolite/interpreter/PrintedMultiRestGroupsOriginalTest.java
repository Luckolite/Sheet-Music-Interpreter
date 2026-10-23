// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.security.MessageDigest;
import org.junit.Test;
import static org.junit.Assert.*;

/** Procedural synthetic staff/H/count/ordinary-note scenes; no score library data. */
public final class PrintedMultiRestGroupsOriginalTest {
    private static final int W = 1000, H = 400;
    private static final int[][] RECTS = {
        {80, 120, 81, 120, 1, 90},
        {82, 120, 739, 120, 4, 110},
        {740, 120, 741, 120, 1, 90},
        {742, 120, 919, 120, 4, 110},
        {920, 120, 921, 120, 1, 90},
        {80, 121, 81, 126, 1, 90},
        {740, 121, 741, 126, 1, 90},
        {920, 121, 921, 126, 1, 90},
        {80, 127, 81, 133, 1, 90},
        {740, 127, 741, 133, 1, 90},
        {814, 127, 815, 133, 1, 30},
        {920, 127, 921, 133, 1, 90},
        {80, 134, 81, 135, 1, 90},
        {240, 134, 242, 135, 5, 50},
        {648, 134, 650, 135, 5, 50},
        {740, 134, 741, 135, 1, 90},
        {814, 134, 815, 135, 1, 30},
        {920, 134, 921, 135, 1, 90},
        {80, 136, 81, 136, 1, 90},
        {82, 136, 239, 136, 4, 110},
        {240, 136, 242, 136, 5, 50},
        {243, 136, 647, 136, 4, 110},
        {648, 136, 650, 136, 5, 50},
        {651, 136, 739, 136, 4, 110},
        {740, 136, 741, 136, 1, 90},
        {742, 136, 813, 136, 4, 110},
        {814, 136, 815, 136, 1, 30},
        {816, 136, 919, 136, 4, 110},
        {920, 136, 921, 136, 1, 90},
        {80, 137, 81, 147, 1, 90},
        {240, 137, 242, 147, 5, 50},
        {648, 137, 650, 147, 5, 50},
        {740, 137, 741, 147, 1, 90},
        {814, 137, 815, 147, 1, 30},
        {920, 137, 921, 147, 1, 90},
        {80, 148, 81, 151, 1, 90},
        {240, 148, 650, 151, 5, 50},
        {740, 148, 741, 151, 1, 90},
        {814, 148, 815, 151, 1, 30},
        {920, 148, 921, 151, 1, 90},
        {80, 152, 81, 152, 1, 90},
        {82, 152, 239, 152, 4, 110},
        {240, 152, 650, 152, 5, 50},
        {651, 152, 739, 152, 4, 110},
        {740, 152, 741, 152, 1, 90},
        {742, 152, 813, 152, 4, 110},
        {814, 152, 815, 152, 1, 30},
        {816, 152, 919, 152, 4, 110},
        {920, 152, 921, 152, 1, 90},
        {80, 153, 81, 154, 1, 90},
        {240, 153, 650, 154, 5, 50},
        {740, 153, 741, 154, 1, 90},
        {814, 153, 815, 154, 1, 30},
        {920, 153, 921, 154, 1, 90},
        {80, 155, 81, 155, 1, 90},
        {240, 155, 650, 155, 5, 50},
        {740, 155, 741, 155, 1, 90},
        {805, 155, 810, 155, 2, 30},
        {814, 155, 815, 155, 1, 30},
        {920, 155, 921, 155, 1, 90},
        {80, 156, 81, 156, 1, 90},
        {240, 156, 650, 156, 5, 50},
        {740, 156, 741, 156, 1, 90},
        {803, 156, 812, 156, 2, 30},
        {814, 156, 815, 156, 1, 30},
        {920, 156, 921, 156, 1, 90},
        {80, 157, 81, 158, 1, 90},
        {240, 157, 242, 158, 5, 50},
        {648, 157, 650, 158, 5, 50},
        {740, 157, 741, 158, 1, 90},
        {801, 157, 813, 158, 2, 30},
        {814, 157, 815, 158, 1, 30},
        {920, 157, 921, 158, 1, 90},
        {80, 159, 81, 160, 1, 90},
        {240, 159, 242, 160, 5, 50},
        {648, 159, 650, 160, 5, 50},
        {740, 159, 741, 160, 1, 90},
        {800, 159, 813, 160, 2, 30},
        {814, 159, 815, 160, 1, 30},
        {920, 159, 921, 160, 1, 90},
        {80, 161, 81, 161, 1, 90},
        {240, 161, 242, 161, 5, 50},
        {648, 161, 650, 161, 5, 50},
        {740, 161, 741, 161, 1, 90},
        {800, 161, 815, 161, 2, 30},
        {920, 161, 921, 161, 1, 90},
        {80, 162, 81, 163, 1, 90},
        {240, 162, 242, 163, 5, 50},
        {648, 162, 650, 163, 5, 50},
        {740, 162, 741, 163, 1, 90},
        {801, 162, 814, 163, 2, 30},
        {920, 162, 921, 163, 1, 90},
        {80, 164, 81, 164, 1, 90},
        {240, 164, 242, 164, 5, 50},
        {648, 164, 650, 164, 5, 50},
        {740, 164, 741, 164, 1, 90},
        {803, 164, 812, 164, 2, 30},
        {920, 164, 921, 164, 1, 90},
        {80, 165, 81, 165, 1, 90},
        {240, 165, 242, 165, 5, 50},
        {648, 165, 650, 165, 5, 50},
        {740, 165, 741, 165, 1, 90},
        {805, 165, 810, 165, 2, 30},
        {920, 165, 921, 165, 1, 90},
        {80, 166, 81, 167, 1, 90},
        {240, 166, 242, 167, 5, 50},
        {648, 166, 650, 167, 5, 50},
        {740, 166, 741, 167, 1, 90},
        {920, 166, 921, 167, 1, 90},
        {80, 168, 81, 168, 1, 90},
        {82, 168, 239, 168, 4, 110},
        {240, 168, 242, 168, 5, 50},
        {243, 168, 647, 168, 4, 110},
        {648, 168, 650, 168, 5, 50},
        {651, 168, 739, 168, 4, 110},
        {740, 168, 741, 168, 1, 90},
        {742, 168, 919, 168, 4, 110},
        {920, 168, 921, 168, 1, 90},
        {80, 169, 81, 170, 1, 90},
        {240, 169, 242, 170, 5, 50},
        {648, 169, 650, 170, 5, 50},
        {740, 169, 741, 170, 1, 90},
        {920, 169, 921, 170, 1, 90},
        {80, 171, 81, 183, 1, 90},
        {740, 171, 741, 183, 1, 90},
        {920, 171, 921, 183, 1, 90},
        {80, 184, 81, 184, 1, 90},
        {82, 184, 739, 184, 4, 110},
        {740, 184, 741, 184, 1, 90},
        {742, 184, 919, 184, 4, 110},
        {920, 184, 921, 184, 1, 90}
    };

    private record Pixels(byte[] labels, byte[] gray) {}

    private record Scene(
            Pixels pixels,
            SelectedMeasureOrigins physical,
            RecognizedMultiRestOrigins origins,
            List<MeasureRegion> raw,
            List<MeasureRegion> measures,
            OmrScoreInterpreter.Analysis score,
            List<MeasureNumberReconciler.NumberToken> counts) {}

    private static void ink(Pixels p, int x, int y) {
        p.labels[y * W + x] = 5;
        p.gray[y * W + x] = 0;
    }

    private static Pixels pixels(int count, String kind) throws Exception {
        Pixels p = new Pixels(new byte[W * H], new byte[W * H]);
        Arrays.fill(p.gray, (byte) 255);
        for (int[] r : RECTS)
            for (int y = r[1]; y <= r[3]; y++)
                for (int x = r[0]; x <= r[2]; x++) {
                    p.labels[y * W + x] = (byte) r[4];
                    p.gray[y * W + x] = (byte) r[5];
                }
        assertEquals(
                "ecfa6c9cb2bdbcaa549dc2bbd13510d61e4ff4f56c1027c92e21f3309c1526d4",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(p.gray)));
        assertEquals(
                "09b9fdc513800a656f1d126ef8dff06a8191dc7bc7f7a466a5e48110ad2f724f",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(p.labels)));
        if (!kind.equals("no-count")) {
            if (count == 4) {
                for (int y = 85; y < 109; y++) ink(p, 450, y);
                for (int d = 0; d < 12; d++) ink(p, 438 + d, 85 + d);
                for (int x = 438; x <= 450; x++) ink(p, x, 97);
            } else {
                for (int x = 437; x <= 451; x++) {
                    ink(p, x, 84);
                    ink(p, x, 108);
                }
                for (int y = 85; y < 92; y++) ink(p, 451, y);
                for (int d = 0; d < 15; d++) ink(p, 451 - d, 93 + d);
            }
        }
        if (kind.equals("barless"))
            for (int y = 132; y < 172; y++)
                for (int x = 238; x < 653; x++)
                    if (y != 136 && y != 152 && y != 168) {
                        p.labels[y * W + x] = 0;
                        p.gray[y * W + x] = (byte) 255;
                    }
        if (kind.equals("written")) {
            for (int y = 145; y < 154; y++)
                for (int x = 302; x < 315; x++)
                    if (Math.pow((x - 308) / 6.0, 2) + Math.pow((y - 149) / 4.0, 2) <= 1) {
                        p.labels[y * W + x] = 2;
                        p.gray[y * W + x] = 0;
                    }
            for (int y = 118; y < 150; y++) {
                p.labels[y * W + 314] = 1;
                p.gray[y * W + 314] = 0;
            }
        }
        return p;
    }

    private static Scene scene(int count, String kind, boolean standalone, boolean foreign)
            throws Exception {
        Pixels p = pixels(count, kind);
        var physical = new SelectedMeasureOrigins();
        var raw = OmrMeasurePostProcessor.process(p.labels, p.gray, W, H, p.labels, physical);
        physical.prepared(p.labels);
        var origins = new RecognizedMultiRestOrigins(physical);
        var token =
                foreign
                        ? new MeasureNumberReconciler.NumberToken(count, .815f, .21f, .83f, .2725f)
                        : new MeasureNumberReconciler.NumberToken(
                                count, .437f, .21f, .452f, .2725f);
        var counts =
                MultiMeasureRestDetector.detect(
                        p.labels,
                        p.gray,
                        W,
                        H,
                        raw,
                        standalone ? List.of() : List.of(token),
                        origins);
        var expanded = MeasureNumberReconciler.reconcile(raw, List.of(), counts, p.labels, W, H);
        origins.expanded(raw, expanded);
        var measures = PrintedMeasureRhythmGuard.reconcile(raw, expanded, p.labels, p.gray, W, H);
        var score = OmrScoreInterpreter.analyze(p.labels, p.gray, W, H, measures);
        return new Scene(p, physical, origins, raw, measures, score, counts);
    }

    private static OmrScoreInterpreter.Analysis apply(Scene s) {
        return run(s, s.score, s.measures, s.pixels.labels, s.pixels.gray, s.origins);
    }

    private static OmrScoreInterpreter.Analysis run(
            Scene s,
            OmrScoreInterpreter.Analysis score,
            List<MeasureRegion> m,
            byte[] l,
            byte[] g,
            RecognizedMultiRestOrigins origins) {
        return RecognizedMultiRestProjection.apply(score, m, l, g, W, H, origins);
    }

    private static OmrScoreInterpreter.Analysis positive(Scene s, int n) {
        assertTrue(s.counts.stream().anyMatch(t -> t.value() == n));
        assertTrue(s.measures.size() > n);
        for (int i = 1; i < n; i++) assertSame(s.measures.get(0), s.measures.get(i));
        assertTrue(s.score.notes().stream().anyMatch(note -> note.measureIndex() >= n));
        byte[] l = s.pixels.labels.clone(), g = s.pixels.gray.clone();
        var result = apply(s);
        assertEquals(s.score.notes(), result.notes());
        assertEquals(s.score.keyChanges(), result.keyChanges());
        assertEquals(s.score.tremoloSource(), result.tremoloSource());
        for (int i = 0; i < s.score.notes().size(); i++)
            assertSame(s.score.notes().get(i), result.notes().get(i));
        for (var old : s.score.rests()) assertTrue(result.rests().stream().anyMatch(r -> r == old));
        assertEquals(
                n,
                result.rests().stream()
                        .filter(r -> r.measureIndex() < n && r.isFullMeasure())
                        .count());
        for (var r : result.rests())
            if (r.measureIndex() < n) {
                assertEquals(ScoreRestEvent.Kind.FULL_MEASURE, r.kind());
                assertEquals(4, r.durationBeats(), 0);
                assertEquals(3, r.resolvedDurationBeats(3), 0);
            }
        assertArrayEquals(l, s.pixels.labels);
        assertArrayEquals(g, s.pixels.gray);
        return result;
    }

    private static List<ScoreMultiRestGroup> printed(Scene s) {
        return PrintedMultiRestGroups.capture(
                apply(s), s.measures, s.pixels.labels, s.pixels.gray, W, H, s.origins);
    }

    @Test
    public void recognizedFourBarHRestKeepsOnePrintedGroup() throws Exception {
        var s = scene(4, "plain", false, false);
        assertEquals(4, s.counts.get(0).value());
        assertEquals(List.of(new ScoreMultiRestGroup(0, 4, 0, 1)), printed(s));
    }

    @Test
    public void ordinaryFourFullRestsCannotMintPrintedGroup() throws Exception {
        var s = scene(4, "no-count", false, true);
        assertTrue(s.counts.isEmpty());
        var rests = new ArrayList<ScoreRestEvent>();
        for (int i = 0; i < 4; i++) rests.add(ScoreRestEvent.fullMeasure(i, .5f, .4f, .04f, 0, 1));
        var m = Collections.nCopies(4, s.measures.get(0));
        var score = new OmrScoreInterpreter.Analysis(List.of(), List.of(), rests);
        assertTrue(
                PrintedMultiRestGroups.capture(
                                score, m, s.pixels.labels, s.pixels.gray, W, H, s.origins)
                        .isEmpty());
    }

    @Test
    public void wrongExpansionOrderCannotRetainGroup() throws Exception {
        var s = scene(4, "plain", false, false);
        assertEquals(1, printed(s).size());
        var m = new ArrayList<>(s.measures);
        m.add(0, m.remove(m.size() - 1));
        assertTrue(
                PrintedMultiRestGroups.capture(
                                apply(s), m, s.pixels.labels, s.pixels.gray, W, H, s.origins)
                        .isEmpty());
    }
}
