// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

/** Original symbolic bowl plus independent printed note shaft ownership. */
public class OwnedShaftFlatFragmentTest {
    static final int W = 160, H = 140;
    byte[] g = new byte[W * H], l = new byte[W * H];

    public OwnedShaftFlatFragmentTest() {
        Arrays.fill(g, (byte) 255);
    }

    void rect(int a, int b, int c, int d, boolean semantic) {
        for (int y = c; y <= d; y++)
            for (int x = a; x <= b; x++) {
                g[y * W + x] = 0;
                if (semantic) l[y * W + x] = 5;
            }
    }

    Object make(String n, Object... a) throws Exception {
        var c =
                Class.forName(OmrScoreInterpreter.class.getName() + "$" + n)
                        .getDeclaredConstructors()[0];
        c.setAccessible(true);
        return c.newInstance(a);
    }

    Object head(int a, int b, int c, int d) throws Exception {
        return make("Component", 180, a, b, c, d, (a + b) * .5f, (c + d) * .5f);
    }

    void flat() {
        rect(60, 62, 50, 75, true);
        rect(62, 68, 64, 66, true);
        rect(67, 69, 65, 71, true);
        rect(62, 67, 72, 75, true);
    }

    int read(boolean owner) throws Exception {
        Object target = head(88, 108, 68, 82), other = head(43, 63, 84, 96);
        Object glyph = make("AccidentalCandidate", head(60, 69, 50, 75), (byte) 5);
        var m =
                OmrScoreInterpreter.class.getDeclaredMethod(
                        "detectWrittenAccidental",
                        byte[].class,
                        int.class,
                        int.class,
                        List.class,
                        target.getClass(),
                        float.class,
                        List.class,
                        byte[].class);
        m.setAccessible(true);
        return (int)
                m.invoke(
                        null,
                        l,
                        W,
                        H,
                        List.of(glyph),
                        target,
                        16f,
                        owner ? List.of(other, target) : List.of(target),
                        g);
    }

    @Test
    public void ownedUpwardShaftFragmentCannotFlattenLaterAttack() throws Exception {
        flat();
        rect(60, 62, 20, 90, false);
        assertEquals(ScoreNoteEvent.ACCIDENTAL_FROM_KEY, read(true));
    }

    @Test
    public void standalonePrintedFlatIsRetained() throws Exception {
        flat();
        assertEquals(ScoreNoteEvent.ACCIDENTAL_FLAT, read(false));
    }

    @Test
    public void noteWithoutConnectedRawShaftCannotVetoFlat() throws Exception {
        flat();
        assertEquals(ScoreNoteEvent.ACCIDENTAL_FLAT, read(true));
    }
}
