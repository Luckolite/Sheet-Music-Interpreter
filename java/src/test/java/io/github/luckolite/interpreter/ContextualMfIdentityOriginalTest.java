// Copyright 2026 Luckolite; SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.io.*;
import org.junit.Test;

/** Identity and source binding from the original independently authored probe. */
public final class ContextualMfIdentityOriginalTest {
    private static final String ASSET = "caller/clean-context-complete-mf-tall-whitepaper.gray.gz";

    private static PlayingTechniqueDetector.Word word() {
        return new PlayingTechniqueDetector.Word(
                "mf", 288 / 700f, 196 / 420f, 363 / 700f, 252 / 420f);
    }

    private static PlayingTechniqueDetector.Staff owner() {
        return new PlayingTechniqueDetector.Staff(112, 176, 16, 0, 1);
    }

    private static ContextualMfAdmission proof(byte[] g, int w, PlayingTechniqueDetector.Staff s) {
        return new ContextualMfAdmission(
                g,
                w,
                420,
                List.of(s),
                List.of(new MeasureRegion(0, 1, 0, 1)),
                List.of(
                        new ScoreNoteEvent(
                                0,
                                330 / 700f,
                                4,
                                0,
                                1,
                                144 / 420f,
                                false,
                                0,
                                0,
                                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                                1)));
    }

    @Test
    public void equalDistinctOwnerDoesNotBorrow() throws Exception {
        byte[] g = ContextualMfResourceFixture.read(ASSET), old = g.clone();
        var s = owner();
        assertFalse(proof(g, 700, s).proved(word(), owner()));
        assertArrayEquals(old, g);
    }

    @Test
    public void dimensionsRemainSourceBound() throws Exception {
        byte[] g = ContextualMfResourceFixture.read(ASSET), old = g.clone();
        var s = owner();
        assertFalse(proof(g, 699, s).proved(word(), s));
        assertArrayEquals(old, g);
    }

    @Test
    public void cachedContextRejectsChangedSource() throws Exception {
        byte[] g = ContextualMfResourceFixture.read(ASSET), old = g.clone();
        var s = owner();
        var p = proof(g, 700, s);
        assertTrue("original complete prerequisite", p.proved(word(), s));
        g[0] = (byte) 254;
        assertFalse(p.proved(word(), s));
        g[0] = old[0];
        assertArrayEquals(old, g);
    }
}
