// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original neutral pedal-caption contracts. No synthesized sustain duration or pedal state. */
public final class PedalRingCaptionOriginalTest {
    static final int W = 500, H = 300;

    static byte[] rails() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        for (int y = 100; y <= 164; y += 16) for (int x = 20; x < 460; x++) g[y * W + x] = 0;
        return g;
    }

    static ScoreRestEvent rest() {
        return ScoreRestEvent.fullMeasure(0, .5f, 116f / H, 8f / H, 0, 1);
    }

    static ScorePageInterpretation page(List<ScoreRestEvent> rests, List<ScoreNoteEvent> notes) {
        return new ScorePageInterpretation(
                List.of(new MeasureRegion(.2f, .5f, .25f, .65f)),
                notes,
                1,
                List.of(),
                List.of(),
                List.of(new ScoreMeterChange(0, 4, 4)),
                rests,
                List.of(),
                List.of());
    }

    static List<PlayingTechniqueDetector.Staff> staff() {
        return List.of(new PlayingTechniqueDetector.Staff(100, 164, 16, 0, 1));
    }

    static PlayingTechniqueDetector.Word word(String text, float l, float r) {
        return new PlayingTechniqueDetector.Word(text, l, 195f / H, r, 211f / H);
    }

    static ScorePageInterpretation apply(ScorePageInterpretation p, String text, byte[] g) {
        return ScoreExpressionDetector.apply(p, List.of(word(text, .15f, .35f)), staff(), g, W, H);
    }

    @Test
    public void completePhraseIsNeutralAndKeepsRawText() {
        for (String t : List.of("let pedal ring", "Let the pedal ring.", " LET PEDAL RING! ")) {
            var ds = ExpressiveDirectionText.parse(t);
            assertEquals(1, ds.size());
            assertEquals(ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION, ds.get(0).kind());
            assertEquals(t, ds.get(0).printedText());
        }
    }

    @Test
    public void proseAndOtherInstructionsDoNotGetCaptionAuthority() {
        for (String t :
                List.of(
                        "do not let pedal ring",
                        "let pedal ring through the chorus",
                        "let pedals ring",
                        "pedal ring",
                        "let ring",
                        "letpedalring",
                        "let pedal ringing",
                        "flowing",
                        "rit."))
            assertFalse(
                    t,
                    PrintedRestDirection.allowsPedalCaption(
                            ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION, t));
    }

    @Test
    public void uniqueFullRestOwnsStraddlingCaption() {
        var input = page(List.of(rest()), List.of());
        var g = rails();
        byte[] before = g.clone();
        var output = apply(input, "let pedal ring", g);
        assertEquals(1, output.expressiveEvents().size());
        var e = output.expressiveEvents().get(0);
        assertEquals(ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION, e.kind());
        assertEquals(ScoreExpressiveEvent.Scope.PART, e.scope());
        assertTrue(e.targetEventId().orElseThrow().startsWith("expression-rest-column:0:0:1:"));
        assertEquals(new ScoreAnchor(0, 0), e.start().orElseThrow());
        assertEquals("let pedal ring", e.evidence().get(0).printedText());
        assertEquals(input.notes(), output.notes());
        assertEquals(input.rests(), output.rests());
        assertEquals(input.dynamicChanges(), output.dynamicChanges());
        assertEquals(input.techniqueChanges(), output.techniqueChanges());
        assertEquals(input.playbackDirections(), output.playbackDirections());
        assertArrayEquals(before, g);
    }

    @Test
    public void missingOrLiteralRestCannotSupplyCaptionOwner() {
        assertTrue(
                apply(page(List.of(), List.of()), "let pedal ring", rails())
                        .expressiveEvents()
                        .isEmpty());
        var literal = new ScoreRestEvent(0, .5f, 116f / H, 8f / H, 0, 1, 4);
        assertNull(
                PrintedRestDirection.findPedalCaption(
                        page(List.of(literal), List.of()),
                        staff(),
                        word("let pedal ring", .15f, .35f),
                        rails(),
                        W,
                        H));
    }

    @Test
    public void soundingOrDuplicateFullRestAbstains() {
        var n = new ScoreNoteEvent(0, .5f, 0, 0, 1, 164f / H, false, 0, 0, 0, 1, 1, 0);
        assertNull(
                PrintedRestDirection.findPedalCaption(
                        page(List.of(rest()), List.of(n)),
                        staff(),
                        word("let pedal ring", .15f, .35f),
                        rails(),
                        W,
                        H));
        assertNull(
                PrintedRestDirection.findPedalCaption(
                        page(List.of(rest(), rest()), List.of()),
                        staff(),
                        word("let pedal ring", .15f, .35f),
                        rails(),
                        W,
                        H));
    }

    @Test
    public void blankRailsAndCrossingNextBarAbstain() {
        byte[] g = new byte[W * H];
        Arrays.fill(g, (byte) 255);
        assertTrue(
                apply(page(List.of(rest()), List.of()), "let pedal ring", g)
                        .expressiveEvents()
                        .isEmpty());
        assertNull(
                PrintedRestDirection.findPedalCaption(
                        page(List.of(rest()), List.of()),
                        staff(),
                        word("let pedal ring", .15f, .6f),
                        rails(),
                        W,
                        H));
    }

    @Test
    public void arbitraryUnresolvedAndOldLeftEdgeRestPathStayClosed() {
        var p = page(List.of(rest()), List.of());
        assertFalse(PrintedRestDirection.allows(ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION));
        assertNull(PrintedRestDirection.find(p, staff(), word("rit.", .15f, .35f), rails(), W, H));
        assertNull(
                PrintedRestDirection.findPedalCaption(
                        p, staff(), word("flowing", .15f, .35f), rails(), W, H));
    }

    @Test
    public void noPedalToggleOrInventedEnd() {
        var out = apply(page(List.of(rest()), List.of()), "let pedal ring", rails());
        assertEquals(1, out.expressiveEvents().size());
        var e = out.expressiveEvents().get(0);
        assertTrue(e.end().isEmpty());
        assertNotEquals(ScoreExpressiveEvent.Kind.PEDAL_DOWN, e.kind());
        assertNotEquals(ScoreExpressiveEvent.Kind.PEDAL_UP, e.kind());
    }

    @Test
    public void emptyEvidenceCannotAuthorizePartCaption() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ScoreExpressiveEvent(
                                "unproved",
                                ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION,
                                java.util.Optional.empty(),
                                java.util.Optional.empty(),
                                ScoreExpressiveEvent.Scope.UNRESOLVED,
                                1,
                                2,
                                java.util.Optional.of("expression-rest-column:0:1:2:1056964608"),
                                ScoreExpressiveEvent.Strength.UNSPECIFIED,
                                "",
                                java.util.List.of()));
    }
}
