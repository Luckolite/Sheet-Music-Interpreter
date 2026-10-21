// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Explicit tab gestures. A numeric bend amount needs its own attached arrow, never just proximity. */
public final class TabGestureMarks {
    private TabGestureMarks() {}

    public static List<TablatureDecoder.Staff> apply(
            List<TablatureDecoder.Staff> tabs,
            List<TablatureDecoder.Word> words,
            byte[] gray,
            int w,
            int h) {
        var result = new ArrayList<TablatureDecoder.Staff>();
        for (var tab : tabs) {
            var frets = new ArrayList<>(tab.frets());
            for (var word : words) {
                float x = (word.left() + word.right()) * w / 2,
                        y = (word.top() + word.bottom()) * h / 2;
                if (y < tab.top() - tab.gap() * 5 || y > tab.bottom() + tab.gap() * 5) continue;
                int strum =
                        switch (word.text().toLowerCase(Locale.ROOT)) {
                            case "\uE846", "upstroke", "strum up" -> TabEffect.STRUM_UP;
                            case "\uE847", "downstroke", "strum down" -> TabEffect.STRUM_DOWN;
                            default -> 0;
                        };
                if (strum != 0) {
                    strum(frets, tab, x, y, strum);
                    continue;
                }
                double amount = amount(word.text());
                if (!Double.isFinite(amount) || gray == null || gray.length != (long) w * h)
                    continue;
                boolean bar =
                        words.stream()
                                .anyMatch(
                                        v -> {
                                            String s = v.text().toLowerCase(Locale.ROOT);
                                            float vx = (v.left() + v.right()) * w / 2,
                                                    vy = (v.top() + v.bottom()) * h / 2;
                                            return (s.equals("w/bar")
                                                            || s.equals("whammy")
                                                            || s.equals("bar"))
                                                    && Math.abs(vx - x) < tab.gap() * 3
                                                    && Math.abs(vy - y) < tab.gap() * 2;
                                        });
                var owners = new ArrayList<Integer>();
                var gestures = new ArrayList<Integer>();
                var downward = new ArrayList<Boolean>();
                for (int i = 0; i < frets.size(); i++) {
                    var f = frets.get(i);
                    boolean down = y > f.y();
                    if (f.fret() < 0
                            || f.tied()
                            || TabEffect.kind(f.marks()) != 0
                            || x < f.x()
                            || x - f.x() > tab.gap() * 5
                            || Math.abs(y - f.y()) < tab.gap() * .4f
                            || !bar && (down || amount < 0)
                            || amount < 0 && !down) continue;
                    int shape = arrow(gray, w, h, tab, f, word, words, down);
                    if (shape != 0) {
                        owners.add(i);
                        gestures.add(shape);
                        downward.add(down);
                    }
                }
                // An amount shared by indistinguishable nearby frets has no proved string owner.
                if (owners.size() != 1) continue;
                int index = owners.get(0), shape = gestures.get(0);
                var f = frets.get(index);
                int kind =
                        bar
                                ? (shape == 2 ? TabEffect.BAR_RELEASE : TabEffect.BAR)
                                : shape == 2 ? TabEffect.BEND_RELEASE : TabEffect.BEND;
                double delta = downward.get(0) ? -Math.abs(amount) : amount;
                frets.set(index, marked(f, f.marks() | TabEffect.encodePitch(kind, delta)));
            }
            if (gray != null && gray.length == (long) w * h)
                rasterStrums(frets, tab, words, gray, w, h);
            frets.sort(Comparator.comparingDouble(TablatureDecoder.Fret::x));
            var previous = new HashMap<Integer, TablatureDecoder.Fret>();
            for (int i = 0; i < frets.size(); i++) {
                var f = frets.get(i);
                var before = previous.get(f.string());
                if (f.fret() < 0) {
                    previous.remove(f.string());
                    continue;
                }
                if (f.tied()
                        && before != null
                        && f.fret() == before.fret()
                        && TabEffect.kind(f.marks()) == 0)
                    f = marked(f, (f.marks() & ~TabEffect.ALL) | (before.marks() & TabEffect.ALL));
                frets.set(i, f);
                previous.put(f.string(), f);
            }
            result.add(tab.withFrets(frets));
        }
        return List.copyOf(result);
    }

    private static void rasterStrums(
            List<TablatureDecoder.Fret> frets,
            TablatureDecoder.Staff tab,
            List<TablatureDecoder.Word> words,
            byte[] gray,
            int w,
            int h) {
        var columns = new ArrayList<Float>();
        for (var f : frets)
            if (f.fret() >= 0
                    && columns.stream().noneMatch(x -> Math.abs(x - f.x()) < tab.gap() * .3))
                columns.add(f.x());
        int arm = Math.max(3, Math.round(tab.gap() * .3f));
        for (float column : columns) {
            // Direction arrows have a long vertical shaft and two converging arms at only one end.
            for (int x = Math.max(arm, Math.round(column - tab.gap() * .4f));
                    x <= Math.min(w - arm - 1, Math.round(column + tab.gap() * .4f));
                    x++) {
                int a = Math.max(arm, (int) (tab.top() - tab.gap() * 3.5f)),
                        b = Math.min(h - arm - 1, (int) (tab.top() - tab.gap() * .35f));
                int first = -1, last = -1;
                for (int y = a; y <= b; y++)
                    if ((gray[y * w + x] & 255) < 180) {
                        if (first < 0) first = y;
                        last = y;
                    }
                if (first < 0 || last - first < tab.gap() * 1.3 || last - first > tab.gap() * 2.8)
                    continue;
                boolean covered = false;
                for (var word : words)
                    if (word.left() * w <= x + arm
                            && word.right() * w >= x - arm
                            && word.top() * h <= last
                            && word.bottom() * h >= first) {
                        covered = true;
                        break;
                    }
                if (covered) continue;
                int shaft = 0;
                for (int y = first; y <= last; y++) if ((gray[y * w + x] & 255) < 180) shaft++;
                if (shaft < (last - first) * .85) continue;
                boolean up = arrowArms(gray, w, h, x, first, arm, 1),
                        down = arrowArms(gray, w, h, x, last, arm, -1);
                if (up == down) continue;
                strum(
                        frets,
                        tab,
                        column,
                        (first + last) / 2f,
                        up ? TabEffect.STRUM_UP : TabEffect.STRUM_DOWN);
                break;
            }
        }
    }

    private static boolean arrowArms(
            byte[] gray, int w, int h, int x, int y, int arm, int direction) {
        for (int sign : new int[] {-1, 1}) {
            int hits = 0;
            for (int i = 1; i <= arm; i++) {
                int xx = x + sign * i, yy = y + direction * i;
                if (xx < 0 || xx >= w || yy < 1 || yy >= h - 1) return false;
                if ((gray[yy * w + xx] & 255) < 180
                        || (gray[(yy - 1) * w + xx] & 255) < 180
                        || (gray[(yy + 1) * w + xx] & 255) < 180) hits++;
            }
            if (hits < arm * .8) return false;
        }
        return true;
    }

    static double amount(String text) {
        return switch (text.trim().toLowerCase(Locale.ROOT).replace("−", "-")) {
            case "full", "1" -> 2;
            case "1/2", "½" -> 1;
            case "1/4", "¼" -> .5;
            case "3/4", "¾" -> 1.5;
            case "1 1/2", "1½" -> 3;
            case "2" -> 4;
            case "-1/4", "-¼" -> -.5;
            case "-1/2", "-½" -> -1;
            case "-1", "-full" -> -2;
            case "-2" -> -4;
            default -> Double.NaN;
        };
    }

    private static void strum(
            List<TablatureDecoder.Fret> frets,
            TablatureDecoder.Staff tab,
            float x,
            float y,
            int kind) {
        if (y >= tab.top() - tab.gap() * .3f) return;
        float nearest = Float.POSITIVE_INFINITY, column = Float.NaN;
        for (var f : frets) {
            float distance = Math.abs(f.x() - x);
            if (f.fret() >= 0 && distance < tab.gap() * .8f && distance < nearest) {
                nearest = distance;
                column = f.x();
            }
        }
        if (!Float.isFinite(column)) return;
        var chord = new ArrayList<Integer>();
        var strings = new HashSet<Integer>();
        for (int i = 0; i < frets.size(); i++) {
            var f = frets.get(i);
            if (f.fret() >= 0 && Math.abs(f.x() - column) < tab.gap() * .3f) {
                // Do not overwrite an independently printed effect or a tied continuation.
                if (TabEffect.kind(f.marks()) != 0 || f.tied() || !strings.add(f.string())) return;
                chord.add(i);
            }
        }
        if (chord.size() < 2) return;
        chord.sort(Comparator.comparingInt(i -> frets.get(i).string()));
        if (kind == TabEffect.STRUM_DOWN) Collections.reverse(chord);
        for (int rank = 0; rank < chord.size(); rank++) {
            int i = chord.get(rank);
            var f = frets.get(i);
            frets.set(i, marked(f, f.marks() | TabEffect.encode(kind, rank)));
        }
    }

    /** Trace a connected arrow from the fret's upper-right edge to the printed amount.
     * String rules and recognized text are masked. The tip needs both arrowhead arms;
     * a plain tie/slur, stem, beam, numeric fingering or missing arrowhead cannot qualify.
     */
    private static int arrow(
            byte[] gray,
            int w,
            int h,
            TablatureDecoder.Staff tab,
            TablatureDecoder.Fret fret,
            TablatureDecoder.Word amount,
            List<TablatureDecoder.Word> words,
            boolean down) {
        float gap = tab.gap();
        if (down) {
            byte[] flipped = new byte[gray.length];
            for (int y = 0; y < h; y++) System.arraycopy(gray, y * w, flipped, (h - 1 - y) * w, w);
            var mirroredWords =
                    words.stream()
                            .map(
                                    v ->
                                            new TablatureDecoder.Word(
                                                    v.text(),
                                                    v.left(),
                                                    1 - v.bottom(),
                                                    v.right(),
                                                    1 - v.top()))
                            .toList();
            var mirroredAmount =
                    new TablatureDecoder.Word(
                            amount.text(),
                            amount.left(),
                            1 - amount.bottom(),
                            amount.right(),
                            1 - amount.top());
            var mirroredFret =
                    new TablatureDecoder.Fret(
                            fret.x(), h - 1 - fret.y(), fret.string(), fret.fret());
            var mirroredTab =
                    new TablatureDecoder.Staff(
                            h - 1 - tab.bottom(),
                            gap,
                            -1,
                            List.of(mirroredFret),
                            tab.bars(),
                            tab.stringCount(),
                            tab.tuning());
            return arrow(
                    flipped, w, h, mirroredTab, mirroredFret, mirroredAmount, mirroredWords, false);
        }
        int left = Math.max(0, (int) (fret.x() + gap * .2f)),
                right = Math.min(w - 1, (int) (amount.right() * w + gap * 3)),
                top = Math.max(0, (int) (amount.bottom() * h)),
                bottom = Math.min(h - 1, (int) (fret.y() - gap * .15f));
        int width = right - left + 1, height = bottom - top + 1;
        if (width < gap * .5 || width > gap * 6 || height < gap * .6 || height > gap * 6) return 0;
        boolean[] ink = new boolean[width * height];
        for (int yy = top; yy <= bottom; yy++)
            for (int xx = left; xx <= right; xx++) {
                if ((gray[yy * w + xx] & 255) >= 180) continue;
                boolean masked = false;
                for (var word : words)
                    if (xx >= word.left() * w - 1
                            && xx <= word.right() * w + 1
                            && yy >= word.top() * h - 1
                            && yy <= word.bottom() * h + 1) {
                        masked = true;
                        break;
                    }
                if (masked) continue;
                for (int s = 0; s < tab.stringCount(); s++)
                    if (Math.abs(yy - (tab.top() + s * gap)) <= 1) {
                        masked = true;
                        break;
                    }
                if (!masked) ink[(yy - top) * width + xx - left] = true;
            }
        boolean[] seen = new boolean[ink.length];
        int[] queue = new int[ink.length];
        for (int seed = 0; seed < ink.length; seed++) {
            if (!ink[seed] || seen[seed]) continue;
            int count = 1, cursor = 0;
            queue[0] = seed;
            seen[seed] = true;
            int minX = width, maxX = 0, minY = height, maxY = 0, tipX = 0;
            while (cursor < count) {
                int p = queue[cursor++], xx = p % width, yy = p / width;
                minX = Math.min(minX, xx);
                maxX = Math.max(maxX, xx);
                maxY = Math.max(maxY, yy);
                if (yy < minY) {
                    minY = yy;
                    tipX = xx;
                }
                for (int dy = -3; dy <= 3; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = xx + dx, ny = yy + dy;
                        if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                        int q = ny * width + nx;
                        if (ink[q] && !seen[q]) {
                            seen[q] = true;
                            queue[count++] = q;
                        }
                    }
            }
            if (maxX - minX < gap * .7
                    || maxY - minY < gap * .7
                    || left + minX > fret.x() + gap * .85
                    || top + maxY < fret.y() - gap * .85
                    || Math.abs(left + tipX - (amount.left() + amount.right()) * w / 2) > gap
                    || tipX - minX < gap * .5) continue;
            boolean leftArm = false, rightArm = false;
            for (int i = 0; i < count; i++) {
                int xx = queue[i] % width, yy = queue[i] / width;
                if (yy - minY < gap * .15 || yy - minY > gap * .5) continue;
                if (xx < tipX - gap * .15) leftArm = true;
                if (xx > tipX + gap * .15) rightArm = true;
            }
            if (!leftArm || !rightArm) continue;
            // A return stroke must reconnect to the original height to the right of the peak.
            boolean returns = false;
            for (int i = 0; i < count; i++)
                if (queue[i] % width > tipX + gap * .6
                        && top + queue[i] / width > fret.y() - gap * .7) returns = true;
            return returns ? 2 : 1;
        }
        return 0;
    }

    private static TablatureDecoder.Fret marked(TablatureDecoder.Fret f, int marks) {
        return new TablatureDecoder.Fret(
                f.x(),
                f.y(),
                f.string(),
                f.fret(),
                f.duration(),
                f.beams(),
                f.dots(),
                marks,
                f.tied(),
                f.tuplet(),
                f.wholeRestGlyph());
    }
}
