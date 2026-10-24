// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Source-free antialiased staff/beam compositions exercise both production capture paths. */
public class ObservedBeamCaptureOriginalsTest {
    private record Sample(ObservedBeamSlurOriginalsTest.Field field, StaffPitchTrack track) {}

    private static Sample sample(int base, int delta, float phase) {
        var f = new ObservedBeamSlurOriginalsTest.Field(1, false, 1, phase);
        for (int x = 102; x <= 163; x++)
            for (int y = 150; y <= 198; y++) f.gray[y * f.w + x] = (byte) 255;
        // Integrate continuous rectangular ink across pixel cells. Staff and beam share ink.
        for (int line = 0; line < 5; line++) {
            float center = base + phase - line * 16;
            for (int y = (int) Math.floor(center - 2); y <= center + 2; y++) {
                float cover =
                        Math.max(
                                0,
                                Math.min(y + .5f, center + 1.1f)
                                        - Math.max(y - .5f, center - 1.1f));
                int value = Math.round(255 - 195 * cover);
                for (int x = 10; x < f.w - 10; x++)
                    f.gray[y * f.w + x] = (byte) Math.min(f.gray[y * f.w + x] & 255, value);
            }
        }
        for (int x = 102; x <= 163; x++) {
            double end = 190 + delta * (x - 100) / 64. + phase;
            for (int y = (int) Math.floor(end - 10); y <= end + 2; y++) {
                double cover =
                        Math.max(0, Math.min(y + .5, end + .5) - Math.max(y - .5, end - 7.5));
                f.gray[y * f.w + x] = (byte) Math.round((f.gray[y * f.w + x] & 255) * (1 - cover));
            }
        }
        var track =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(
                                new float[] {0, base + phase, 16},
                                new float[] {f.w - 1, base + phase, 16}));
        return new Sample(f, track);
    }

    private static PrintedSlurPageCapture.Result geometry(
            Sample sample, List<ScoreNoteEvent> notes) {
        var f = sample.field();
        return PrintedSlurPageCapture.captureGeometry(
                7,
                2,
                f.gray,
                null,
                f.w,
                f.h,
                List.of(
                        new PrintedSlurPageCapture.Frame<>(
                                0, 30, f.w - 1, 260, sample.track(), f.heads)),
                notes);
    }

    private static PrintedSlurPageCapture.Result timed(
            Sample sample,
            List<ScoreNoteEvent> notes,
            int endPitch,
            Set<PrintedSlurOwners.Tie> ties) {
        var f = sample.field();
        var heads = new ArrayList<PrintedSlurOwners.Head>();
        for (var head : f.heads)
            heads.add(
                    new PrintedSlurOwners.Head(
                            head.owner(),
                            head.x(),
                            head.y(),
                            0,
                            1,
                            1,
                            head.owner() * 100L,
                            head.owner() == 0 ? 60 : endPitch));
        return PrintedSlurPageCapture.capture(
                7,
                2,
                f.gray,
                null,
                f.w,
                f.h,
                List.of(
                        new PrintedSlurPageCapture.Frame<>(
                                0, 30, f.w - 1, 260, sample.track(), heads)),
                ties,
                notes);
    }

    private static void rejectsOnlyProvedBeam(int base, int delta, float phase) {
        var sample = sample(base, delta, phase);
        var f = sample.field();
        byte[] before = f.gray.clone();
        var legacy = geometry(sample, null);
        var corrected = geometry(sample, f.notes);
        assertFalse(legacy.limitReached());
        assertFalse(corrected.limitReached());
        assertEquals(1, legacy.source().candidates().size());
        assertEquals(0, corrected.source().candidates().size());
        // The existing empty-source contract retains note count without assigning arc origins.
        assertEquals(legacy.source().noteOrigins().size(), corrected.source().noteOrigins().size());
        assertTrue(corrected.source().noteOrigins().stream().allMatch(Optional::isEmpty));
        assertArrayEquals(before, f.gray);
    }

    @Test
    public void shallowBeamAtIntegerPhaseIsNotASlur() {
        rejectsOnlyProvedBeam(214, -2, 0);
    }

    @Test
    public void shallowBeamAtQuarterPhaseIsNotASlur() {
        rejectsOnlyProvedBeam(214, -2, .25f);
    }

    @Test
    public void shallowBeamAtThreeQuarterPhaseIsNotASlur() {
        rejectsOnlyProvedBeam(214, -2, .75f);
    }

    @Test
    public void descendingBeamAtIntegerPhaseIsNotASlur() {
        rejectsOnlyProvedBeam(214, -3, 0);
    }

    @Test
    public void descendingBeamAtQuarterPhaseIsNotASlur() {
        rejectsOnlyProvedBeam(214, -3, .25f);
    }

    @Test
    public void descendingBeamAtHalfPhaseIsNotASlur() {
        rejectsOnlyProvedBeam(214, -3, .5f);
    }

    @Test
    public void descendingBeamAtThreeQuarterPhaseIsNotASlur() {
        rejectsOnlyProvedBeam(214, -3, .75f);
    }

    @Test
    public void steeperBeamRetainsTheSameLiteralAuthority() {
        rejectsOnlyProvedBeam(214, -4, .25f);
    }

    @Test
    public void translatedStaffKeepsBeamOwnership() {
        rejectsOnlyProvedBeam(216, -2, .5f);
    }

    @Test
    public void absentBeamMetadataPreservesTheOriginalCapture() {
        var sample = sample(214, -3, 0);
        var f = sample.field();
        f.notes.set(0, f.note(0, 0, -1, 0, 0, 1));
        assertEquals(geometry(sample, null), geometry(sample, f.notes));
    }

    @Test
    public void incompleteShaftPreservesTheCandidate() {
        var sample = sample(214, -3, 0);
        var f = sample.field();
        f.erase(100, 101, 150, 150);
        var before = geometry(sample, null);
        assertEquals(1, before.source().candidates().size());
        assertEquals(before, geometry(sample, f.notes));
    }

    @Test
    public void timedCaptureAlsoRejectsTheSameProvedBeam() {
        var sample = sample(214, -3, 0);
        var before = timed(sample, null, 62, Set.of());
        var after = timed(sample, sample.field().notes, 62, Set.of());
        assertEquals(1, before.source().spans().size());
        assertTrue(after.source().spans().isEmpty());
        assertTrue(after.source().candidates().isEmpty());
        assertEquals(before.source().noteOrigins().size(), after.source().noteOrigins().size());
    }

    @Test
    public void unresolvedPitchCannotReissueTheProvedBeamAsACandidate() {
        var sample = sample(214, -3, 0);
        var before = timed(sample, null, -1, Set.of());
        var after = timed(sample, sample.field().notes, -1, Set.of());
        assertEquals(1, before.source().candidates().size());
        assertTrue(after.source().candidates().isEmpty());
    }

    @Test
    public void retainedTieExclusionKeepsItsOriginalStatus() {
        var sample = sample(214, -3, 0);
        var ties = Set.of(new PrintedSlurOwners.Tie(0, 1));
        var before = timed(sample, null, 60, ties);
        assertTrue(before.source().spans().isEmpty());
        assertEquals(before, timed(sample, sample.field().notes, 60, ties));
    }

    @Test
    public void sourceCountMismatchFailsBeforeCapture() {
        var sample = sample(214, -3, 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> geometry(sample, List.of(sample.field().notes.get(0))));
    }
}
