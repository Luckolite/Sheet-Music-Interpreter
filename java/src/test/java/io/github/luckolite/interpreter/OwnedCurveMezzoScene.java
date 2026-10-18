// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Procedural mixed notation; the mf and arc have separate observable owners. */
final class OwnedCurveMezzoScene {
    static final int W = 1000, H = 700, G = 14;
    final byte[] gray = new byte[W * H];
    final List<PlayingTechniqueDetector.Staff> staffs =
            List.of(new PlayingTechniqueDetector.Staff(124, 180, G, 0, 1));
    final List<MeasureRegion> measures = List.of(new MeasureRegion(.3f, .55f, .16f, .27f));
    final List<ScoreNoteEvent> notes =
            List.of(
                    new ScoreNoteEvent(
                            0,
                            .136f,
                            1,
                            0,
                            1,
                            173f / H,
                            false,
                            0,
                            0,
                            ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                            1),
                    new ScoreNoteEvent(
                            0,
                            .76f,
                            2,
                            0,
                            1,
                            166f / H,
                            false,
                            0,
                            0,
                            ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                            1));
    final PlayingTechniqueDetector.Word word =
            new PlayingTechniqueDetector.Word("mf", 359f / W, 199f / H, 433f / W, 247f / H);

    OwnedCurveMezzoScene(boolean bodies, boolean faintBridge) {
        Arrays.fill(gray, (byte) 255);
        for (int y = 124; y <= 180; y += G) for (int x = 0; x < W; x++) gray[y * W + x] = 20;
        var f = new ContextualMfStrokeFixture();
        f.draw(3, true);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                if ((f.p[y * W + x] & 255) < 255) {
                    int yy = 210 + Math.round((y - 200) * .7f);
                    gray[yy * W + x] =
                            (byte) Math.min(gray[yy * W + x] & 255, f.p[y * W + x] & 255);
                }
        for (int x = 340; x <= 490; x++) {
            double z = 2.0 * (x - 340) / 150 - 1;
            int y = (int) Math.round(202 - 28 * z * z);
            gray[y * W + x] = 20;
        }
        if (bodies) {
            head(334, 173);
            head(490, 166);
        }
        if (faintBridge)
            for (int y = 202; y <= 210; y++)
                gray[y * W + 420] = (byte) Math.min(gray[y * W + 420] & 255, 250);
    }

    void head(int cx, int cy) {
        for (int y = cy - 4; y <= cy + 4; y++)
            for (int x = cx - 7; x <= cx + 7; x++)
                if ((x - cx) * (x - cx) / 49.0 + (y - cy) * (y - cy) / 16.0 <= 1)
                    gray[y * W + x] = 20;
    }

    StaffPitchTrack track() {
        return StaffPitchTrack.fromVerifiedSamples(
                List.of(new float[] {0, 180, G}, new float[] {W - 1, 180, G}));
    }

    List<PrintedSlurOwners.GeometryHead> heads() {
        return List.of(
                new PrintedSlurOwners.GeometryHead(0, 334, 173, 0, 1),
                new PrintedSlurOwners.GeometryHead(1, 490, 166, 0, 1));
    }

    List<PrintedSlurContours.Bow> bows() {
        return PrintedSlurContours.find(
                        gray,
                        null,
                        W,
                        H,
                        300,
                        140,
                        550,
                        250,
                        track(),
                        null,
                        PrintedSlurOwners.headEvidence(heads(), gray, W, H, track()))
                .bows();
    }

    List<PrintedSlurOwners.Binding> bindings(List<PrintedSlurOwners.GeometryHead> heads) {
        var out = new ArrayList<PrintedSlurOwners.Binding>();
        int i = 0;
        for (var b : bows())
            out.add(
                    PrintedSlurOwners.bind(
                            "original-mixed-arc-" + (i++), 0, b, heads, gray, W, H, track()));
        return out;
    }

    List<ScoreDynamicChange> dynamics() {
        return ScoreDynamicsDetector.detect(List.of(word), staffs, measures, notes, gray, W, H);
    }
}
