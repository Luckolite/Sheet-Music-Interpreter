// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrintedSlurPageCaptureTest {
    private static final int W = 240, H = 220;

    private byte[] pixels() {
        var gray = new byte[W * H];
        Arrays.fill(gray, (byte) 255);
        for (int x = 40; x <= 190; x++) {
            double t = 2.0 * (x - 40) / 150 - 1;
            gray[(int) Math.round(55 + 14 * t * t) * W + x] = 0;
        }
        for (int y = 82; y <= 88; y++) for (int x = 36; x <= 44; x++) gray[y * W + x] = 0;
        for (int y = 92; y <= 98; y++) for (int x = 186; x <= 194; x++) gray[y * W + x] = 0;
        return gray;
    }

    private PrintedSlurPageCapture.Frame frame(int voice, int endPitch) {
        var track =
                StaffPitchTrack.fromVerifiedSamples(
                        List.of(new float[] {0, 150, 12}, new float[] {W - 1, 150, 12}));
        return new PrintedSlurPageCapture.Frame(
                0,
                20,
                W - 1,
                H - 1,
                track,
                List.of(
                        new PrintedSlurOwners.Head(0, 40, 85, 0, 1, 1, 0, 60),
                        new PrintedSlurOwners.Head(1, 190, 95, 0, 1, voice, 100, endPitch)));
    }

    private PrintedSlurPageCapture.Result capture(
            int page, int voice, int pitch, Set<PrintedSlurOwners.Tie> ties) {
        return PrintedSlurPageCapture.capture(
                page, 2, pixels(), null, W, H, List.of(frame(voice, pitch)), ties);
    }

    @Test
    public void rawDetectionRetainsPhysicalPageAndNoteIdentities() {
        var result = capture(9, 1, 62, Set.of());
        assertFalse(result.limitReached());
        assertEquals(Integer.valueOf(1), result.statuses().get(PrintedSlurOwners.Status.RESOLVED));
        var source = result.source();
        assertEquals(1, source.spans().size());
        assertEquals(new ScoreSlurPages.Anchor(9, 0), source.spans().get(0).start());
        assertEquals(new ScoreSlurPages.Anchor(9, 1), source.spans().get(0).end());
        assertEquals(1, source.resolve().resolved().size());
    }

    @Test
    public void joinedSelectionOrderDoesNotBecomePhysicalPageOrder() {
        var page9 = capture(9, 1, 62, Set.of()).source();
        var page2 = capture(2, 1, 62, Set.of()).source();
        var joined = ScoreSlurSource.join(List.of(page9, page2));
        assertEquals(2, joined.resolve().resolved().size());
        assertEquals(new ScoreSlurPages.Anchor(9, 0), joined.noteOrigins().get(0).orElseThrow());
        assertEquals(new ScoreSlurPages.Anchor(2, 0), joined.noteOrigins().get(2).orElseThrow());
        assertEquals(2, joined.resolve().resolved().get(1).startOwner());
    }

    @Test
    public void distinctVoiceLabelsKeepTheProvedPrintedSourceArc() {
        var result = capture(0, 2, 62, Set.of());
        assertEquals(1, result.source().spans().size());
        assertEquals(2, result.source().noteOrigins().size());
        assertEquals(Integer.valueOf(1), result.statuses().get(PrintedSlurOwners.Status.RESOLVED));
    }

    @Test
    public void retainedTieIsExcludedBeforeCapture() {
        var result = capture(0, 1, 60, Set.of(new PrintedSlurOwners.Tie(0, 1)));
        assertTrue(result.source().spans().isEmpty());
        assertEquals(
                Integer.valueOf(1),
                result.statuses().get(PrintedSlurOwners.Status.TIE_ALREADY_RETAINED));
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateOwnershipAcrossStaffFramesIsRejected() {
        PrintedSlurPageCapture.capture(
                0, 2, pixels(), null, W, H, List.of(frame(1, 62), frame(1, 62)), Set.of());
    }

    @Test(expected = IllegalArgumentException.class)
    public void pageLocalHeadCannotEscapeRetainedNoteCount() {
        PrintedSlurPageCapture.capture(0, 1, pixels(), null, W, H, List.of(frame(1, 62)), Set.of());
    }
}
