// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ContextualCharacterOwnerTest {
    static OcrText full(String cue, boolean separate, OcrText.Grouping grouping) {
        var flow =
                new OcrText.Line(
                        "Flowing",
                        new OcrText.Box(139, 54, 239, 72),
                        List.of(
                                new OcrText.Element(
                                        "Flowing", new OcrText.Box(139, 54, 239, 72), List.of())));
        var mp =
                new OcrText.Line(
                        "mp",
                        new OcrText.Box(130, 185, 169, 204),
                        List.of(
                                new OcrText.Element(
                                        "mp", new OcrText.Box(130, 185, 169, 204), List.of())));
        var lines = new ArrayList<OcrText.Line>();
        lines.add(flow);
        lines.add(mp);
        var blocks = new ArrayList<OcrText.Block>();
        OcrText.Line prose =
                cue == null
                        ? null
                        : new OcrText.Line(cue, new OcrText.Box(130, 76, 280, 92), List.of());
        if (prose != null && !separate) lines.add(prose);
        if (separate) {
            blocks.add(new OcrText.Block("Flowing", flow.box(), List.of(flow)));
            blocks.add(new OcrText.Block("mp", mp.box(), List.of(mp)));
        } else
            blocks.add(new OcrText.Block("Flowing mp", new OcrText.Box(130, 54, 280, 204), lines));
        if (prose != null && separate)
            blocks.add(new OcrText.Block(cue, new OcrText.Box(130, 76, 280, 92), List.of(prose)));
        return new OcrText("Flowing mp" + (cue == null ? "" : cue), blocks, grouping);
    }

    static List<CharacterDirectionEvidence.Evidence> collect(OcrText t) throws Exception {
        var gray = NativeEnglishPositiveTest.page("mp");
        var literal = NativeEnglishPositiveTest.literals(gray, "mp");
        assertEquals(1, literal.size());
        var score = LexicalContextTest.score();
        return CharacterDirectionEvidence.collect(
                t,
                1,
                0,
                List.of(literal.get(0).word()),
                NativeEnglishPositiveTest.staffs(),
                score.measures(),
                score.notes(),
                gray,
                500,
                300);
    }

    @Test
    public void genuineOriginalBlockDirectionPreserved() throws Exception {
        assertEquals(1, collect(full(null, false, OcrText.Grouping.ORIGINAL_BLOCKS)).size());
    }

    @Test
    public void unknownLegacyAbstains() throws Exception {
        assertTrue(collect(full(null, false, OcrText.Grouping.UNKNOWN)).isEmpty());
    }

    @Test
    public void sameBlockExplicitBylineAbstains() throws Exception {
        assertTrue(
                collect(full("Music by Original Author", false, OcrText.Grouping.ORIGINAL_BLOCKS))
                        .isEmpty());
    }

    @Test
    public void detectedSeparateBylineAbstains() throws Exception {
        assertTrue(
                collect(full("Music by Original Author", true, OcrText.Grouping.DETECTED_LINES))
                        .isEmpty());
    }

    @Test
    public void originalSeparateBylineAbstains() throws Exception {
        assertTrue(
                collect(full("Music by Original Author", true, OcrText.Grouping.ORIGINAL_BLOCKS))
                        .isEmpty());
    }

    @Test
    public void originalSeparatePublisherAbstains() throws Exception {
        assertTrue(
                collect(full("Copyright Publisher", true, OcrText.Grouping.ORIGINAL_BLOCKS))
                        .isEmpty());
    }

    @Test
    public void originalSeparateVerseAbstains() throws Exception {
        assertTrue(
                collect(
                                full(
                                        "Verse 1: these are sung words",
                                        true,
                                        OcrText.Grouping.ORIGINAL_BLOCKS))
                        .isEmpty());
    }
}
