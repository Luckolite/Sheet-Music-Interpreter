// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Original paired bulbs, staff rules, sparse/dense noise and note-owned columns. */
public final class RestProjectionParityTest {
    record Fixture(
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<SixteenthRestDetector.Staff> staffs,
            List<ScoreNoteEvent> notes) {}

    static Fixture fixture(int n) {
        int width = n % 3 == 0 ? 1200 : 400, height = 260;
        float gap = 14.5f;
        int shade = new int[] {0, 145, 180, 190}[n % 4], paper = n % 5 == 0 ? 210 : 255;
        byte[] gray = new byte[width * height];
        Arrays.fill(gray, (byte) paper);
        for (int line = 0; line < 5; line++)
            for (int x = 10; x < width - 10; x++) gray[Math.round(80 + line * gap) * width + x] = 0;
        int origin = width / 2;
        for (int y = 97; y <= 135; y++) {
            int x = origin - (y - 97) * 10 / 38;
            gray[y * width + x] = (byte) shade;
            gray[y * width + x + 1] = (byte) shade;
        }
        for (int y = 99; y <= 101; y++)
            for (int x = origin - 7; x <= origin; x++) gray[y * width + x] = (byte) shade;
        if (n % 6 != 0)
            for (int y = 113; y <= 115; y++)
                for (int x = origin - 11; x <= origin - 4; x++) gray[y * width + x] = (byte) shade;
        if (n >= 16) {
            Random random = new Random(712044L + n);
            for (int i = 0; i < (n % 4) * 400; i++)
                gray[random.nextInt(gray.length)] = (byte) random.nextInt(256);
        }
        var notes =
                n % 7 == 0
                        ? List.of(new ScoreNoteEvent(0, .5f, 3, 0, 1, 114f / height, false, 0, 1))
                        : List.<ScoreNoteEvent>of();
        return new Fixture(
                gray,
                width,
                height,
                List.of(new MeasureRegion(.02f, .98f, .2f, .8f)),
                List.of(new SixteenthRestDetector.Staff(80, 138, gap, 0, 1)),
                notes);
    }

    static SixteenthRestDetector.Detection detect(Fixture f) {
        return SixteenthRestDetector.detectWithDots(
                f.gray, f.width, f.height, f.measures, f.staffs, f.notes);
    }

    @org.junit.Test
    public void faintDarkNoisyAndNoteOwnedRestRecordsRemainExact() throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        int rests = 0;
        for (int n = 0; n < 48; n++) {
            Fixture f = fixture(n);
            byte[] before = f.gray.clone();
            var d = detect(f);
            org.junit.Assert.assertArrayEquals(
                    "Detection must preserve original ink", before, f.gray);
            digest.update(d.toString().getBytes(StandardCharsets.UTF_8));
            rests += d.rests().size();
        }
        org.junit.Assert.assertEquals(23, rests);
        // Original generated pages include a complete paired-bulb rest recovered inside noise.
        org.junit.Assert.assertEquals(
                "1c85dde8e3d7d905483d24c2e3141213ad53d8388d0e1b6ba89d45d1d928583f",
                HexFormat.of().formatHex(digest.digest()));
    }

    @org.junit.Test
    public void overlappingStaffMasksAndSuccessiveCallsRemainIndependent() throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        int count = 0;
        for (int n = 0; n < 48; n++) {
            Fixture f = fixture(n);
            byte[] before = f.gray.clone();
            var first = new SixteenthRestDetector.Staff(80, 138, 14.5f, 0, 2);
            var second = new SixteenthRestDetector.Staff(88, 136, 12, 1, 2);
            var staffs = n % 2 == 0 ? List.of(first, second) : List.of(second, first);
            var result =
                    SixteenthRestDetector.detectWithDots(
                            f.gray, f.width, f.height, f.measures, staffs, f.notes);
            hash.update(result.toString().getBytes(StandardCharsets.UTF_8));
            count += result.rests().size();
            hash.update(detect(f).toString().getBytes(StandardCharsets.UTF_8));
            org.junit.Assert.assertArrayEquals(before, f.gray);
        }
        org.junit.Assert.assertEquals(53, count);
        // Reviewed complete records include the same recovered glyph under overlapping frames.
        org.junit.Assert.assertEquals(
                "4a353fada312348f15cc7e06d42edd9e81e5dba4f39313583080654fa2d6162d",
                HexFormat.of().formatHex(hash.digest()));
    }
}
