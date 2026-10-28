// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

/** Exercise actual private producer evidence, never a certificate reconstructed from values. */
public final class OwnedDistantCaretEvidenceTest {
    record Captured(
            OwnedDistantCaretOriginalTest.Scene scene,
            ScorePageInterpretation score,
            PrintedBeamSource.CaretEvidence evidence,
            PrintedBeamSource.Capture result) {}

    private static PrintedBeamSource.CaretEvidence empty(
            OwnedDistantCaretOriginalTest.Scene s, ScorePageInterpretation score) throws Exception {
        var ctor =
                PrintedBeamSource.CaretEvidence.class.getDeclaredConstructor(
                        ScorePageInterpretation.class,
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class,
                        long.class,
                        long.class,
                        long.class,
                        long.class,
                        long.class,
                        long.class);
        assertTrue(Modifier.isPrivate(ctor.getModifiers()));
        ctor.setAccessible(true);
        // This creates an empty bookkeeping scope only. It supplies no shaft,
        // head, strip, contact, source port, or other positive evidence.
        return ctor.newInstance(
                score,
                s.labels,
                s.raw,
                OwnedDistantCaretOriginalTest.W,
                OwnedDistantCaretOriginalTest.H,
                0,
                1_000_000L,
                1_000_000L,
                512_000_000L,
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                Long.MAX_VALUE);
    }

    private static Captured actual() throws Exception {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        var score = s.score();
        var evidence = empty(s, score);
        var method =
                PrintedBeamSource.class.getDeclaredMethod(
                        "capture",
                        ScorePageInterpretation.class,
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        int.class,
                        long.class,
                        long.class,
                        long.class,
                        PrintedBeamSource.CaretEvidence.class);
        method.setAccessible(true);
        var result =
                (PrintedBeamSource.Capture)
                        method.invoke(
                                null,
                                score,
                                s.labels,
                                s.raw,
                                OwnedDistantCaretOriginalTest.W,
                                OwnedDistantCaretOriginalTest.H,
                                s.staffs,
                                0,
                                8_000_000L,
                                256_000_000L,
                                64_000_000L,
                                evidence);
        assertEquals("", result.statistics().refusal());
        assertEquals(1, result.source().groups().size());
        assertTrue(
                evidence.inputs(
                        s.labels,
                        s.raw,
                        OwnedDistantCaretOriginalTest.W,
                        OwnedDistantCaretOriginalTest.H,
                        evidence.anchors()));
        assertTrue(evidence.proves(0, evidence.anchors().get(0), 188, 249, 212, 262));
        return new Captured(s, score, evidence, result);
    }

    private static ScorePageInterpretation consume(
            Captured c,
            ScorePageInterpretation score,
            byte[] labels,
            byte[] raw,
            int width,
            int height,
            int page)
            throws Exception {
        var m =
                PrintedBeamSource.CaretEvidence.class.getDeclaredMethod(
                        "apply",
                        ScorePageInterpretation.class,
                        byte[].class,
                        byte[].class,
                        int.class,
                        int.class,
                        int.class);
        m.setAccessible(true);
        return (ScorePageInterpretation)
                m.invoke(c.evidence, score, labels, raw, width, height, page);
    }

    @Test
    public void anEmptyBookHasNoAuthorityEvenWithValidSourceValues() throws Exception {
        var s = new OwnedDistantCaretOriginalTest.Scene();
        var score = s.score();
        var e = empty(s, score);
        assertFalse(e.proves(0, s.anchors().get(0), 188, 249, 212, 262));
        assertFalse(
                e.inputs(
                        s.labels,
                        s.raw,
                        OwnedDistantCaretOriginalTest.W,
                        OwnedDistantCaretOriginalTest.H,
                        s.anchors()));
    }

    @Test
    public void actualProducerEvidenceRejectsCopiedRawAndLabels() throws Exception {
        var c = actual();
        var s = c.scene;
        var e = c.evidence;
        assertFalse(
                e.inputs(
                        s.labels,
                        s.raw.clone(),
                        OwnedDistantCaretOriginalTest.W,
                        OwnedDistantCaretOriginalTest.H,
                        e.anchors()));
        assertFalse(
                e.inputs(
                        s.labels.clone(),
                        s.raw,
                        OwnedDistantCaretOriginalTest.W,
                        OwnedDistantCaretOriginalTest.H,
                        e.anchors()));
    }

    @Test
    public void actualProducerEvidenceRejectsCopiedAnchorValuesAndList() throws Exception {
        var c = actual();
        var e = c.evidence;
        var s = c.scene;
        var a = e.anchors().get(0);
        assertFalse(
                e.proves(
                        0,
                        new NoteArticulationDetector.Anchor(
                                a.x(), a.y(), a.gap(), a.staff(), a.track()),
                        188,
                        249,
                        212,
                        262));
        assertFalse(
                e.inputs(
                        s.labels,
                        s.raw,
                        OwnedDistantCaretOriginalTest.W,
                        OwnedDistantCaretOriginalTest.H,
                        new ArrayList<>(e.anchors())));
    }

    @Test
    public void actualProducerEvidenceRejectsCopiedScoreAndDifferentPage() throws Exception {
        var c = actual();
        var s = c.scene;
        var copy = new ScorePageInterpretation(c.score.measures(), c.score.notes());
        assertNotSame(c.score, copy);
        assertSame(
                copy,
                consume(
                        c,
                        copy,
                        s.labels,
                        s.raw,
                        OwnedDistantCaretOriginalTest.W,
                        OwnedDistantCaretOriginalTest.H,
                        0));
        assertSame(
                c.score,
                consume(
                        c,
                        c.score,
                        s.labels,
                        s.raw,
                        OwnedDistantCaretOriginalTest.W,
                        OwnedDistantCaretOriginalTest.H,
                        1));
    }

    @Test
    public void actualProducerEvidenceCannotCrossThreads() throws Exception {
        var c = actual();
        var s = c.scene;
        AtomicBoolean authorized = new AtomicBoolean(true);
        var t =
                new Thread(
                        () ->
                                authorized.set(
                                        c.evidence.inputs(
                                                s.labels,
                                                s.raw,
                                                OwnedDistantCaretOriginalTest.W,
                                                OwnedDistantCaretOriginalTest.H,
                                                c.evidence.anchors())));
        t.start();
        t.join();
        assertFalse(authorized.get());
    }

    @Test
    public void anActualCapturedBookCanCommitOnlyOnce() throws Exception {
        var c = actual();
        var s = c.scene;
        var first =
                consume(
                        c,
                        c.score,
                        s.labels,
                        s.raw,
                        OwnedDistantCaretOriginalTest.W,
                        OwnedDistantCaretOriginalTest.H,
                        0);
        assertEquals(NoteArticulation.MARCATO, first.notes().get(0).articulations());
        assertSame(
                c.score,
                consume(
                        c,
                        c.score,
                        s.labels,
                        s.raw,
                        OwnedDistantCaretOriginalTest.W,
                        OwnedDistantCaretOriginalTest.H,
                        0));
    }

    @Test
    public void reducedOptionalBudgetsKeepAllLegacySourceAndFields() {
        for (int kind = 0; kind < 6; kind++) {
            var s = new OwnedDistantCaretOriginalTest.Scene();
            var score = s.score();
            var old = s.source().source();
            long[] caps = {
                1_000_000, 1_000_000, 512_000_000, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE
            };
            caps[kind] = 0;
            var after =
                    PrintedBeamSource.applyWithCaretLimits(
                            score,
                            s.labels,
                            s.raw,
                            OwnedDistantCaretOriginalTest.W,
                            OwnedDistantCaretOriginalTest.H,
                            s.staffs,
                            0,
                            caps[0],
                            caps[1],
                            caps[2],
                            caps[3],
                            caps[4],
                            caps[5]);
            assertEquals("kind=" + kind, score.withBeamSource(old), after);
        }
    }
}
