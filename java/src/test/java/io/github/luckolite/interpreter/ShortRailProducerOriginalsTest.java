// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Analytic rising/returning curves and literal rails; no recorded score pixels. */
public class ShortRailProducerOriginalsTest {
    static class Scene {
        final int w = 360, h = 320;
        final byte[] gray = new byte[w * h];
        final StaffPitchTrack track;
        final List<PrintedSlurOwners.GeometryHead> heads = new ArrayList<>();
        final List<ScoreNoteEvent> notes = new ArrayList<>();
        final float phase, tilt, bend;

        Scene(float phase, float tilt, float bend) {
            this.phase = phase;
            this.tilt = tilt;
            this.bend = bend;
            Arrays.fill(gray, (byte) 255);
            for (int x = 8; x < w - 8; x++)
                for (int r = 0; r < 5; r++) stroke(x, 240 + phase - r * 16, 1.15);
            for (int x = 70; x <= 210; x++) {
                double t = (x - 70) / 140.;
                stroke(x, 200 + phase + tilt * t - 4 * bend * t * (1 - t), 1.6);
            }
            heads.add(new PrintedSlurOwners.GeometryHead(0, 66, 220 + phase, 0, 1));
            heads.add(new PrintedSlurOwners.GeometryHead(1, 215, 220 + phase + tilt, 0, 1));
            for (int x = 203; x <= 227; x++) {
                double t = (x - 203) / 24.;
                stroke(x, 220 + phase + tilt - 35 - 6.5 + 6.5 * t, 2);
                stroke(x, 220 + phase + tilt - 35 + 6.5 - 6.5 * t, 2);
            }
            for (var head : heads)
                for (int y = (int) head.y() - 6; y <= head.y() + 6; y++)
                    for (int x = (int) head.x() - 9; x <= head.x() + 9; x++)
                        if (Math.pow((x - head.x()) / 8., 2) + Math.pow((y - head.y()) / 5., 2)
                                <= 1) gray[y * w + x] = 0;
            for (var head : heads)
                notes.add(
                        new ScoreNoteEvent(
                                0,
                                head.x() / w,
                                7,
                                0,
                                1,
                                head.y() / h,
                                false,
                                0,
                                0,
                                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                                1,
                                1,
                                0,
                                head.owner() == 1 ? NoteArticulation.ACCENT : 0,
                                30));
            track =
                    StaffPitchTrack.fromVerifiedSamples(
                            List.of(
                                    new float[] {0, 240 + phase, 16},
                                    new float[] {w - 1, 240 + phase, 16}));
        }

        void stroke(int x, double y, double radius) {
            for (int row = (int) Math.floor(y - radius); row <= y + radius; row++) {
                int value =
                        (int) Math.round(255 * (1 - Math.max(0, 1 - Math.abs(row - y) / radius)));
                gray[row * w + x] = (byte) Math.min(gray[row * w + x] & 255, value);
            }
        }

        PrintedSlurContours.HeadEvidence hints() {
            return new PrintedCurveMarkRoles(notes, gray, w, h)
                    .wrap(PrintedSlurOwners.headEvidence(heads, gray, w, h, track), heads, track);
        }

        PrintedSlurContours.Result find() {
            return PrintedSlurContours.find(
                    gray, null, w, h, 8, 100, w - 9, 280, track, null, hints());
        }
    }

    static void recovers(float phase, float tilt, float bend) {
        var f = new Scene(phase, tilt, bend);
        var r = f.find();
        var owned = r.bows().stream().filter(x -> f.hints().ownsReturningBow(x)).toList();
        assertEquals("one complete attack-owned phrase", 1, owned.size());
        var binding =
                PrintedSlurOwners.bind(
                        "analytic", 0, owned.get(0), f.heads, f.gray, f.w, f.h, f.track);
        assertTrue(binding.span().isPresent());
        assertEquals(0, binding.span().orElseThrow().start().noteIndex());
        assertEquals(1, binding.span().orElseThrow().end().noteIndex());
    }

    @Test
    public void risingSixteenthRailCrossings() {
        recovers(0, -40, 16);
    }

    @Test
    public void quarterPixelPhase() {
        recovers(.25f, -40, 20);
    }

    @Test
    public void halfPixelPhase() {
        recovers(.5f, -40, 24);
    }

    @Test
    public void threeQuarterPixelPhase() {
        recovers(.75f, -36, 20);
    }

    @Test
    public void greaterTilt() {
        recovers(0, -48, 24);
    }

    @Test
    public void lesserTilt() {
        recovers(0, -32, 20);
    }

    @Test
    public void deeperReturningBend() {
        recovers(.25f, -44, 28);
    }

    @Test
    public void broadReturningBend() {
        recovers(.5f, -48, 28);
    }
}
