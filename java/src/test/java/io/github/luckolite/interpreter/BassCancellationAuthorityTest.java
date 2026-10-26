// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original procedural bass-clef cancellation evidence; no source score or model. */
public final class BassCancellationAuthorityTest {
    static final int W = 320, H = 200;

    static Class<?> nested(String name) throws Exception {
        return Class.forName(OmrScoreInterpreter.class.getName() + "$" + name);
    }

    static Object make(String name, Object... args) throws Exception {
        Class<?>[] signature =
                switch (name) {
                    case "Staff" -> new Class<?>[] {float.class, float.class, float.class};
                    case "AccidentalCandidate" -> new Class<?>[] {nested("Component"), byte.class};
                    case "SignatureGlyph" ->
                            new Class<?>[] {
                                float.class, int.class, float.class, nested("Component")
                            };
                    default ->
                            throw new IllegalArgumentException(
                                    "Unreviewed original fixture constructor " + name);
                };
        var c = nested(name).getDeclaredConstructor(signature);
        c.setAccessible(true);
        return c.newInstance(args);
    }

    static Object call(String name, Class<?>[] types, Object... args) throws Exception {
        var m = OmrScoreInterpreter.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    static void rect(byte[] labels, byte[] gray, int x, int y, int w, int h, int label) {
        for (int yy = y; yy < y + h; yy++)
            for (int xx = x; xx < x + w; xx++) {
                labels[yy * W + xx] = (byte) label;
                gray[yy * W + xx] = 0;
            }
    }

    static Object evidence(boolean lowerDot, int naturalY) throws Exception {
        byte[] labels = new byte[W * H], gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int line = 0; line < 5; line++) rect(labels, gray, 20, 80 + 16 * line, 280, 1, 4);
        // Same independently printed two-dot/descending-tail geometry as the retained bass
        // controls.
        rect(labels, gray, 100, 82, 18, 26, 3);
        rect(labels, gray, 126, 86, 5, 5, 5);
        if (lowerDot) rect(labels, gray, 126, 102, 5, 5, 5);
        rect(labels, gray, 102, 117, 5, 14, 5);
        rect(labels, gray, 195, naturalY - 22, 2, 32, 3);
        rect(labels, gray, 204, naturalY - 9, 2, 32, 3);
        rect(labels, gray, 196, naturalY - 9, 9, 3, 3);
        rect(labels, gray, 196, naturalY + 7, 9, 3, 3);
        @SuppressWarnings("unchecked")
        var components =
                (List<Object>)
                        call(
                                "findComponents",
                                new Class<?>[] {byte[].class, int.class, int.class, byte.class},
                                labels,
                                W,
                                H,
                                (byte) 3);
        assertEquals(
                "actual semantic components, not invented bounding records", 2, components.size());
        Object body = null, natural = null;
        var x = nested("Component").getDeclaredMethod("minX");
        x.setAccessible(true);
        for (var c : components)
            if ((int) x.invoke(c) < 150) body = c;
            else natural = c;
        assertNotNull(body);
        assertNotNull(natural);
        var staff = make("Staff", 80f, 144f, 16f);
        assertEquals(
                lowerDot,
                call(
                        "rawBassClef",
                        new Class<?>[] {
                            nested("Component"),
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            nested("Staff")
                        },
                        body,
                        labels,
                        gray,
                        W,
                        H,
                        staff));
        var n = make("AccidentalCandidate", natural, (byte) 3);
        float pitch =
                (float)
                        call(
                                "naturalGlyphPitch",
                                new Class<?>[] {
                                    byte[].class,
                                    int.class,
                                    int.class,
                                    nested("AccidentalCandidate"),
                                    float.class
                                },
                                labels,
                                W,
                                H,
                                n,
                                16f);
        assertEquals("actual two-band pitch coordinate", naturalY, pitch, .5f);
        var glyph = make("SignatureGlyph", 200f, ScoreNoteEvent.ACCIDENTAL_NATURAL, pitch, null);
        return call(
                "cancellationEvidence",
                new Class<?>[] {
                    byte[].class,
                    byte[].class,
                    int.class,
                    int.class,
                    List.class,
                    nested("Staff"),
                    List.class
                },
                labels,
                gray,
                W,
                H,
                List.of(glyph),
                staff,
                List.of(make("AccidentalCandidate", body, (byte) 3), n));
    }

    static boolean cancels(Object evidence, int prior) throws Exception {
        var m = nested("NaturalCancellation").getDeclaredMethod("cancels", Integer.class);
        m.setAccessible(true);
        return (boolean) m.invoke(evidence, prior);
    }

    static Object vote(String name, Object value, Class<?> type) throws Exception {
        var m = nested("KeyVote").getDeclaredMethod(name, type);
        m.setAccessible(true);
        return m.invoke(null, value);
    }

    static Object resolve(List<Object> votes, int prior) throws Exception {
        return call("resolveCourtesy", new Class<?>[] {List.class, Integer.class}, votes, prior);
    }

    @Test
    public void bassFNaturalCancelsKnownFSharpThroughActualClefAndTwoBandPitch() throws Exception {
        assertTrue(cancels(evidence(true, 96), 1));
    }

    @Test
    public void sameDrawnTrebleFLaneCannotCancelFSharpInBass() throws Exception {
        assertFalse(cancels(evidence(true, 80), 1));
    }

    @Test
    public void missingPrintedBassDotCannotAuthorizeCancellation() throws Exception {
        assertFalse(cancels(evidence(false, 96), 1));
    }

    @Test
    public void sameOnsetUnresolvedAndConflictingCourtesyVotesCannotInjectZero() throws Exception {
        var signature = vote("signature", 2, int.class);
        var good = vote("cancellation", evidence(true, 96), nested("NaturalCancellation"));
        var wrong = vote("cancellation", evidence(true, 80), nested("NaturalCancellation"));
        assertEquals(0, resolve(List.of(good), 1));
        assertEquals(2, resolve(List.of(signature), 1));
        assertNull(resolve(List.of(good, signature), 1));
        assertNull(resolve(List.of(signature, good), 1));
        assertNull(resolve(List.of(wrong, signature), 1));
        assertNull(resolve(List.of(signature, wrong), 1));
    }
}
