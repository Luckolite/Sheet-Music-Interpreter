// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Original abstract note bodies/shafts, short ports, staff crossings and foreign ownership. */
public class FiniteConnectedSlidePortsOriginalChecks {
    static int checks;

    static final class Scene {
        final int scale, w, h;
        final float gap;
        byte[] gray;
        final List<NoteSlideDetector.Head> heads = new ArrayList<>();
        final List<NoteSlideDetector.Staff> staffs;
        final float x0, x1, y0, y1;
        boolean cross;

        Scene(int scale, boolean crossing, int sourceDir, int targetDir) {
            this.scale = scale;
            w = 420 * scale;
            h = 280 * scale;
            gap = 14 * scale;
            cross = crossing;
            x0 = 110 * scale;
            x1 = 180 * scale;
            y0 = (crossing ? 105 : 112) * scale;
            y1 = y0 + (crossing ? 14 : 7) * scale;
            gray = new byte[w * h];
            Arrays.fill(gray, (byte) 255);
            staffs = List.of(new NoteSlideDetector.Staff(84 * scale, 140 * scale, gap));
            for (int k = 0; k < 5; k++)
                for (int x = 15 * scale; x < w - 15 * scale; x++)
                    for (int d = 0; d < 2 * scale; d++)
                        gray[((84 + 14 * k) * scale + d) * w + x] = 35;
            head(x0, y0, sourceDir);
            head(x1, y1, targetDir);
            heads.add(new NoteSlideDetector.Head(x0, y0, gap, 0, 0));
            heads.add(new NoteSlideDetector.Head(x1, y1, gap, 0, 0));
        }

        void head(float cx, float cy, int dir) {
            int rx = 10 * scale, ry = 6 * scale;
            for (int y = (int) cy - ry; y <= (int) cy + ry; y++)
                for (int x = (int) cx - rx; x <= (int) cx + rx; x++)
                    if ((x - cx) * (x - cx) / (rx * rx) + (y - cy) * (y - cy) / (ry * ry) <= 1)
                        gray[y * w + x] = 35;
            int sx = Math.round(cx + (dir > 0 ? rx : -rx));
            for (int j = 0; j <= 42 * scale; j++)
                for (int dx = 0; dx < scale; dx++)
                    gray[(Math.round(cy) - dir * j) * w + sx + dx] = 35;
        }

        void line() {
            int l = 132 * scale, r = 160 * scale;
            for (int x = l; x <= r; x++) {
                float t = (x - l) / (float) (r - l);
                float cy = y0 + (y1 - y0) * (.3f + .4f * t);
                for (int y = Math.round(cy) - scale; y <= Math.round(cy) + scale; y++)
                    gray[y * w + x] = 35;
            }
        }

        void bow() {
            int l = 122 * scale, r = 168 * scale;
            for (int x = l; x <= r; x++) {
                double t = (x - l) / (double) (r - l);
                int yy = (int) Math.round(y0 - 12 * scale - 20 * scale * 4 * t * (1 - t));
                for (int d = 0; d < scale; d++) gray[(yy + d) * w + x] = 35;
            }
        }

        List<NoteSlideDetector.Stroke> classify() {
            return NoteSlideDetector.detect(gray, w, h, staffs, heads);
        }

        List<NoteSlideDetector.Stroke> port() {
            byte[] clean = NoteSlideDetector.removeStaffLines(gray, w, h, staffs);
            var p = ObservedRuleSlideInk.prepare(gray, w, h, staffs, heads);
            return FiniteConnectedSlidePorts.find(
                    gray,
                    w,
                    h,
                    staffs,
                    heads,
                    clean,
                    p == null ? null : p.clean(),
                    p == null ? null : p.tracks());
        }
    }

    static void check(boolean yes, String text) {
        checks++;
        if (!yes) throw new AssertionError(text);
        System.out.println("PASS " + text);
    }

    public static void main(String[] args) {
        boolean baseline = args.length > 0;
        for (int scale : new int[] {1, 2, 3})
            for (boolean cross : new boolean[] {false, true})
                for (int direction : new int[] {-1, 1}) {
                    var s = new Scene(scale, cross, direction, -direction);
                    s.line();
                    s.bow();
                    var result = s.classify();
                    if (baseline) {
                        System.out.println(
                                "BASELINE scale="
                                        + scale
                                        + " crossing="
                                        + cross
                                        + " stems="
                                        + direction
                                        + " results="
                                        + result);
                        continue;
                    }
                    check(
                            result.stream()
                                    .anyMatch(
                                            x ->
                                                    x.noteIndex() == 1
                                                            && x.connected()
                                                            && x.direction() == -1),
                            "finite complete port scale="
                                    + scale
                                    + " crossing="
                                    + cross
                                    + " stems="
                                    + direction);
                    check(
                            s.port().stream().allMatch(x -> x.noteIndex() == 1 && x.connected()),
                            "unique owner scale="
                                    + scale
                                    + " crossing="
                                    + cross
                                    + " stems="
                                    + direction);
                }
        if (baseline) return;
        var s = new Scene(1, false, -1, 1);
        check(s.port().isEmpty(), "staff carriers with two real heads are not a gesture");
        s = new Scene(1, false, -1, 1);
        s.bow();
        check(s.port().isEmpty(), "curved phrasing bow alone preserved");
        s = new Scene(1, false, -1, 1);
        for (int y = 114; y <= 118; y++) for (int x = 142; x <= 147; x++) s.gray[y * s.w + x] = 35;
        check(s.port().isEmpty(), "short shallow dot rectangle is not a connecting port");
        s = new Scene(1, false, -1, 1);
        s.line();
        for (int x = 144; x <= 148; x++)
            for (int y = 108; y <= 123; y++) s.gray[y * s.w + x] = (byte) 255;
        check(s.port().isEmpty(), "disconnected non-rule gap cannot be joined");
        s = new Scene(1, true, -1, 1);
        s.line();
        for (int x = 144; x <= 148; x++)
            for (int y = 104; y <= 121; y++) s.gray[y * s.w + x] = (byte) 255;
        check(s.port().isEmpty(), "paper gap across a rule still needs literal raw bridge ink");
        s = new Scene(1, false, -1, 1);
        s.line();
        s.heads.add(new NoteSlideDetector.Head(s.x0 + .1f * s.gap, s.y0 + s.gap, s.gap, 0, 0));
        check(s.port().isEmpty(), "ambiguous source chord cannot supply one exact source");
        s = new Scene(1, false, -1, 1);
        s.line();
        s.heads.add(new NoteSlideDetector.Head(s.x1 + .1f * s.gap, s.y1 - s.gap, s.gap, 0, 0));
        check(s.port().isEmpty(), "ambiguous target chord preserved");
        s = new Scene(1, false, -1, 1);
        s.line();
        s.heads.add(new NoteSlideDetector.Head(146, 116, s.gap, 1, 0));
        check(s.port().isEmpty(), "foreign voice head within line corridor vetoes ownership");
        s = new Scene(1, false, -1, 1);
        s.line();
        s.head(145, 95, 1);
        s.heads.add(new NoteSlideDetector.Head(145, 95, s.gap, 0, 0));
        check(s.port().isEmpty(), "scale neighbour cannot be skipped for the original target");
        s = new Scene(1, false, -1, 1);
        s.line();
        for (int y = 104; y < 121; y++)
            for (int x = 99; x <= 121; x++) s.gray[y * s.w + x] = (byte) 255;
        check(s.port().isEmpty(), "head record without its real printed oval/shaft is rejected");
        s = new Scene(1, false, -1, 1);
        for (int x = 132; x <= 160; x++) for (int y = 115; y <= 117; y++) s.gray[y * s.w + x] = 35;
        check(s.port().isEmpty(), "horizontal ink cannot borrow a pitched endpoint direction");
        s = new Scene(1, false, -1, 1);
        for (int x = 117; x <= 131; x++) {
            int y = 151 - (x - 117) / 2;
            for (int dy = 0; dy < 2; dy++) s.gray[(y + dy) * s.w + x] = 35;
        }
        check(s.port().isEmpty(), "free flag tip far from head pitch corridor is preserved");
        System.out.println("CHECKS=" + checks);
    }
}
