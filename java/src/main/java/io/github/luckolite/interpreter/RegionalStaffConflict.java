// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** A regional window must not reuse established rules without joint five-rule support. */
final class RegionalStaffConflict {
    private static final long MAX_WORK = 2_000_000;

    interface Rules {
        float y(int line, int x);
    }

    /** True only for a proved shared raw path AND insufficient common-five support. */
    static boolean unsupportedAlias(
            byte[] gray,
            int width,
            int height,
            RegionalStaffSeeds.Evidence candidate,
            float priorGap,
            Rules prior) {
        if (gray == null
                || width < 1
                || height < 1
                || (long) width * height != gray.length
                || candidate == null
                || prior == null
                || !Float.isFinite(priorGap)
                || priorGap < 3) return false;
        var seed = candidate.seed();
        float gap = seed.gap();
        if (!Float.isFinite(gap) || gap < 3 || !Float.isFinite(seed.slope())) return false;
        int[] rows = candidate.rows();
        if (rows.length != 5) return false;
        int radius = Math.max(0, Math.round(gap * .10f));
        int priorRadius = Math.max(0, Math.round(priorGap * .10f));
        // New proof is best-effort: inability to finish cannot change old admission.
        if (radius > 15 || priorRadius > 15) return false;
        int band = radius * 2 + 1;
        int priorBand = priorRadius * 2 + 1;
        long charge = (long) width * (25L * band * band + 10L * band + 5L * priorBand + 100L);
        if (charge > MAX_WORK) return false;
        int probe = Math.max(2, Math.round(gap * .32f));
        int priorProbe = Math.max(2, Math.round(priorGap * .32f));
        int minimum = Math.max(24, Math.round(width * .08f));
        int minimumRun = Math.max(8, Math.round(gap * 3));
        if (width < minimum) return false;
        int jointCount = 0;
        int[] lineCount = new int[5], lineRun = new int[5], lineLongest = new int[5];
        int[][] previous = new int[25][band], current = new int[25][band];
        int[] previousCenter = new int[5], center = new int[5];
        int[] counts = new int[25], longest = new int[25];
        boolean[][] candidateInk = new boolean[5][band];
        float[] priorCenter = new float[5];
        int[] roundedPrior = new int[5];
        boolean[][] priorInk = new boolean[5][priorBand];
        for (int x = 0; x < width; x++) {
            boolean joint = true;
            for (int line = 0; line < 5; line++) {
                float phase = rows[line] + candidate.offset() + seed.slope() * (x - width * .5f);
                if (!Float.isFinite(phase) || phase < -radius || phase > height - 1L + radius)
                    return false;
                center[line] = Math.round(phase);
                priorCenter[line] = prior.y(line, x);
                if (!Float.isFinite(priorCenter[line])) return false;
                boolean found = false;
                for (int k = 0; k < band; k++) {
                    boolean ink =
                            contrasted(gray, width, height, x, center[line] + k - radius, probe);
                    candidateInk[line][k] = ink;
                    found |= ink;
                }
                joint &= found;
                if (found) {
                    lineCount[line]++;
                    lineLongest[line] = Math.max(lineLongest[line], ++lineRun[line]);
                } else lineRun[line] = 0;
            }
            if (joint) jointCount++;
            for (int line = 0; line < 5; line++) roundedPrior[line] = Math.round(priorCenter[line]);
            // Inclusive search bands must be disjoint: each candidate pixel can
            // otherwise cause up to five separately charged predecessor scans.
            for (int one = 0; one < 5; one++)
                for (int two = one + 1; two < 5; two++)
                    if (Math.abs((long) roundedPrior[one] - roundedPrior[two]) <= 2L * priorRadius)
                        return false;
            boolean priorJoint = true;
            for (int line = 0; line < 5; line++) {
                boolean found = false;
                for (int p = 0; p < priorBand; p++) {
                    long y = (long) roundedPrior[line] + p - priorRadius;
                    boolean ink =
                            y >= 0
                                    && y < height
                                    && contrasted(gray, width, height, x, (int) y, priorProbe);
                    priorInk[line][p] = ink;
                    found |= ink;
                }
                priorJoint &= found;
            }
            for (int line = 0; line < 5; line++) {
                for (int oldLine = 0; oldLine < 5; oldLine++) {
                    int pair = line * 5 + oldLine;
                    boolean any = false;
                    for (int k = 0; k < band; k++) {
                        int y = center[line] + k - radius;
                        long priorOffset = (long) y - roundedPrior[oldLine] + priorRadius;
                        boolean shared =
                                priorJoint
                                        && candidateInk[line][k]
                                        && priorOffset >= 0
                                        && priorOffset < priorBand
                                        && priorInk[oldLine][(int) priorOffset];
                        int length = 0;
                        if (shared) {
                            length = 1;
                            for (int p = 0; p < band; p++) {
                                long oldY = (long) previousCenter[line] + p - radius;
                                if (Math.abs((long) y - oldY) <= 1)
                                    length = Math.max(length, previous[pair][p] + 1);
                            }
                            longest[pair] = Math.max(longest[pair], length);
                            any = true;
                        }
                        current[pair][k] = length;
                    }
                    if (any) counts[pair]++;
                }
            }
            int[][] swap = previous;
            previous = current;
            current = swap;
            System.arraycopy(center, 0, previousCenter, 0, 5);
        }
        // The producer already requires each individual rule's count/run.
        // Only simultaneous five-rule run continuity is deliberately unnecessary.
        boolean supported = jointCount >= minimum;
        for (int line = 0; line < 5; line++)
            supported &= lineCount[line] >= minimum && lineLongest[line] >= minimumRun;
        if (supported) return false;
        for (int pair = 0; pair < 25; pair++)
            if (counts[pair] >= minimum && longest[pair] >= minimumRun) return true;
        return false;
    }

    private static boolean contrasted(byte[] gray, int width, int height, int x, int y, int probe) {
        if ((long) y - probe < 0 || (long) y + probe >= height) return false;
        int ink = gray[y * width + x] & 255;
        return ink <= 238
                && (gray[(y - probe) * width + x] & 255) >= ink + 12
                && (gray[(y + probe) * width + x] & 255) >= ink + 12;
    }
}
