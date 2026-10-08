// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.Test;

/** Original generated rest rows exercise thresholds and changing caller-owned ink. */
public final class RestRowInkLifetimeParityTest {
    record Result(String hash, int rests) {}

    static Result thresholdRecords() throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        int rests = 0;
        for (int n = 0; n < 96; n++) {
            var f = RestProjectionParityTest.fixture(n % 48);
            byte[] gray = f.gray();
            int width = f.width(), height = f.height(), origin = width / 2;
            int shade = new int[] {169, 170, 171, 0, 145, 190}[n % 6];
            for (int y = 97; y <= 135; y++)
                for (int x = origin - 12; x <= origin + 1; x++)
                    if ((gray[y * width + x] & 255) < 200) gray[y * width + x] = (byte) shade;
            // First/last columns and short dense runs share rows with multiple masks.
            for (int y = 92; y < 144; y++) {
                gray[y * width] = (byte) shade;
                gray[y * width + width - 1] = (byte) shade;
                for (int x = 20; x < 20 + n % 17; x++) gray[y * width + x] = (byte) shade;
            }
            byte[] before = gray.clone();
            var result = RestProjectionParityTest.detect(f);
            hash.update(result.toString().getBytes(StandardCharsets.UTF_8));
            rests += result.rests().size();
            assertArrayEquals(before, gray);
            assertEquals(result, RestProjectionParityTest.detect(f));
        }
        return new Result(HexFormat.of().formatHex(hash.digest()), rests);
    }

    static Result changingRecords() throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        int rests = 0;
        for (int n = 0; n < 48; n++) {
            var f = RestProjectionParityTest.fixture(n);
            byte[] original = f.gray().clone();
            var first = RestProjectionParityTest.detect(f);
            hash.update(first.toString().getBytes(StandardCharsets.UTF_8));
            rests += first.rests().size();
            int origin = f.width() / 2;
            for (int y = 94; y <= 136; y++)
                for (int x = origin - 14; x <= origin + 3; x++)
                    f.gray()[y * f.width() + x] = (byte) 255;
            byte[] erased = f.gray().clone();
            var second = RestProjectionParityTest.detect(f);
            hash.update(second.toString().getBytes(StandardCharsets.UTF_8));
            rests += second.rests().size();
            assertArrayEquals(erased, f.gray());
            RestProjectionParityTest.detect(RestProjectionParityTest.fixture(47 - n));
            System.arraycopy(original, 0, f.gray(), 0, original.length);
            var restored = RestProjectionParityTest.detect(f);
            assertEquals(first, restored);
            assertArrayEquals(original, f.gray());
            hash.update(restored.toString().getBytes(StandardCharsets.UTF_8));
            rests += restored.rests().size();
        }
        return new Result(HexFormat.of().formatHex(hash.digest()), rests);
    }

    @Test
    public void exactThresholdAndRepeatedMaskResults() throws Exception {
        Result r = thresholdRecords();
        assertEquals(50, r.rests());
        assertEquals("cd66d07e504927cd0212788a91657f06d847db3ed7093e685ce3fc819674dc2f", r.hash());
    }

    @Test
    public void callerMutationAndRestorationTakeEffectOnEveryCall() throws Exception {
        Result r = changingRecords();
        assertEquals(46, r.rests());
        assertEquals("bd554337c8f885d7573d4f21af8fc59bca822c1ea7a10ce546352491c05961b2", r.hash());
    }

    public static void main(String[] args) throws Exception {
        Result a = thresholdRecords(), b = changingRecords();
        System.out.println("REST_ROW_THRESHOLD " + a.rests() + " " + a.hash());
        System.out.println("REST_ROW_CHANGING " + b.rests() + " " + b.hash());
    }
}
