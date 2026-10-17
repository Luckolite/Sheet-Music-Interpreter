// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class NativeDirectionOcrCacheTest {
    static class Reader implements NativeDirectionOcrCache.Reader {
        int full, c2, c3;
        boolean failFull, fail2, mutateFull;
        String cue;

        public OcrText full(byte[] g, int w, int h) throws Exception {
            full++;
            if (mutateFull) g[0] ^= 1;
            if (failFull) throw new IOException("original OCR failure");
            return NativeEnglishPositiveTest.text(cue);
        }

        public OcrText crop(byte[] g, int w, int h, PaperDynamicWord.Crop crop, int scale)
                throws Exception {
            if (scale == 2) {
                c2++;
                if (fail2) throw new IOException("original crop failure");
            } else if (scale == 3) c3++;
            else throw new AssertionError();
            return PaperDynamicLiteralReproofTest.reading("mp", crop, scale);
        }
    }

    static List<PlayingTechniqueDetector.Word> call(
            NativeDirectionOcrCache cache, byte[] g, List<PlayingTechniqueDetector.Word> words) {
        var s = LexicalContextTest.score();
        return cache.directions(
                g,
                500,
                300,
                NativeEnglishPositiveTest.staffs(),
                s.measures(),
                s.notes(),
                words,
                List.of());
    }

    static List<PlayingTechniqueDetector.Word> seed() {
        return List.of(NativeEnglishPositiveTest.candidate("mp"));
    }

    @Test
    public void realAgreementFallbackAndRawReadsAreReused() throws Exception {
        var r = new Reader();
        var c = new NativeDirectionOcrCache(r);
        var g = NativeEnglishPositiveTest.page("mp");
        var a = call(c, g, seed());
        assertEquals(1, a.size());
        assertTrue(EnglishCharacterOcrContext.admitted(a.get(0)));
        assertEquals(a, call(c, g, seed()));
        assertEquals(1, r.full);
        assertEquals(1, r.c2);
        assertEquals(1, r.c3);
    }

    @Test
    public void allCurrentOrdinaryCompetitorsAreRecheckedAfterAcceptedCache() throws Exception {
        var r = new Reader();
        var c = new NativeDirectionOcrCache(r);
        var g = NativeEnglishPositiveTest.page("mp");
        assertEquals(1, call(c, g, seed()).size());
        var s = seed().get(0);
        var competitor =
                new PlayingTechniqueDetector.Word(
                        "not a dynamic subset", s.left(), s.top(), s.right(), s.bottom());
        assertTrue(call(c, g, List.of(s, competitor)).isEmpty());
        assertEquals(1, call(c, g, seed()).size());
        assertEquals(1, r.full);
        assertEquals(1, r.c2);
        assertEquals(1, r.c3);
    }

    @Test
    public void changedStaffAndColumnsCannotReusePriorAdmission() throws Exception {
        var r = new Reader();
        var c = new NativeDirectionOcrCache(r);
        var g = NativeEnglishPositiveTest.page("mp");
        assertEquals(1, call(c, g, seed()).size());
        var s = LexicalContextTest.score();
        assertTrue(
                c.directions(
                                g,
                                500,
                                300,
                                List.of(new PlayingTechniqueDetector.Staff(200, 264, 16, 0, 1)),
                                s.measures(),
                                s.notes(),
                                seed(),
                                List.of())
                        .isEmpty());
        var other = new ScoreNoteEvent(0, .30f, 0, 0, 1, 164f / 300, false, 0, 0, 0, 1, 1, 0);
        assertTrue(
                c.directions(
                                g,
                                500,
                                300,
                                NativeEnglishPositiveTest.staffs(),
                                s.measures(),
                                List.of(s.notes().get(0), other),
                                seed(),
                                List.of())
                        .isEmpty());
        assertEquals(1, call(c, g, seed()).size());
        assertEquals(1, r.full);
        assertEquals(1, r.c2);
        assertEquals(1, r.c3);
    }

    @Test
    public void sameArrayMutationPoisonsCacheWithoutOcrRetry() throws Exception {
        var r = new Reader();
        var c = new NativeDirectionOcrCache(r);
        var g = NativeEnglishPositiveTest.page("mp");
        assertEquals(1, call(c, g, seed()).size());
        g[0] ^= 1;
        assertTrue(call(c, g, seed()).isEmpty());
        g[0] ^= 1;
        assertTrue(call(c, g, seed()).isEmpty());
        assertEquals(1, r.full);
        assertEquals(1, r.c2);
        assertEquals(1, r.c3);
    }

    @Test
    public void mutationDuringFullReadCannotQualify() throws Exception {
        var r = new Reader();
        r.mutateFull = true;
        var c = new NativeDirectionOcrCache(r);
        var g = NativeEnglishPositiveTest.page("mp");
        assertTrue(call(c, g, seed()).isEmpty());
        assertTrue(call(c, g, seed()).isEmpty());
        assertEquals(1, r.full);
        assertEquals(0, r.c2);
    }

    @Test
    public void noOcrAndNoLiteralPathsAbstainWithoutReading() throws Exception {
        var g = NativeEnglishPositiveTest.page("mp");
        assertTrue(call(new NativeDirectionOcrCache(null), g, seed()).isEmpty());
        var r = new Reader();
        assertTrue(
                call(
                                new NativeDirectionOcrCache(r),
                                g,
                                List.of(
                                        new PlayingTechniqueDetector.Word(
                                                "Flowing", .2f, .2f, .4f, .3f)))
                        .isEmpty());
        assertEquals(0, r.full);
        assertEquals(0, r.c2);
    }

    @Test
    public void fullPageFailureIsRawFailureCached() throws Exception {
        var r = new Reader();
        r.failFull = true;
        var c = new NativeDirectionOcrCache(r);
        var g = NativeEnglishPositiveTest.page("mp");
        assertTrue(call(c, g, seed()).isEmpty());
        assertTrue(call(c, g, seed()).isEmpty());
        assertEquals(1, r.full);
        assertEquals(0, r.c2);
    }

    @Test
    public void cropFailureIsRawFailureCachedButCompetingInputsRemainLive() throws Exception {
        var r = new Reader();
        r.fail2 = true;
        var c = new NativeDirectionOcrCache(r);
        var g = NativeEnglishPositiveTest.page("mp");
        assertTrue(call(c, g, seed()).isEmpty());
        assertTrue(call(c, g, seed()).isEmpty());
        assertEquals(1, r.full);
        assertEquals(1, r.c2);
        assertEquals(0, r.c3);
    }

    @Test
    public void existingPaperPassRawTreesWarmTheSameProofCache() throws Exception {
        var r = new Reader();
        var c = new NativeDirectionOcrCache(r);
        var g = NativeEnglishPositiveTest.page("mp");
        var crop = PaperDynamicWord.crop(seed().get(0), 500, 300);
        c.rawCrop(g, 500, 300, crop, 2);
        c.rawCrop(g, 500, 300, crop, 3);
        assertEquals(0, r.full);
        assertEquals(1, call(c, g, seed()).size());
        assertEquals(1, r.full);
        assertEquals(1, r.c2);
        assertEquals(1, r.c3);
    }

    @Test
    public void strictGlyphPositiveKeepsAgreementOcrUnneeded() throws Exception {
        var r = new Reader();
        var c = new NativeDirectionOcrCache(r);
        var g = NativeEnglishPositiveTest.page("mp");
        var s = LexicalContextTest.score();
        var actual = NativeEnglishPositiveTest.literals(g, "mp");
        assertEquals(1, actual.size());
        assertEquals(
                1,
                c.directions(
                                g,
                                500,
                                300,
                                NativeEnglishPositiveTest.staffs(),
                                s.measures(),
                                s.notes(),
                                seed(),
                                actual)
                        .size());
        assertEquals(1, r.full);
        assertEquals(0, r.c2);
        assertEquals(0, r.c3);
    }

    @Test
    public void currentLocalCreditVetoPreventsBoundedCropReads() throws Exception {
        var r = new Reader();
        r.cue = "Music by Original Author";
        var c = new NativeDirectionOcrCache(r);
        var g = NativeEnglishPositiveTest.page("mp");
        assertTrue(call(c, g, seed()).isEmpty());
        assertEquals(1, r.full);
        assertEquals(0, r.c2);
        assertEquals(0, r.c3);
    }
}
