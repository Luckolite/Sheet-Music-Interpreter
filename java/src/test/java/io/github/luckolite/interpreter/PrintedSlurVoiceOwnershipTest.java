// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original raw bow and explicit head clocks; voice labels are never filled or reassigned. */
public class PrintedSlurVoiceOwnershipTest {
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

    private StaffPitchTrack track() {
        return StaffPitchTrack.fromVerifiedSamples(
                List.of(new float[] {0, 150, 12}, new float[] {W - 1, 150, 12}));
    }

    private PrintedSlurOwners.Head first(int voice, long clock, int pitch) {
        return new PrintedSlurOwners.Head(0, 40, 85, 0, 1, voice, clock, pitch);
    }

    private PrintedSlurOwners.Head last(int voice, long clock, int pitch) {
        return new PrintedSlurOwners.Head(1, 190, 95, 0, 1, voice, clock, pitch);
    }

    private PrintedSlurOwners.Result resolve(
            List<PrintedSlurOwners.Head> heads, Set<PrintedSlurOwners.Tie> ties) {
        var gray = pixels();
        var frame = track();
        var bows = PrintedSlurContours.find(gray, null, W, H, 0, 20, W - 1, H - 1, frame).bows();
        assertEquals(1, bows.size());
        return PrintedSlurOwners.resolve(
                "original-voice-independent-phrase",
                7,
                bows.get(0),
                heads,
                ties,
                gray,
                W,
                H,
                frame);
    }

    @Test
    public void uniqueSameStaffHeadsCanConnectDifferentVoices() {
        var a = first(1, 0, 60);
        var b = last(2, 100, 62);
        var result = resolve(List.of(a, b), Set.of());
        assertEquals(PrintedSlurOwners.Status.RESOLVED, result.status());
        assertEquals(new ScoreSlurPages.Anchor(7, 0), result.span().orElseThrow().start());
        assertEquals(1, a.voice());
        assertEquals(2, b.voice());
    }

    @Test
    public void unknownVoiceLabelsDoNotEraseProvedPrintedEndpoints() {
        var a = first(0, 0, 60);
        var b = last(0, 100, 62);
        assertEquals(PrintedSlurOwners.Status.RESOLVED, resolve(List.of(a, b), Set.of()).status());
        assertEquals(0, a.voice());
        assertEquals(0, b.voice());
    }

    @Test
    public void oneMissingVoiceLabelDoesNotInventACopyOfTheOther() {
        var a = first(3, 0, 60);
        var b = last(0, 100, 62);
        assertEquals(PrintedSlurOwners.Status.RESOLVED, resolve(List.of(a, b), Set.of()).status());
        assertEquals(3, a.voice());
        assertEquals(0, b.voice());
    }

    @Test
    public void coincidentHeadOwnersStayAmbiguousAcrossUnknownVoices() {
        var duplicate = new PrintedSlurOwners.Head(2, 40, 85, 0, 1, 0, 0, 67);
        assertEquals(
                PrintedSlurOwners.Status.AMBIGUOUS_START,
                resolve(List.of(first(0, 0, 60), duplicate, last(2, 100, 62)), Set.of()).status());
    }

    @Test
    public void crossedVoiceLabelsCannotBypassWrittenDirection() {
        assertEquals(
                PrintedSlurOwners.Status.REVERSED_CLOCK,
                resolve(List.of(first(1, 100, 60), last(2, 0, 62)), Set.of()).status());
    }

    @Test
    public void unknownPitchRemainsUnresolvedWithoutAProvedTieRelationship() {
        assertEquals(
                PrintedSlurOwners.Status.UNPITCHED_UNRESOLVED,
                resolve(List.of(first(0, 0, -1), last(0, 100, 62)), Set.of()).status());
    }

    @Test
    public void samePitchAmbiguityIsIndependentOfVoiceAvailability() {
        assertEquals(
                PrintedSlurOwners.Status.SAME_PITCH_UNRESOLVED,
                resolve(List.of(first(0, 0, 60), last(0, 100, 60)), Set.of()).status());
    }

    @Test
    public void differentPhysicalStaffOwnershipStillNeedsSeparateProof() {
        var a = new PrintedSlurOwners.Head(0, 40, 85, 0, 2, 1, 0, 60);
        var b = new PrintedSlurOwners.Head(1, 190, 95, 1, 2, 1, 100, 62);
        assertEquals(
                PrintedSlurOwners.Status.DIFFERENT_LANES,
                resolve(List.of(a, b), Set.of()).status());
    }

    @Test
    public void exactRetainedTieIsExcludedWithUnknownVoices() {
        assertEquals(
                PrintedSlurOwners.Status.TIE_ALREADY_RETAINED,
                resolve(
                                List.of(first(0, 0, 60), last(0, 100, 60)),
                                Set.of(new PrintedSlurOwners.Tie(0, 1)))
                        .status());
    }

    @Test
    public void pageCaptureRetainsUnknownVoiceSourceOwners() {
        var frame =
                new PrintedSlurPageCapture.Frame(
                        0, 20, W - 1, H - 1, track(), List.of(first(0, 0, 60), last(0, 100, 62)));
        var result =
                PrintedSlurPageCapture.capture(
                        7, 2, pixels(), null, W, H, List.of(frame), Set.of());
        assertEquals(1, result.source().spans().size());
        assertEquals(new ScoreSlurPages.Anchor(7, 1), result.source().spans().get(0).end());
    }
}
