// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
// Original source-free independent component/overlapping-bounds controls.
package io.github.luckolite.interpreter;

import java.util.*;
import java.nio.file.*;
import java.security.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public final class DisconnectedOwnerSeedTest {
    static final int W = 320, H = 240;

    record Case(String name, int decoy, boolean rules, int dx) {}

    static final List<Case> CASES =
            List.of(
                    new Case("genuine-wing-no-decoy", 0, false, 0),
                    new Case("left-first-disconnected-dot", -1, false, 0),
                    new Case("right-later-disconnected-dot", 1, false, 0),
                    new Case("left-first-disconnected-dot-rulegap", -1, true, 0),
                    new Case("right-later-disconnected-dot-rulegap", 1, true, 0),
                    new Case("translated-left-first-disconnected-dot", -1, false, 53));

    static void rect(byte[] p, int l, int t, int r, int b, int g) {
        for (int y = t; y <= b; y++) for (int x = l; x <= r; x++) p[y * W + x] = (byte) g;
    }

    static byte[] pixels(Case c) {
        byte[] p = new byte[W * H];
        Arrays.fill(p, (byte) 245);
        int dx = c.dx;
        rect(p, 168 + dx, 108, 169 + dx, 160, 30);
        for (int y = 154; y <= 166; y++)
            for (int x = 151 + dx; x <= 169 + dx; x++)
                if (Math.pow((x - 160 - dx) / 9.0, 2) + Math.pow((y - 160) / 6.0, 2) <= 1)
                    p[y * W + x] = 30;
        // The genuine quarter wing returns left of its narrow first stroke. Its
        // overall box therefore overlaps a disconnected dot's box without owning it.
        for (int y = 110; y <= 139; y++) {
            int l, r;
            if (y <= 120) {
                int z = y - 110;
                l = 154 + Math.round(z * .6f);
                r = l + 1 + z / 3;
            } else if (y <= 129) {
                int z = y - 120;
                l = 160 - Math.round(z * 1.2f);
                r = 164 - Math.round(z * .9f);
            } else {
                int z = y - 129;
                l = 149 + Math.round(z * .3f);
                r = 156 + Math.round(z * .2f);
            }
            rect(p, l + dx, y, r + dx, y, 30);
        }
        if (c.decoy != 0)
            rect(p, (c.decoy < 0 ? 150 : 162) + dx, 110, (c.decoy < 0 ? 150 : 162) + dx, 110, 30);
        if (c.rules) for (int y = 96; y <= 160; y += 16) rect(p, 20, y, 300, y, 100);
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
            var score =
                    new ScorePageInterpretation(
                            List.of(new MeasureRegion(0, 1, 0, 1)),
                            List.of(note((160f + c.dx) / W, 160f / H)));
            var found =
                    BeamedHeadQuarterRests.coveredFoot(
                            p,
                            W,
                            H,
                            score,
                            List.of(new PlayingTechniqueDetector.Staff(96, 160, 16, 0, 1)));
            boolean got = !found.isEmpty();
            if (!got) failures++;
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(p));
            System.out.println(
                    c.name
                            + " expected=true actual="
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
            org.junit.Assert.assertEquals(c.name, true, !found.isEmpty());
        }
    }
}
