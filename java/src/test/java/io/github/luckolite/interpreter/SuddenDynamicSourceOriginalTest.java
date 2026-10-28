// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original OCR hierarchy and literal raw ink; no score, model, timing or private source. */
public final class SuddenDynamicSourceOriginalTest {
    static final int W = 300, H = 200;

    static final class Fixture {
        final byte[] raw = new byte[W * H];
        final OcrText.Line line;
        final OcrText read;
        final SuddenDynamicSource.Root root;
        final PlayingTechniqueDetector.Word whole, child, duplicate;

        Fixture(String label, String qualifier, String level, int elements) {
            Arrays.fill(raw, (byte) 255);
            for (int y = 80; y < 90; y++) {
                for (int x = 70; x < 90; x++) raw[y * W + x] = 0;
                for (int x = 130; x < 150; x++) raw[y * W + x] = 0;
            }
            var es = new ArrayList<OcrText.Element>();
            es.add(new OcrText.Element(qualifier, new OcrText.Box(60, 70, 100, 100), List.of()));
            es.add(new OcrText.Element(level, new OcrText.Box(120, 70, 160, 100), List.of()));
            if (elements == 3)
                es.add(new OcrText.Element("mf", new OcrText.Box(170, 70, 195, 100), List.of()));
            line = new OcrText.Line(label, new OcrText.Box(50, 60, 200, 110), es);
            read =
                    new OcrText(
                            label,
                            List.of(new OcrText.Block(label, line.box(), List.of(line))),
                            OcrText.Grouping.DETECTED_LINES);
            root = new SuddenDynamicSource.Root(raw, W, H);
            whole = word(label, 60, 70, 160, 100);
            child = word(label, 125, 75, 155, 95);
            duplicate = word(label, 125, 75, 155, 95);
        }

        SuddenDynamicSource.Group group() {
            return SuddenDynamicSource.capture(root, read, line, 0, 0, W, H, 1);
        }

        List<PlayingTechniqueDetector.Word> run(List<PlayingTechniqueDetector.Word> words) {
            return SuddenDynamicSource.normalize(
                    words, root, Collections.singletonList(group()), raw, W, H);
        }
    }

    static PlayingTechniqueDetector.Word word(String s, int l, int t, int r, int b) {
        return new PlayingTechniqueDetector.Word(
                s, (float) l / W, (float) t / H, (float) r / W, (float) b / H);
    }

    static Fixture good() {
        return new Fixture("sub. mp", "sub.", "mp", 2);
    }

    @Test
    public void oneParentOneChildRetainsExactQualifiedChild() {
        var f = good();
        var out = f.run(List.of(f.whole, f.child, f.duplicate));
        assertEquals(1, out.size());
        assertSame(f.child, out.get(0));
        assertEquals("sub. mp", out.get(0).text());
        assertTrue(ScoreDynamicsDetector.isSuddenLevel(out.get(0).text()));
        assertEquals(-4, ScoreDynamicsDetector.level(out.get(0).text()), 0);
    }

    @Test
    public void qualifierEvidenceSurvives() {
        var f = good();
        var out = f.run(List.of(f.whole, f.child));
        assertSame(f.child, out.get(0));
        assertEquals(f.line.elements().get(0).text(), "sub.");
        assertEquals(2, f.line.elements().size());
    }

    @Test
    public void originalOnePrintedPhraseHasOneStrength() {
        var f = good();
        var words = List.of(f.whole, f.child);
        var staffs = List.of(new PlayingTechniqueDetector.Staff(10, 50, 10, 0, 1));
        var measures = List.of(new MeasureRegion(0, 1, 0, 1));
        var before =
                ScoreDynamicsDetector.detectWithEvidence(
                        words, staffs, measures, List.of(), f.raw, W, H);
        var after =
                ScoreDynamicsDetector.detectWithEvidence(
                        f.run(words), staffs, measures, List.of(), f.raw, W, H);
        assertEquals(2, before.changes().size());
        assertEquals(1, after.changes().size());
        assertEquals(before.events(), after.events());
        assertEquals(1, ExpressiveDirectionText.parse(f.child.text()).size());
    }

    @Test
    public void adjacentGenuineMarksRemain() {
        var f = good();
        var other = word("sub. mp", 205, 70, 245, 100);
        assertEquals(List.of(f.child, other), f.run(List.of(f.whole, f.child, other)));
    }

    @Test
    public void sameTimeOtherStaffRemains() {
        var f = good();
        var other = word("sub. mp", 125, 135, 155, 160);
        assertEquals(List.of(f.child, other), f.run(List.of(f.whole, f.child, other)));
    }

    @Test
    public void multipleLevelsCannotBeCollapsed() {
        var f = new Fixture("sub. mp mf", "sub.", "mp", 3);
        var words = List.of(f.whole, f.child);
        assertSame(words, f.run(words));
    }

    @Test
    public void compoundFpAndSfzCannotBeCollapsed() {
        for (String level : List.of("fp", "sfz")) {
            var f = new Fixture("sub. " + level, "sub.", level, 2);
            var words = List.of(f.whole, f.child);
            assertSame(words, f.run(words));
        }
    }

    @Test
    public void unsupportedQualifierCannotBeCollapsed() {
        var f = new Fixture("dolce mp", "dolce", "mp", 2);
        var words = List.of(f.whole, f.child);
        assertSame(words, f.run(words));
    }

    @Test
    public void overlappingDifferentRootCannotAuthorize() {
        var f = good();
        var other = good();
        var words = List.of(f.whole, f.child);
        assertSame(
                words,
                SuddenDynamicSource.normalize(words, f.root, List.of(other.group()), f.raw, W, H));
    }

    @Test
    public void missingChildDoesNotMoveParent() {
        var f = good();
        var words = List.of(f.whole);
        assertSame(words, f.run(words));
    }

    @Test
    public void ambiguousChildBoxesDoNotChooseAnAnchor() {
        var f = good();
        var alternative = word("sub. mp", 124, 74, 156, 96);
        var words = List.of(f.whole, f.child, alternative);
        assertSame(words, f.run(words));
    }

    @Test
    public void missingLiteralInkRefuses() {
        var f = good();
        Arrays.fill(f.raw, (byte) 255);
        var words = List.of(f.whole, f.child);
        assertSame(words, f.run(words));
    }

    @Test
    public void foreignParentIdentityRefuses() {
        var f = good();
        var copied = new OcrText.Line(f.line.text(), f.line.box(), f.line.elements());
        assertNull(SuddenDynamicSource.capture(f.root, f.read, copied, 0, 0, W, H, 1));
    }

    @Test
    public void inputMutationRefuses() {
        var f = good();
        var group = f.group();
        f.raw[0] = 0;
        var words = List.of(f.whole, f.child);
        assertSame(
                words, SuddenDynamicSource.normalize(words, f.root, List.of(group), f.raw, W, H));
    }

    @Test
    public void unsupportedGroupingRefuses() {
        var f = good();
        assertNull(
                SuddenDynamicSource.capture(
                        f.root,
                        new OcrText(f.read.text(), f.read.blocks()),
                        f.line,
                        0,
                        0,
                        W,
                        H,
                        1));
    }

    @Test
    public void candidateLimitsRefuseAtomically() {
        var f = good();
        var words = new ArrayList<PlayingTechniqueDetector.Word>();
        for (int i = 0; i < 257; i++) words.add(f.whole);
        assertSame(words, f.run(words));
    }

    @Test
    public void competingParentBodiesRefuseInEitherOrder() {
        var f = good();
        var alternate =
                new OcrText.Line(
                        "sub. mp",
                        f.line.box(),
                        List.of(
                                f.line.elements().get(0),
                                new OcrText.Element(
                                        "mp", new OcrText.Box(121, 70, 161, 100), List.of())));
        var read =
                new OcrText(
                        "sub. mp",
                        List.of(new OcrText.Block("sub. mp", alternate.box(), List.of(alternate))),
                        OcrText.Grouping.DETECTED_LINES);
        var rival = SuddenDynamicSource.capture(f.root, read, alternate, 0, 0, W, H, 1);
        assertNotNull(rival);
        var words = List.of(f.whole, f.child);
        for (var groups : List.of(List.of(f.group(), rival), List.of(rival, f.group())))
            assertSame(words, SuddenDynamicSource.normalize(words, f.root, groups, f.raw, W, H));
    }

    @Test
    public void equivalentRepeatedRoleObservationsKeepSameChild() {
        var f = good();
        var out =
                SuddenDynamicSource.normalize(
                        List.of(f.whole, f.child),
                        f.root,
                        List.of(f.group(), f.group()),
                        f.raw,
                        W,
                        H);
        assertEquals(1, out.size());
        assertSame(f.child, out.get(0));
    }

    @Test
    public void parentPixelBudgetRefusesWithoutPartialRemoval() {
        var f = good();
        var groups = new ArrayList<SuddenDynamicSource.Group>();
        for (int i = 0; i < 128; i++) groups.add(f.group());
        var words = List.of(f.whole, f.child);
        assertSame(words, SuddenDynamicSource.normalize(words, f.root, groups, f.raw, W, H));
        groups.add(f.group());
        assertSame(words, SuddenDynamicSource.normalize(words, f.root, groups, f.raw, W, H));
    }

    @Test
    public void cachedLineInDistinctActualFramesIsNotCollapsed() {
        var f = good();
        var frames = new HashSet<SuddenDynamicSource.ReadFrame>();
        assertTrue(frames.add(new SuddenDynamicSource.ReadFrame(f.line, 0, 0, W, H, 1)));
        assertFalse(frames.add(new SuddenDynamicSource.ReadFrame(f.line, 0, 0, W, H, 1)));
        assertTrue(frames.add(new SuddenDynamicSource.ReadFrame(f.line, 1, 0, W, H, 1)));
        assertTrue(frames.add(new SuddenDynamicSource.ReadFrame(f.line, 0, 1, W, H, 1)));
        assertTrue(frames.add(new SuddenDynamicSource.ReadFrame(f.line, 0, 0, W - 1, H, 1)));
        assertTrue(frames.add(new SuddenDynamicSource.ReadFrame(f.line, 0, 0, W, H - 1, 1)));
        assertTrue(frames.add(new SuddenDynamicSource.ReadFrame(f.line, 0, 0, W, H, 2)));
        var copied = new OcrText.Line(f.line.text(), f.line.box(), f.line.elements());
        assertTrue(frames.add(new SuddenDynamicSource.ReadFrame(copied, 0, 0, W, H, 1)));
        var first = SuddenDynamicSource.capture(f.root, f.read, f.line, 0, 0, W, H, 1);
        var second = SuddenDynamicSource.capture(f.root, f.read, f.line, 1, 0, W, H, 1);
        assertNotNull(first);
        assertNotNull(second);
        var words = List.of(f.whole, f.child);
        assertSame(
                words,
                SuddenDynamicSource.normalize(words, f.root, List.of(first, second), f.raw, W, H));
    }

    @Test
    public void actualReadCropCannotBeEscapedOrClipped() {
        var f = good();
        assertNotNull(SuddenDynamicSource.capture(f.root, f.read, f.line, 0, 0, 200, 110, 1));
        assertNull(SuddenDynamicSource.capture(f.root, f.read, f.line, 0, 0, 199, 110, 1));
        assertNull(SuddenDynamicSource.capture(f.root, f.read, f.line, 0, 0, 200, 109, 1));
        assertNull(SuddenDynamicSource.capture(f.root, f.read, f.line, -1, 0, 200, 110, 1));
        assertNull(SuddenDynamicSource.capture(f.root, f.read, f.line, 0, 0, W + 1, H, 1));
        var escaping =
                new OcrText.Line(
                        f.line.text(),
                        f.line.box(),
                        List.of(
                                f.line.elements().get(0),
                                new OcrText.Element(
                                        "mp", new OcrText.Box(120, 70, 205, 100), List.of())));
        var read =
                new OcrText(
                        escaping.text(),
                        List.of(
                                new OcrText.Block(
                                        escaping.text(), escaping.box(), List.of(escaping))),
                        OcrText.Grouping.DETECTED_LINES);
        assertNull(SuddenDynamicSource.capture(f.root, read, escaping, 0, 0, 200, 110, 1));
    }

    @Test
    public void sparseChildCannotSuppressDensityAdmittedParent() {
        var f = good();
        Arrays.fill(f.raw, (byte) 255);
        for (int y = 80; y < 90; y++) for (int x = 70; x < 90; x++) f.raw[y * W + x] = 0;
        for (int x = 130; x < 134; x++) f.raw[80 * W + x] = 0;
        var root = new SuddenDynamicSource.Root(f.raw, W, H);
        var group = SuddenDynamicSource.capture(root, f.read, f.line, 0, 0, W, H, 1);
        assertTrue(ScoreDynamicsDetector.containsInk(f.whole, f.raw, W, H));
        assertFalse(ScoreDynamicsDetector.containsInk(f.child, f.raw, W, H));
        var words = List.of(f.whole, f.child);
        assertSame(words, SuddenDynamicSource.normalize(words, root, List.of(group), f.raw, W, H));
    }
}
