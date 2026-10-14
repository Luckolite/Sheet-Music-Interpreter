// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LetterboxedArtworkTest {
    static ScoreCreditsDetector.Result read(String... text) {
        var lines = new ArrayList<ScoreCreditsDetector.Line>();
        int y = 100;
        for (String t : text) {
            lines.add(new ScoreCreditsDetector.Line(t, 500, y, 1500, y + 50));
            y += 80;
        }
        return ScoreCreditsDetector.detect(
                "Evening Song",
                List.of(new ScoreCreditsDetector.Page(1800, 1350, -1, lines, false, false)),
                Set.of(),
                true);
    }

    @Test
    public void brandingDoesNotOverrideSong() {
        var r = read("Championships", "CHANNEL LOGO", "Copyright Camera Person");
        assertEquals("Evening Song", r.title());
        assertTrue(r.unclassifiedCredits().isEmpty());
    }

    @Test
    public void explicitComposerRetained() {
        assertEquals("Morgan Vale", read("Championships", "Music by Morgan Vale").composer());
    }

    @Test
    public void explicitArrangerRetained() {
        assertEquals("Morgan Vale", read("Arranged by Morgan Vale").arranger());
    }

    @Test
    public void explicitArtistRetained() {
        assertEquals("Morgan Vale", read("Performed by Morgan Vale").artist());
    }

    @Test
    public void explicitTitleRetained() {
        assertEquals("Silver River", read("Title: Silver River").title());
    }

    @Test
    public void conflictingTitlesRetainImport() {
        assertEquals(
                "Evening Song",
                read("Title: Silver River", "Title: Golden Lake", "Title: Quiet Hill").title());
    }

    static int[] image(boolean bars, boolean color) {
        int w = 1000, h = 750;
        int[] p = new int[w * h];
        var r = new Random(41);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if (bars && (y < 90 || y >= 660)) continue;
                int a = 50 + r.nextInt(190),
                        b = color ? 50 + r.nextInt(190) : a,
                        c = color ? 50 + r.nextInt(190) : a;
                p[y * w + x] = a << 16 | b << 8 | c;
            }
        return p;
    }

    @Test
    public void framedColorTextureQualifies() {
        assertTrue(LetterboxedArtworkEvidence.photographic(1000, 750, image(true, true), -1));
    }

    @Test
    public void unframedImageExcluded() {
        assertFalse(LetterboxedArtworkEvidence.photographic(1000, 750, image(false, true), -1));
    }

    @Test
    public void monochromeTextureExcluded() {
        assertFalse(LetterboxedArtworkEvidence.photographic(1000, 750, image(true, false), -1));
    }

    @Test
    public void notationExcluded() {
        assertFalse(LetterboxedArtworkEvidence.photographic(1000, 750, image(true, true), 400));
    }

    @Test
    public void invalidDimensionsExcluded() {
        assertFalse(LetterboxedArtworkEvidence.photographic(1000, 750, new int[1], -1));
    }

    @Test
    public void nonfiniteStaffExcluded() {
        assertFalse(
                LetterboxedArtworkEvidence.photographic(1000, 750, image(true, true), Float.NaN));
    }
}
