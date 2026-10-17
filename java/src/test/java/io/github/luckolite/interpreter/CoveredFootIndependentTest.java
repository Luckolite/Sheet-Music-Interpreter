// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
// Original procedural reviewer fixtures. No score pixels, traces, fonts or copied glyphs.
package io.github.luckolite.interpreter;

import java.util.*;
import java.nio.file.*;
import java.security.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public final class CoveredFootIndependentTest {
    static final int W = 320, H = 240;

    record Case(
            String name,
            boolean expected,
            int dx,
            int shift,
            boolean linked,
            boolean rules,
            boolean occlusion) {}

    static final List<Case> CASES =
            List.of(
                    new Case("independent-rest", true, 0, 0, false, false, false),
                    new Case("independent-rest-near-roi-edge", true, 0, 5, false, false, false),
                    new Case("independent-rest-thin-staff", true, 0, 0, false, true, false),
                    new Case("independent-rest-head-occlusion", true, 0, 0, false, false, true),
                    new Case("note-owned-beam-contour", false, 0, 0, true, false, false),
                    new Case("note-owned-beam-contour-thin-staff", false, 0, 0, true, true, false),
                    new Case("translated-independent-rest", true, 53, 0, false, false, false),
                    new Case(
                            "translated-note-owned-beam-contour",
                            false,
                            53,
                            0,
                            true,
                            false,
                            false));

    static void rect(byte[] p, int l, int t, int r, int b, int gray) {
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++)
                if (x >= 0 && x < W && y >= 0 && y < H) p[y * W + x] = (byte) gray;
    }

    static byte[] pixels(Case c) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 245);
        int dx = c.dx;
        // Genuine upstem and filled head, independent of the putative rest shape.
        rect(p, 168 + dx, 108, 169 + dx, 160, 30);
        for (int y = 154; y <= 166; y++)
            for (int x = 151 + dx; x <= 169 + dx; x++)
                if (Math.pow((x - 160 - dx) / 9.0, 2) + Math.pow((y - 160) / 6.0, 2) <= 1)
                    p[y * W + x] = 30;
        // Original upper tapered diagonal and broad returning wing, with lower hook covered by
        // head.
        for (int y = 112; y <= 140; y++) {
            int l, r;
            if (y <= 122) {
                int z = y - 112;
                l = 149 + Math.round(z * .7f);
                r = l + 1 + z / 3;
            } else if (y <= 130) {
                int z = y - 122;
                l = 156 - Math.round(z * .75f);
                r = 160 - Math.round(z * .65f);
            } else {
                int z = y - 130;
                l = 150 + Math.round(z * .3f);
                r = 155 + Math.round(z * .2f);
            }
            rect(p, l + dx + c.shift, y, r + dx + c.shift, y, 30);
        }
        // This route contains no rest: the entire apparent wing belongs to the head's printed
        // beam/shaft.
        if (c.linked) rect(p, 159 + dx, 121, 169 + dx, 123, 30);
        // Real thin full staff rules are independently present, not arbitrary masks.
        if (c.rules) for (int y = 96; y <= 160; y += 16) rect(p, 20, y, 300, y, 100);
        if (c.occlusion) { // Separate actual upper head partly hides the bottom of the rest
                           // contour.
            for (int y = 140; y <= 150; y++)
                for (int x = 151 + dx; x <= 167 + dx; x++)
                    if (Math.pow((x - 159 - dx) / 8.0, 2) + Math.pow((y - 145) / 5.0, 2) <= 1)
                        p[y * W + x] = 30;
        }
        return p;
    }

    static ScoreNoteEvent note(float x, float y) {
        return new ScoreNoteEvent(
                0, x, 0, 0, 1, y, false, 0, 2, 2, 0, 1, 0, 0, 30, false, 0, false, 0, 0, 1, 1);
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        int failures = 0;
        for (Case c : CASES) {
            byte[] p = pixels(c);
            var notes = new ArrayList<ScoreNoteEvent>();
            notes.add(note((160f + c.dx) / W, 160f / H));
            if (c.occlusion) notes.add(note((159f + c.dx) / W, 145f / H));
            var score = new ScorePageInterpretation(List.of(new MeasureRegion(0, 1, 0, 1)), notes);
            var found =
                    BeamedHeadQuarterRests.coveredFoot(
                            p,
                            W,
                            H,
                            score,
                            List.of(new PlayingTechniqueDetector.Staff(96, 160, 16, 0, 1)));
            boolean got = !found.isEmpty();
            if (got != c.expected) failures++;
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(p));
            System.out.println(
                    c.name
                            + " expected="
                            + c.expected
                            + " actual="
                            + got
                            + " count="
                            + found.size()
                            + " sha256="
                            + hash);
            BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
            im.getRaster().setDataElements(0, 0, W, H, p);
            ImageIO.write(im, "png", out.resolve(c.name + ".png").toFile());
        }
        System.out.println("cases=" + CASES.size() + " mismatches=" + failures);
    }

    @org.junit.Test
    public void frozenOriginalOwnershipCases() {
        for (Case c : CASES) {
            byte[] p = pixels(c);
            var notes = new java.util.ArrayList<ScoreNoteEvent>();
            notes.add(note((160f + c.dx) / W, 160f / H));
            if (c.occlusion) notes.add(note((159f + c.dx) / W, 145f / H));
            var score =
                    new ScorePageInterpretation(
                            java.util.List.of(new MeasureRegion(0, 1, 0, 1)), notes);
            var found =
                    BeamedHeadQuarterRests.coveredFoot(
                            p,
                            W,
                            H,
                            score,
                            java.util.List.of(
                                    new PlayingTechniqueDetector.Staff(96, 160, 16, 0, 1)));
            org.junit.Assert.assertEquals(c.name, c.expected, !found.isEmpty());
        }
    }
}
