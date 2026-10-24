// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Bounded operation counter over original real glyph metadata; no stress/OOM. */
public class FiniteSlideWorkBoundProbe {
    static class StopAtLimit extends RuntimeException {}

    static class CountedHeads extends AbstractList<NoteSlideDetector.Head> {
        final List<NoteSlideDetector.Head> data;
        final long limit;
        long gets;

        CountedHeads(List<NoteSlideDetector.Head> d, long cap) {
            data = d;
            limit = cap;
        }

        public int size() {
            return data.size();
        }

        public NoteSlideDetector.Head get(int index) {
            if (++gets > limit) throw new StopAtLimit();
            return data.get(index);
        }
    }

    static void drawHead(byte[] g, int w, int cx, int cy) {
        for (int y = cy - 3; y <= cy + 3; y++)
            for (int x = cx - 6; x <= cx + 6; x++)
                if ((x - cx) * (x - cx) / 36.0 + (y - cy) * (y - cy) / 9.0 <= 1) g[y * w + x] = 35;
        for (int y = cy - 24; y <= cy; y++) g[y * w + cx + 6] = 35;
    }

    public static void main(String[] args) {
        for (int n : new int[] {64, 128, 256}) {
            int w = 400, rows = (n + 15) / 16, h = rows * 60 + 80;
            byte[] gray = new byte[w * h];
            Arrays.fill(gray, (byte) 255);
            var heads = new ArrayList<NoteSlideDetector.Head>();
            var staffs = new ArrayList<NoteSlideDetector.Staff>();
            for (int row = 0; row < rows; row++) {
                int cy = 45 + row * 60;
                staffs.add(new NoteSlideDetector.Staff(cy - 16, cy + 16, 8));
                for (int k = 0; k < 5; k++)
                    for (int x = 10; x < w - 10; x++) gray[(cy - 16 + 8 * k) * w + x] = 35;
                for (int col = 0; col < 16 && heads.size() < n; col++) {
                    int cx = 25 + col * 22;
                    drawHead(gray, w, cx, cy);
                    heads.add(new NoteSlideDetector.Head(cx, cy, 8, row, 0));
                }
            }
            int shafts = 0;
            for (var q : heads)
                if (PrintedStemDirection.detect(gray, w, h, q.x(), q.y(), q.gap()) != 0) shafts++;
            var counted = new CountedHeads(heads, 100_000);
            byte[] clean = NoteSlideDetector.removeStaffLines(gray, w, h, staffs);
            boolean boundedStop = false;
            List<NoteSlideDetector.Stroke> result = null;
            long started = System.nanoTime();
            try {
                result =
                        FiniteConnectedSlidePorts.find(
                                gray, w, h, staffs, counted, clean, null, null);
            } catch (StopAtLimit expected) {
                boundedStop = true;
            }
            System.out.println(
                    "heads="
                            + n
                            + " glyphShafts="
                            + shafts
                            + " pixels="
                            + (w * h)
                            + " getOperations="
                            + counted.gets
                            + " fixtureSafetyStop="
                            + boundedStop
                            + " milliseconds="
                            + (System.nanoTime() - started) / 1_000_000.0
                            + " result="
                            + result);
            if (shafts != n)
                throw new AssertionError("Fixture needs actual attached stems for every head");
            if (n == 256 && !boundedStop)
                throw new AssertionError(
                        "Expected original finite loop workload to exceed100000 counted metadata reads");
        }
    }
}
