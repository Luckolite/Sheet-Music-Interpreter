// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original short shallow connectors and independent printed five-rule scenes. */
public class ObservedStaffSlideOriginalsTest {
    private static final int W = 460, H = 240;

    private static final class Scene {
        final byte[] gray = new byte[W * H];
        final List<NoteSlideDetector.Head> heads = new ArrayList<>();
        final List<NoteSlideDetector.Staff> staffs;
        final int dx, dy;

        Scene(int dx, int dy, boolean connector, boolean bow, boolean rising) {
            this.dx = dx;
            this.dy = dy;
            Arrays.fill(gray, (byte) 255);
            staffs = List.of(new NoteSlideDetector.Staff(80 + dy, 136 + dy, 14));
            for (int line = 0; line < 5; line++)
                for (int x = 16; x < W - 16; x++) {
                    double center = 80 + dy + line * 14 + (x - W * .5) * .003;
                    for (int y = (int) Math.floor(center - 1.5); y <= center + 1.5; y++)
                        if (y >= 0 && y < H) ink(x, y, Math.abs(y - center), 1.25);
                }
            double y0 = rising ? 116 : 100, slope = rising ? -1d / 6 : 1d / 6;
            heads.add(new NoteSlideDetector.Head(120 + dx, (float) y0 + dy, 14, 0, 0));
            heads.add(
                    new NoteSlideDetector.Head(204 + dx, (float) (y0 + 84 * slope) + dy, 14, 0, 0));
            if (connector)
                for (int x = 133 + dx; x <= 191 + dx; x++) {
                    double center = y0 + dy + (x - 120 - dx) * slope;
                    for (int y = (int) Math.floor(center - 2); y <= center + 2; y++)
                        ink(x, y, Math.abs(y - center), 1.25);
                }
            if (bow)
                for (int x = 125 + dx; x <= 200 + dx; x++) {
                    double t = (x - 125 - dx) / 75d, center = 94 + dy - 12 * 4 * t * (1 - t);
                    for (int y = (int) Math.floor(center - 2); y <= center + 2; y++)
                        ink(x, y, Math.abs(y - center), 1.1);
                }
        }

        void ink(int x, int y, double distance, double radius) {
            if (x < 0 || x >= W || y < 0 || y >= H) return;
            int value = (int) Math.round(255 * Math.max(0, Math.min(1, distance - radius + .5)));
            if (value < (gray[y * W + x] & 255)) gray[y * W + x] = (byte) value;
        }

        List<NoteSlideDetector.Stroke> found() {
            byte[] before = gray.clone();
            var roster = List.copyOf(heads);
            var found = NoteSlideDetector.detect(gray, W, H, staffs, heads);
            assertArrayEquals(before, gray);
            assertEquals(roster, heads);
            return found;
        }

        void positive() {
            var found = found();
            assertEquals(found.toString(), 1, found.size());
            assertEquals(1, found.get(0).noteIndex());
            assertTrue(found.get(0).connected());
        }

        void negative() {
            var found = found();
            assertTrue(found.toString(), found.isEmpty());
        }
    }

    @Test
    public void ascendingShortThickFragmentsRejoin() {
        new Scene(0, 0, true, false, true).positive();
    }

    @Test
    public void descendingShortThickFragmentsRejoin() {
        new Scene(0, 0, true, false, false).positive();
    }

    @Test
    public void translationPreservesRecognition() {
        new Scene(53, 37, true, false, true).positive();
    }

    @Test
    public void separateCurvedSlurDoesNotHideStraightLine() {
        new Scene(0, 0, true, true, true).positive();
    }

    @Test
    public void staffAloneIsNotAConnector() {
        new Scene(0, 0, false, false, true).negative();
    }

    @Test
    public void curvedSlurAloneIsNotStraight() {
        new Scene(0, 0, false, true, true).negative();
    }

    @Test
    public void missingInkAwayFromStaffIsNotInvented() {
        var s = new Scene(0, 0, true, false, true);
        for (int x = 141; x <= 150; x++)
            for (int y = 111; y <= 117; y++) s.gray[y * W + x] = (byte) 255;
        s.negative();
    }

    @Test
    public void sourceChordRemainsAmbiguous() {
        var s = new Scene(0, 0, true, false, true);
        s.heads.add(new NoteSlideDetector.Head(120, 130, 14, 0, 0));
        s.negative();
    }

    @Test
    public void interveningAttackCannotBeSkipped() {
        var s = new Scene(0, 0, true, false, true);
        s.heads.add(new NoteSlideDetector.Head(160, 136, 14, 0, 0));
        s.negative();
    }

    @Test
    public void sourceInAnotherMeasureCannotBind() {
        var s = new Scene(0, 0, true, false, true);
        s.heads.set(0, new NoteSlideDetector.Head(120, 116, 14, 0, 1));
        s.negative();
    }

    @Test
    public void sourceOnAnotherStaffCannotBind() {
        var s = new Scene(0, 0, true, false, true);
        s.heads.set(0, new NoteSlideDetector.Head(120, 116, 14, 1, 0));
        s.negative();
    }

    @Test
    public void freshCallDoesNotReuseADeletedConnector() {
        var s = new Scene(0, 0, true, false, true);
        s.positive();
        var blank = new Scene(0, 0, false, false, true);
        System.arraycopy(blank.gray, 0, s.gray, 0, s.gray.length);
        s.negative();
    }

    @Test
    public void sharedCleanCallerUsesTheSameRepair() {
        var s = new Scene(0, 0, true, true, true);
        byte[] original = s.gray.clone();
        byte[] clean = NoteSlideDetector.removeStaffLines(s.gray, W, H, s.staffs);
        var found =
                NoteSlideDetector.detectWithRemovedStaffLines(
                        s.gray, W, H, s.staffs, s.heads, clean);
        assertEquals(found.toString(), 1, found.size());
        assertTrue(found.get(0).connected());
        assertEquals(1, found.get(0).noteIndex());
        assertArrayEquals(original, s.gray);
    }

    @Test
    public void observedFrameCorrectsAnAscendingSeedOffset() {
        shiftedSeed(true);
    }

    @Test
    public void observedFrameCorrectsADescendingSeedOffset() {
        shiftedSeed(false);
    }

    private void shiftedSeed(boolean rising) {
        var s = new Scene(0, 0, true, true, rising);
        byte[] original = s.gray.clone();
        var shifted = List.of(new NoteSlideDetector.Staff(83, 139, 13.75f));
        var found = NoteSlideDetector.detect(s.gray, W, H, shifted, s.heads);
        assertEquals(found.toString(), 1, found.size());
        assertTrue(found.get(0).connected());
        assertEquals(1, found.get(0).noteIndex());
        assertArrayEquals(original, s.gray);
    }

    @Test
    public void normalPortableCallerAddsOnlyTheLinkedSlide() {
        var s = new Scene(0, 0, true, true, true);
        var source =
                new ScoreNoteEvent(0, 120f / W, 5, 0, 1, 116f / H, false, 0, 0, 2, 2).withClef(30);
        var target =
                new ScoreNoteEvent(0, 204f / W, 7, 0, 1, 102f / H, false, 0, 0, 2, 1).withClef(30);
        var notes = List.of(source, target);
        byte[] original = s.gray.clone();
        var found =
                PortableNoteOrnaments.applyWithAlignedStaffs(
                        new PortableOrnamentGlyphs(),
                        s.gray,
                        W,
                        H,
                        List.of(new MeasureRegion(0, 1, 60f / H, 150f / H)),
                        notes,
                        List.of(),
                        List.of(new PlayingTechniqueDetector.Staff(83, 139, 13.75f, 0, 1)));
        assertEquals(
                List.of(
                        source,
                        target.withArticulations(NoteOrnament.SLIDE | NoteOrnament.FROM_PREVIOUS)),
                found);
        assertArrayEquals(original, s.gray);
        assertEquals(2, ScoreNoteTiming.writtenDurationBeats(found.get(0)), 0);
        assertEquals(1, ScoreNoteTiming.writtenDurationBeats(found.get(1)), 0);
    }

    @Test
    public void shallowRasterPortsUseTheCombinedLine() {
        for (boolean rising : new boolean[] {false, true})
            for (double phase : new double[] {0, .25, .5, .75}) shallow(rising, phase, false);
    }

    @Test
    public void unevenRuleProfilesKeepBothCompleteProposals() {
        for (boolean rising : new boolean[] {false, true})
            for (double phase : new double[] {0, .25, .5, .75}) shallow(rising, phase, true);
    }

    private void shallow(boolean rising, double phase, boolean unevenRule) {
        var s = new Scene(0, 0, false, false, rising);
        double slope = rising ? -.1 : .1, center = 108 + phase, y0 = center + (120 - 164) * slope;
        s.heads.set(0, new NoteSlideDetector.Head(120, (float) y0, 14, 0, 0));
        s.heads.set(1, new NoteSlideDetector.Head(204, (float) (y0 + 84 * slope), 14, 0, 0));
        if (unevenRule)
            for (int x = 16; x < W - 16; x++)
                for (int y = 106; y <= 109; y++) if (y >= 107 && y <= 109) s.gray[y * W + x] = 0;
        for (int x = 133; x <= 191; x++) {
            double cy = center + (x - 164) * slope;
            for (int y = (int) Math.floor(cy - 2); y <= cy + 2; y++)
                s.ink(x, y, Math.abs(y - cy), 1.25);
        }
        s.positive();
    }
}
