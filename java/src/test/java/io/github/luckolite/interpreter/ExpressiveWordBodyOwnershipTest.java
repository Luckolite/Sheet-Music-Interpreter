// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Procedural italic letter strokes and independently placed musical marks. */
public class ExpressiveWordBodyOwnershipTest {
    private static final int W = 1000, H = 1000;
    private static final float GAP = 12;
    private final byte[] gray = new byte[W * H], labels = new byte[W * H];
    private final List<NoteArticulationDetector.Anchor> anchors =
            List.of(new NoteArticulationDetector.Anchor(211, 179, GAP, 0));

    private List<PlayingTechniqueDetector.Word> word(String text) {
        return List.of(
                new PlayingTechniqueDetector.Word(text, 149f / W, 111f / H, 265f / W, 163f / H));
    }

    private void pixel(int x, int y, int tone, byte label) {
        gray[y * W + x] = (byte) tone;
        labels[y * W + x] = label;
    }

    private void wordBody() {
        Arrays.fill(gray, (byte) 205);
        Arrays.fill(labels, (byte) 0);
        // Repeated hollow letter bowls joined by pale italic baseline strokes.
        for (int center = 162; center <= 246; center += 14) {
            for (int y = 121; y <= 143; y++)
                for (int x = center - 8; x <= center + 8; x++) {
                    double xx = (x - center - (143 - y) * .12) / 7.0, yy = (y - 132) / 10.0;
                    double radius = xx * xx + yy * yy;
                    if (radius >= .55 && radius <= 1.3)
                        pixel(x, y, 155, OmrMeasurePostProcessor.SYMBOL);
                }
        }
        for (int y = 140; y <= 142; y++)
            for (int x = 160; x <= 251; x++) pixel(x, y, 155, OmrMeasurePostProcessor.SYMBOL);
        // A dark vertical core on one pale letter is an isolated wedge at the old raw cutoff.
        for (int y = 125; y <= 138; y++) {
            int radius = Math.round(3 * (138 - y) / 13f);
            for (int x = 211 - radius; x <= 211 + radius; x++)
                pixel(x, y, 35, OmrMeasurePostProcessor.SYMBOL);
        }
    }

    private List<ExpressiveWordBodyInk.Body> bodies(List<PlayingTechniqueDetector.Word> words) {
        return ExpressiveWordBodyInk.detect(words, labels, gray, W, H, anchors);
    }

    private int[] fragment() {
        return new int[] {126 * W + 210, 128 * W + 211, 132 * W + 211};
    }

    private int marks(boolean filtered) {
        return filtered
                ? NoteArticulationDetector.detectWithWordBodies(
                        labels, gray, W, H, anchors, bodies(word("espressivo")))[0]
                : NoteArticulationDetector.detect(labels, gray, W, H, anchors)[0];
    }

    @Test
    public void recognizedWordOwnsOnlyItsConnectedLetterPixels() {
        wordBody();
        var bodies = bodies(word("espressivo"));
        assertEquals(1, bodies.size());
        assertTrue(ExpressiveWordBodyInk.owns(bodies, fragment(), fragment().length, W));
    }

    @Test
    public void originalDetectorNeedsTheOcrEvidenceToRejectTheLetterCore() {
        wordBody();
        assertTrue((marks(false) & NoteArticulation.STACCATISSIMO) != 0);
        assertEquals(0, marks(true));
    }

    @Test
    public void missingWordEvidenceDoesNotOwnTheLetter() {
        wordBody();
        assertTrue(bodies(List.of()).isEmpty());
    }

    @Test
    public void unrecognizedWordDoesNotOwnTheLetter() {
        wordBody();
        assertTrue(bodies(word("abcdefghi")).isEmpty());
    }

    @Test
    public void partialDirectionTokenDoesNotOwnTheLetter() {
        wordBody();
        assertTrue(bodies(word("espress")).isEmpty());
    }

    @Test
    public void staleBoxElsewhereDoesNotOwnTheLetter() {
        wordBody();
        assertTrue(
                bodies(
                                List.of(
                                        new PlayingTechniqueDetector.Word(
                                                "espressivo", .03f, .04f, .3f, .22f)))
                        .isEmpty());
    }

    @Test
    public void clippedBoxDoesNotOwnIncompleteText() {
        wordBody();
        assertTrue(
                bodies(
                                List.of(
                                        new PlayingTechniqueDetector.Word(
                                                "espressivo",
                                                193f / W,
                                                120f / H,
                                                237f / W,
                                                144f / H)))
                        .isEmpty());
    }

    @Test
    public void genuineDotInsideWordBoxRemainsIndependent() {
        wordBody();
        for (int y = 153; y <= 157; y++)
            for (int x = 209; x <= 213; x++) pixel(x, y, 35, OmrMeasurePostProcessor.SYMBOL);
        assertEquals(NoteArticulation.STACCATO, marks(true));
    }

    @Test
    public void genuineSameBitInsideWordBoxSurvivesAfterFalseLetterCore() {
        wordBody();
        for (int y = 149; y <= 162; y++) {
            int radius = Math.round(3 * (162 - y) / 13f);
            for (int x = 211 - radius; x <= 211 + radius; x++)
                pixel(x, y, 35, OmrMeasurePostProcessor.SYMBOL);
        }
        assertEquals(NoteArticulation.STACCATISSIMO, marks(false));
        assertEquals(NoteArticulation.STACCATISSIMO, marks(true));
    }

    @Test
    public void independentTenutoAndDotAreBothPreserved() {
        wordBody();
        for (int x = 205; x <= 217; x++) pixel(x, 177, 35, OmrMeasurePostProcessor.SYMBOL);
        for (int y = 185; y <= 189; y++)
            for (int x = 209; x <= 213; x++) pixel(x, y, 35, OmrMeasurePostProcessor.SYMBOL);
        var distant = List.of(new NoteArticulationDetector.Anchor(211, 209, GAP, 0));
        var box =
                List.of(
                        new PlayingTechniqueDetector.Word(
                                "espressivo", 149f / W, 111f / H, 265f / W, 181f / H));
        var proved = ExpressiveWordBodyInk.detect(box, labels, gray, W, H, distant);
        assertEquals(1, proved.size());
        assertEquals(
                NoteArticulation.STACCATO | NoteArticulation.TENUTO,
                NoteArticulationDetector.detect(labels, gray, W, H, distant)[0]);
        assertEquals(
                NoteArticulation.STACCATO | NoteArticulation.TENUTO,
                NoteArticulationDetector.detectWithWordBodies(labels, gray, W, H, distant, proved)[
                        0]);
    }

    @Test
    public void wordBoxMembershipAloneCannotOwnDetachedPixels() {
        wordBody();
        int[] p = {154 * W + 211, 155 * W + 211, 156 * W + 211};
        assertFalse(ExpressiveWordBodyInk.owns(bodies(word("espressivo")), p, p.length, W));
    }

    @Test
    public void oneDetachedPixelPreventsWholeFragmentOwnership() {
        wordBody();
        int[] p = {126 * W + 210, 128 * W + 211, 154 * W + 211};
        assertFalse(ExpressiveWordBodyInk.owns(bodies(word("espressivo")), p, p.length, W));
    }

    @Test
    public void headMaskTouchingTheWordPreventsOwnership() {
        wordBody();
        labels[132 * W + 211] = OmrMeasurePostProcessor.NOTEHEAD;
        assertTrue(bodies(word("espressivo")).isEmpty());
    }

    @Test
    public void graceAnchorInsideTheWordPreventsOwnership() {
        wordBody();
        var grace = List.of(new NoteArticulationDetector.Anchor(211, 132, GAP, 0));
        assertTrue(
                ExpressiveWordBodyInk.detect(word("espressivo"), labels, gray, W, H, grace)
                        .isEmpty());
    }

    @Test
    public void wordTouchingAConnectedShaftCannotOwnIt() {
        wordBody();
        for (int y = 138; y <= 181; y++) pixel(230, y, 35, OmrMeasurePostProcessor.STEM_OR_REST);
        assertTrue(bodies(word("espressivo")).isEmpty());
    }

    @Test
    public void wordTouchingStaffRuleCannotOwnIt() {
        wordBody();
        for (int x = 0; x < W; x++) pixel(x, 142, 35, OmrMeasurePostProcessor.STAFF);
        assertTrue(bodies(word("espressivo")).isEmpty());
    }

    @Test
    public void aLongClosedRingInsideAnOcrBoxDoesNotProveWordInk() {
        Arrays.fill(gray, (byte) 205);
        Arrays.fill(labels, (byte) 0);
        for (int y = 121; y <= 145; y++)
            for (int x = 157; x <= 255; x++) {
                double radius = Math.pow((x - 206) / 49.0, 2) + Math.pow((y - 133) / 12.0, 2);
                if (radius > .7 && radius < 1.15) pixel(x, y, 100, OmrMeasurePostProcessor.SYMBOL);
            }
        assertTrue(bodies(word("espressivo")).isEmpty());
    }

    @Test
    public void rawPlanesRemainImmutable() {
        wordBody();
        byte[] a = gray.clone(), b = labels.clone();
        marks(true);
        assertArrayEquals(a, gray);
        assertArrayEquals(b, labels);
    }

    private ScoreNoteEvent note(int marks) {
        return new ScoreNoteEvent(
                0,
                211f / W,
                0,
                0,
                1,
                179f / H,
                false,
                1,
                1,
                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                0,
                3,
                0,
                marks,
                ScoreNoteEvent.CLEF_TREBLE);
    }

    private List<ScoreNoteEvent> apply(List<ScoreNoteEvent> notes) {
        return PrintedWordArticulations.apply(
                word("espressivo"),
                List.of(new PlayingTechniqueDetector.Staff(150, 198, GAP, 0, 1)),
                List.of(new MeasureRegion(0, 1, .15f, .21f)),
                notes,
                labels,
                gray,
                W,
                H);
    }

    @Test
    public void postOcrRemovalRetainsEveryNonArticulationFieldAndOrnament() {
        wordBody();
        var before = note(NoteArticulation.STACCATISSIMO | NoteOrnament.MORDENT);
        assertEquals(
                List.of(before.withArticulations(NoteOrnament.MORDENT)), apply(List.of(before)));
    }

    @Test
    public void mismatchedCurrentLowBitsCannotBeClearedByThePostpass() {
        wordBody();
        var before = note(NoteArticulation.STACCATISSIMO | NoteArticulation.STACCATO);
        assertEquals(List.of(before), apply(List.of(before)));
    }

    @Test
    public void postpassCannotInventMissingArticulationBits() {
        wordBody();
        var before = note(NoteOrnament.MORDENT);
        assertEquals(List.of(before), apply(List.of(before)));
    }

    @Test
    public void postpassPreservesIndependentSameBitEvidence() {
        wordBody();
        for (int y = 149; y <= 162; y++) {
            int radius = Math.round(3 * (162 - y) / 13f);
            for (int x = 211 - radius; x <= 211 + radius; x++)
                pixel(x, y, 35, OmrMeasurePostProcessor.SYMBOL);
        }
        var before = note(NoteArticulation.STACCATISSIMO);
        assertEquals(List.of(before), apply(List.of(before)));
    }

    @Test
    public void postpassPreservesIndependentOtherBitEvidence() {
        wordBody();
        for (int y = 153; y <= 157; y++)
            for (int x = 209; x <= 213; x++) pixel(x, y, 35, OmrMeasurePostProcessor.SYMBOL);
        var before = note(NoteArticulation.STACCATISSIMO | NoteArticulation.STACCATO);
        assertEquals(
                List.of(before.withArticulations(NoteArticulation.STACCATO)),
                apply(List.of(before)));
    }

    @Test
    public void unmatchedStaffCannotEstablishThePostpassBaseline() {
        wordBody();
        var before = note(NoteArticulation.STACCATISSIMO);
        var result =
                PrintedWordArticulations.apply(
                        word("espressivo"),
                        List.of(new PlayingTechniqueDetector.Staff(150, 198, GAP, 1, 2)),
                        List.of(new MeasureRegion(0, 1, .15f, .21f)),
                        List.of(before),
                        labels,
                        gray,
                        W,
                        H);
        assertEquals(List.of(before), result);
    }

    @Test
    public void missingMeasureCannotEstablishThePostpassBaseline() {
        wordBody();
        var before = note(NoteArticulation.STACCATISSIMO);
        assertEquals(
                List.of(before),
                PrintedWordArticulations.apply(
                        word("espressivo"),
                        List.of(new PlayingTechniqueDetector.Staff(150, 198, GAP, 0, 1)),
                        List.of(),
                        List.of(before),
                        labels,
                        gray,
                        W,
                        H));
    }

    @Test
    public void postpassIsIdempotent() {
        wordBody();
        var once = apply(List.of(note(NoteArticulation.STACCATISSIMO)));
        assertEquals(once, apply(once));
    }
}
