// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class EnglishContextOwnershipTest {
    static final List<String> TERMS =
            List.of(
                    "Flowing",
                    "Triumphantly",
                    "Ethereal",
                    "With driving intensity",
                    "Like an echo");

    static OcrText.Line line(String s, int x, int y) {
        return new OcrText.Line(
                s,
                new OcrText.Box(x, y, x + 150, y + 18),
                List.of(new OcrText.Element(s, new OcrText.Box(x, y, x + 150, y + 18), List.of())));
    }

    static OcrText.Block block(OcrText.Line... ls) {
        return new OcrText.Block("", new OcrText.Box(0, 0, 500, 300), List.of(ls));
    }

    static List<PlayingTechniqueDetector.Word> collect(OcrText t) {
        var out = new ArrayList<PlayingTechniqueDetector.Word>();
        MusicalOcrEvidence.addDirectionWords(t, out, 1, 0, 500, 300);
        return out;
    }

    @Test
    public void portableSplitLinesKeepLocalProseOwnership() {
        for (String term : TERMS)
            for (String cue :
                    List.of(
                            "Music by Original Author",
                            "Copyright Original Publisher",
                            "Verse 1: these are sung")) {
                var a = line(term, 130, 54);
                var b = line(cue, 130, 76);
                var t =
                        new OcrText(
                                "", List.of(block(a), block(b)), OcrText.Grouping.DETECTED_LINES);
                assertTrue(term, collect(t).isEmpty());
            }
    }

    @Test
    public void originalSeparateBlocksDoNotImplyOwnership() {
        for (String term : TERMS) {
            var a = line(term, 130, 54);
            var b = line("Music by Original Author", 130, 76);
            assertEquals(term, 1, collect(new OcrText("", List.of(block(a), block(b)))).size());
        }
    }

    @Test
    public void oversizedBlockDistantCueDoesNotOwnMarking() {
        for (String term : TERMS) {
            var a = line(term, 130, 54);
            var b = line("Copyright Original Publisher", 130, 220);
            assertEquals(term, 1, collect(new OcrText("", List.of(block(a, b)))).size());
        }
    }

    @Test
    public void differentColumnDoesNotOwnMarking() {
        for (String term : TERMS) {
            var a = line(term, 20, 54);
            var b = line("Verse 1: these are sung", 300, 76);
            assertEquals(
                    term,
                    1,
                    collect(new OcrText("", List.of(block(a, b)), OcrText.Grouping.DETECTED_LINES))
                            .size());
        }
    }

    @Test
    public void loneSourceMarkingAndExactPrintedTextRetained() {
        for (String term : TERMS) {
            var a = line("  " + term + "!  ", 130, 54);
            var out = collect(new OcrText("", List.of(block(a))));
            assertEquals(1, out.size());
            assertEquals(a.text(), out.get(0).text());
        }
    }

    @Test
    public void italianEndingNavigationPathsRemainAvailable() {
        for (String term : List.of("dolce", "rit.", "D.C. al Fine", "1.")) {
            var a = line(term, 130, 54);
            var b = line("Music by Original Author", 130, 76);
            var t = new OcrText("", List.of(block(a, b)));
            assertFalse(term, collect(t).isEmpty());
        }
    }

    @Test
    public void missingGeometryCannotInventOwnership() {
        var a = line("Flowing", 130, 54);
        var b = new OcrText.Line("Music by Original Author", null, List.of());
        assertEquals(1, collect(new OcrText("", List.of(block(a, b)))).size());
    }

    @Test
    public void legacyTreeDefaultsUnknown() {
        var t = new OcrText("Flowing", List.of(block(line("Flowing", 130, 54))));
        assertEquals(OcrText.Grouping.UNKNOWN, t.grouping());
    }
}
