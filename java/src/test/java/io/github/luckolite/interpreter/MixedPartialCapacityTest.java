// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural row with one false head cut and an independent faint real separator. */
public class MixedPartialCapacityTest {
    static final int W = 1000, H = 300;
    final byte[] gray = new byte[W * H], labels = new byte[W * H];

    static MeasureRegion bar(float l, float r) {
        return new MeasureRegion(l, r, .2f, .8f);
    }

    final List<MeasureRegion> raw =
            List.of(
                    bar(.08f, .24f),
                    bar(.25f, .40f),
                    bar(.41f, .59f),
                    bar(.60f, .77f),
                    bar(.78f, .94f));
    List<MeasureRegion> fitted =
            List.of(
                    bar(.08f, .158f),
                    bar(.162f, .24f),
                    raw.get(1),
                    bar(.41f, .498f),
                    bar(.502f, .59f),
                    raw.get(3),
                    raw.get(4));
    final List<ScoreNoteEvent> notes = new ArrayList<>();

    public MixedPartialCapacityTest() {
        Arrays.fill(gray, (byte) 255);
        for (int y = 100; y <= 156; y += 14) for (int x = 70; x <= 950; x++) gray[y * W + x] = 0;
        for (int x : new int[] {245, 405, 595, 775})
            for (int y = 100; y <= 156; y++) gray[y * W + x] = 0;
        for (int m = 0; m < raw.size(); m++) {
            int count = m == 2 ? 6 : 3;
            for (int i = 0; i < count; i++) note(m, (i + .5f) / count, 1, 0, 0);
        }
        for (int y = 100; y <= 156; y++) gray[y * W + 500] = (byte) 218;
    }

    void note(int m, float p, float duration, int beams, float tail) {
        var r = raw.get(m);
        int x = Math.round((r.left() + p * (r.right() - r.left())) * W),
                cy = 114 + 14 * ((int) Math.floor(p * 6) % 3);
        for (int y = cy - 4; y <= cy + 4; y++)
            for (int xx = x - 7; xx <= x + 7; xx++)
                if ((xx - x) * (xx - x) / 49.0 + (y - cy) * (y - cy) / 16.0 <= 1) {
                    gray[y * W + xx] = 0;
                    labels[y * W + xx] = OmrMeasurePostProcessor.NOTEHEAD;
                }
        for (int y = 105; y <= cy; y++) {
            gray[y * W + x + 7] = 0;
            labels[y * W + x + 7] = OmrMeasurePostProcessor.STEM_OR_REST;
        }
        notes.add(
                new ScoreNoteEvent(
                        m,
                        p,
                        (156 - cy) / 7,
                        0,
                        1,
                        cy / (float) H,
                        false,
                        0,
                        beams,
                        2,
                        duration,
                        1,
                        tail,
                        0,
                        30,
                        false,
                        0,
                        false,
                        0,
                        0,
                        1,
                        1));
    }

    List<MeasureRegion> result() {
        var before = PrintedMeasureRhythmGuard.preserveCountNeutralRows(raw, fitted, gray, W, H);
        return PrintedMeasureRhythmGuard.preservePartialWrittenBars(
                raw, before, notes, List.of(), gray, W, H);
    }

    List<MeasureRegion> mixedExpected() {
        return List.of(
                raw.get(0), raw.get(1), fitted.get(3), fitted.get(4), raw.get(3), raw.get(4));
    }

    @Test
    public void badHeadCutCannotEraseIndependentWeakBarline() throws Exception {
        assertEquals(mixedExpected(), result());
    }

    @Test
    public void independentlyWrittenDoubleCapacityRemainsSplit() throws Exception {
        fitted =
                List.of(
                        raw.get(0),
                        raw.get(1),
                        fitted.get(3),
                        fitted.get(4),
                        raw.get(3),
                        raw.get(4));
        assertEquals(
                "A 6-quarter rawspan must remain two bars against 3-quarter references",
                fitted,
                result());
    }

    @Test
    public void conflictingUntouchedNeighborCapacityKeepsFitting() throws Exception {
        notes.removeIf(n -> n.measureIndex() == 4);
        note(4, 1f / 6, 2, 0, 0);
        note(4, .5f, 1, 0, 0);
        note(4, 5f / 6, 1, 0, 0);
        assertEquals(
                "Two agreeing neighbors cannot hide an independently known different capacity",
                fitted,
                result());
    }

    @Test
    public void sourceRasterAndSemanticLabelsRemainImmutable() throws Exception {
        byte[] g = gray.clone(), l = labels.clone();
        result();
        assertArrayEquals(g, gray);
        assertArrayEquals(l, labels);
    }

    @Test
    public void coincidentDifferentDurationsDoNotProveSingleVoiceNeighbors() throws Exception {
        for (int m : new int[] {1, 3, 4}) {
            final int at = m;
            notes.removeIf(n -> n.measureIndex() == at);
            note(m, .25f, 2, 0, 0);
            note(m, .25f, 1, 0, 0);
            note(m, .65f, 1, 0, 0);
        }
        assertEquals(
                "A held half plus an independent quarter pair is ambiguous, even with same-direction stems",
                fitted,
                result());
    }
}
