package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;

/** Original procedural null/paid helper equivalence; no source, model or native entrypoint. */
public class NullableCoreControls {
    static int checks;

    static void check(boolean b, String message) {
        checks++;
        if (!b) throw new AssertionError(message);
    }

    static long bits(float value) {
        return Integer.toUnsignedLong(Float.floatToRawIntBits(value));
    }

    static String frame(float[] v) {
        if (v == null) return "null";
        return bits(v[0]) + ":" + bits(v[1]);
    }

    static String defaults() throws Exception {
        var result = new StringBuilder();
        int w = 400, h = 160;
        byte[] raw = new byte[w * h];
        Arrays.fill(raw, (byte) 255);
        for (int y : new int[] {40, 54, 68, 82, 96})
            for (int x = 10; x < 390; x++) raw[y * w + x] = 0;
        var staff = new SixteenthRestDetector.Staff(40, 96, 14, 0, 1);
        result.append("frame=")
                .append(frame(QuarterRestLocalBody.frame(raw, w, h, 188, 205, staff)))
                .append(';');
        int[] strength = new int[h];
        for (int y : new int[] {40, 54, 68, 82, 96}) strength[y] = 80;
        for (var lines : RawStaffLineDetector.detectFromStrength(strength, 40, h))
            result.append(Arrays.toString(lines.rows()))
                    .append(':')
                    .append(bits(lines.gap()))
                    .append(';');
        var track = StaffPitchTrack.linear(w, 96, 14, .03f);
        for (float x : new float[] {-100, 0, 99.25f, 399, 600})
            result.append(frame(track.at(x))).append(';');
        Random random = new Random(731903);
        for (int n : new int[] {12, 24, 43, 64})
            for (int sample = 0; sample < 8; sample++) {
                double[] a = new double[n], b = new double[n];
                boolean[] observed = new boolean[n];
                Arrays.fill(observed, true);
                for (int i = 0; i < n; i++) {
                    a[i] = random.nextInt(12) * .5;
                    b[i] = a[i] + 3 + random.nextInt(3);
                }
                result.append(CompactQuarterRestContour.matches(a, 14) ? '1' : '0')
                        .append(CompactQuarterRestContour.matchesNarrow(a, 14) ? '1' : '0')
                        .append(CompactQuarterRestContour.leftSilhouette(a, b, 14) ? '1' : '0')
                        .append(
                                CompactQuarterRestContour.pairedSilhouette(a, b, observed, 14)
                                        ? '1'
                                        : '0');
            }
        return result.toString();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("DEFAULT=" + defaults());
        if (args.length == 0 || args[0].equals("default")) return;
        int w = 400, h = 160;
        byte[] raw = new byte[w * h];
        Arrays.fill(raw, (byte) 255);
        for (int y : new int[] {40, 54, 68, 82, 96})
            for (int x = 10; x < 390; x++) raw[y * w + x] = 0;
        var staff = new SixteenthRestDetector.Staff(40, 96, 14, 0, 1);
        var budget = new FaintQuarterRestInk.Budget(64_000_000, 64_000_000);
        String expected = frame(QuarterRestLocalBody.frame(raw, w, h, 188, 205, staff));
        check(
                expected.equals(
                        frame(QuarterRestLocalBody.frame(raw, w, h, 188, 205, staff, budget))),
                "paid frame bits");
        long work = budget.workUsed, bytes = budget.bytesUsed;
        var exact = new FaintQuarterRestInk.Budget(work, bytes);
        check(
                expected.equals(
                        frame(QuarterRestLocalBody.frame(raw, w, h, 188, 205, staff, exact))),
                "exact frame");
        check(exact.workUsed == work && exact.bytesUsed == bytes, "exact frame counters");
        var under = new FaintQuarterRestInk.Budget(work - 1, bytes);
        boolean refused = false;
        try {
            QuarterRestLocalBody.frame(raw, w, h, 188, 205, staff, under);
        } catch (FaintQuarterRestInk.Refusal r) {
            refused = true;
        }
        check(
                refused
                        && under.exhausted
                        && under.workUsed <= under.workLimit
                        && under.bytesUsed <= under.byteLimit,
                "one-under atomic frame");
        var track = StaffPitchTrack.linear(w, 96, 14, .03f);
        for (float x : new float[] {-100, 0, 99.25f, 399, 600})
            check(
                    frame(track.at(x))
                            .equals(
                                    frame(
                                            track.at(
                                                    x,
                                                    new FaintQuarterRestInk.Budget(
                                                            64_000_000, 64_000_000)))),
                    "paid interpolation bits");
        check(!track.verified(), "interpolation does not invent verification");
        Random random = new Random(731903);
        for (int n : new int[] {12, 24, 43, 64})
            for (int sample = 0; sample < 8; sample++) {
                double[] a = new double[n], b = new double[n];
                boolean[] observed = new boolean[n];
                Arrays.fill(observed, true);
                for (int i = 0; i < n; i++) {
                    a[i] = random.nextInt(12) * .5;
                    b[i] = a[i] + 3 + random.nextInt(3);
                }
                var before = a.clone();
                budget = new FaintQuarterRestInk.Budget(64_000_000, 64_000_000);
                check(
                        CompactQuarterRestContour.matches(a, 14)
                                == CompactQuarterRestContour.matches(a, 14, budget),
                        "paid matches");
                check(
                        CompactQuarterRestContour.matchesNarrow(a, 14)
                                == CompactQuarterRestContour.matchesNarrow(a, 14, budget),
                        "paid narrow");
                check(
                        CompactQuarterRestContour.leftSilhouette(a, b, 14)
                                == CompactQuarterRestContour.leftSilhouette(a, b, 14, budget),
                        "paid left");
                check(
                        CompactQuarterRestContour.pairedSilhouette(a, b, observed, 14)
                                == CompactQuarterRestContour.pairedSilhouette(
                                        a, b, observed, 14, budget),
                        "paid paired");
                check(Arrays.equals(before, a), "inputs unchanged");
            }
        double[] left = new double[44], right = new double[44];
        for (int n = 8; n <= 12; n++) left[n] = 3;
        for (int n = 14; n <= 19; n++) left[n] = -6;
        for (int n = 26; n <= 32; n++) left[n] = -4;
        left[41] = -20;
        left[43] = 4;
        for (int n = 0; n < 44; n++) right[n] = left[n] + 3;
        budget = new FaintQuarterRestInk.Budget(64_000_000, 64_000_000);
        boolean old = CompactQuarterRestContour.leftSilhouette(left, right, 14);
        check(
                old == CompactQuarterRestContour.leftSilhouette(left, right, 14, budget),
                "suffix-search parity");
        check(budget.workUsed > 1000, "real non-linear iterations charged");
        under = new FaintQuarterRestInk.Budget(100, 64_000_000);
        refused = false;
        try {
            CompactQuarterRestContour.leftSilhouette(left, right, 14, under);
        } catch (FaintQuarterRestInk.Refusal r) {
            refused = true;
        }
        check(
                refused && under.exhausted && under.workUsed <= 100,
                "non-linear search refuses atomically");
        var numbers = new ArrayList<>(List.of(3, 1, 2));
        int[] comparisons = {0};
        budget = new FaintQuarterRestInk.Budget(1000, 1000);
        FaintQuarterRestInk.sort(
                budget,
                numbers,
                (a, b) -> {
                    comparisons[0]++;
                    return Integer.compare(a, b);
                });
        check(numbers.equals(List.of(1, 2, 3)), "sort result");
        check(budget.workUsed == 5 + comparisons[0], "real comparator calls paid");
        numbers = new ArrayList<>(List.of(3, 1, 2));
        comparisons[0] = 0;
        under = new FaintQuarterRestInk.Budget(5, 1000);
        refused = false;
        try {
            FaintQuarterRestInk.sort(
                    under,
                    numbers,
                    (a, b) -> {
                        comparisons[0]++;
                        return Integer.compare(a, b);
                    });
        } catch (FaintQuarterRestInk.Refusal r) {
            refused = true;
        }
        check(
                refused && comparisons[0] == 0 && under.workUsed == 5,
                "comparator precharge before original callback");
        System.out.println(
                "PASS "
                        + checks
                        + " procedural null/paid assertions; frame work="
                        + work
                        + " bytes="
                        + bytes);
    }
}
