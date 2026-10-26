// Copyright 2026 Luckolite and Music Sheets contributors. SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Anchored raw five-rule binding only. Never a verified track, carrier or musical clock. */
final class PrintedBeamLocalFrame {
    private PrintedBeamLocalFrame() {}

    static final class Witness {
        private final PrintedBeamShaftPixels.Frame frame;
        private final ScorePageInterpretation score;
        private final byte[] raw;
        private final int width, height, page;
        private final PrintedSlurOwners.GeometryHead head;
        private final ScoreNoteEvent note;
        private final PlayingTechniqueDetector.Staff staff;
        private final FaintQuarterRestInk.Budget budget;
        private final float bottom, gap;

        private Witness(
                PrintedBeamShaftPixels.Frame frame,
                ScorePageInterpretation score,
                byte[] raw,
                int width,
                int height,
                int page,
                PrintedSlurOwners.GeometryHead head,
                ScoreNoteEvent note,
                PlayingTechniqueDetector.Staff staff,
                FaintQuarterRestInk.Budget budget,
                float bottom,
                float gap) {
            this.frame = frame;
            this.score = score;
            this.raw = raw;
            this.width = width;
            this.height = height;
            this.page = page;
            this.head = head;
            this.note = note;
            this.staff = staff;
            this.budget = budget;
            this.bottom = bottom;
            this.gap = gap;
        }

        boolean matches(
                PrintedBeamShaftPixels.Frame frame,
                ScorePageInterpretation score,
                byte[] raw,
                int width,
                int height,
                int page,
                PrintedSlurOwners.GeometryHead head,
                ScoreNoteEvent note,
                PlayingTechniqueDetector.Staff staff,
                FaintQuarterRestInk.Budget budget) {
            FaintQuarterRestInk.charge(this.budget, 64, 0);
            return this.frame == frame
                    && this.score == score
                    && this.raw == raw
                    && this.width == width
                    && this.height == height
                    && this.page == page
                    && this.head == head
                    && this.note == note
                    && this.staff == staff
                    && this.budget == budget
                    && head.owner() >= 0
                    && head.owner() < score.notes().size()
                    && score.notes().get(head.owner()) == note;
        }

        float bottom() {
            return bottom;
        }

        float gap() {
            return gap;
        }
    }

    static Witness at(
            PrintedBeamShaftPixels.Frame frame,
            ScorePageInterpretation score,
            byte[] raw,
            int width,
            int height,
            int page,
            PrintedSlurOwners.GeometryHead head,
            ScoreNoteEvent note,
            PlayingTechniqueDetector.Staff staff,
            FaintQuarterRestInk.Budget budget) {
        if (budget == null) throw new IllegalArgumentException("Missing local binding meter");
        FaintQuarterRestInk.charge(budget, 128, 0);
        if (frame == null || score == null || head == null || note == null || staff == null)
            return null;
        frame.check(score, raw, width, height, page);
        float gap = staff.gap();
        if (head.owner() < 0
                || head.owner() >= score.notes().size()
                || score.notes().get(head.owner()) != note
                || !Float.isFinite(head.x())
                || !Float.isFinite(head.y())
                || head.x() < 0
                || head.x() >= width
                || head.y() < 0
                || head.y() >= height
                || !Float.isFinite(staff.top() + staff.bottom() + gap)
                || gap < 3
                || gap > 64
                || staff.top() < 0
                || staff.bottom() >= height
                || staff.top() >= staff.bottom()
                || note.staffIndex() != staff.index()
                || note.staffCount() != staff.count()) return null;
        int left = (int) Math.floor(head.x() - gap * .8f),
                right = (int) Math.ceil(head.x() + gap * .8f);
        float[] result = frame(raw, width, height, left, right, staff, budget);
        if (result == null
                || Math.abs(result[0] - staff.bottom()) > gap * .4f
                || Math.abs(result[1] - gap) > gap * .12f
                || Math.abs(head.y() - (result[0] - note.staffStep() * result[1] * .5f))
                        > result[1] * .45f) return null;
        FaintQuarterRestInk.charge(budget, 64, 256);
        return new Witness(
                frame, score, raw, width, height, page, head, note, staff, budget, result[0],
                result[1]);
    }

    private static float[] frame(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            PlayingTechniqueDetector.Staff staff,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        float gap = staff.gap();
        int a = Math.max(0, Math.round(left - gap * 12)),
                b = Math.max(0, Math.round(left - gap * .75f));
        int c = Math.min(width, Math.round(right + gap * .75f)),
                d = Math.min(width, Math.round(right + gap * 12));
        if (b - a < gap || d - c < gap) return null;
        int flank = Math.max(2, Math.round(gap * .32f));
        int first = Math.max(flank, (int) Math.floor(staff.top() - gap * .8f));
        int last = Math.min(height - flank, (int) Math.ceil(staff.bottom() + gap * .8f));
        if (last <= first) return null;
        var leftRules = rules(gray, width, a, b, first, last, flank, staff, quarterBudget);
        var rightRules = rules(gray, width, c, d, first, last, flank, staff, quarterBudget);
        if (leftRules == null || rightRules == null) return null;
        float fraction = ((left + right) * .5f - (a + b) * .5f) / ((c + d - a - b) * .5f);
        float[] rows =
                (quarterBudget == null
                                || quarterBudget.allocate(
                                        (Math.max(0L, (long) (5))) + 1,
                                        32L * 1 + 8L * (Math.max(0L, (long) (5))))
                        ? new float[5]
                        : null);
        float mean = 0;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int i = 0; i < 5; i++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                // Compare rise to the actual horizontal baseline, as the legacy binding does.
                // Vertical staff spacing is independent of the distance between these flank
                // samples.
                if (Math.abs(leftRules[i] - rightRules[i]) > ((c + d - a - b) * .5f) * .2f)
                    return null;
                rows[i] = leftRules[i] + (rightRules[i] - leftRules[i]) * fraction;
                mean += rows[i] / 5;
            }
        }

        float fittedGap = 0;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int i = 0; i < 5; i++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                fittedGap += (i - 2) * (rows[i] - mean) / 10;
            }
        }

        if (fittedGap < gap * .88f || fittedGap > gap * 1.12f) return null;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int i = 0; i < 5; i++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                if (Math.abs(rows[i] - (mean + (i - 2) * fittedGap)) > gap * .12f + .5f)
                    return null;
            }
        }

        return (quarterBudget == null
                        || quarterBudget.allocate(
                                (Math.max(0L, (long) (2))) + 1,
                                32L * 1 + 8L * (Math.max(0L, (long) (2))))
                ? new float[] {mean + 2 * fittedGap, fittedGap}
                : null);
    }

    private static int[] rules(
            byte[] gray,
            int width,
            int left,
            int right,
            int first,
            int last,
            int flank,
            PlayingTechniqueDetector.Staff staff,
            FaintQuarterRestInk.Budget quarterBudget) {
        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

        int[] strength =
                (quarterBudget == null
                                || quarterBudget.allocate(
                                        (Math.max(0L, (long) (last - first))) + 1,
                                        32L * 1 + 8L * (Math.max(0L, (long) (last - first))))
                        ? new int[last - first]
                        : null);
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
            for (int y = first; y < last; y++) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int x = left; x < right; x++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                        int ink = gray[y * width + x] & 255;
                        if (thin(gray, width, x, y, flank, staff.gap(), quarterBudget))
                            strength[y - first]++;
                    }
                }
            }
        }

        int[] found = null;
        {
            if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 64);
            for (var lines :
                    RawStaffLineDetector.detectFromStrength(
                            strength,
                            Math.max(4, Math.round((right - left) * .5f)),
                            last - first,
                            quarterBudget)) {
                if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);

                if (lines.gap() < staff.gap() * .88f
                        || lines.gap() > staff.gap() * 1.12f
                        || Math.abs(lines.bottom() + first - staff.bottom()) > staff.gap() * .8f)
                    continue;
                int[] rows =
                        (quarterBudget == null
                                        || quarterBudget.allocate(
                                                1L + (lines.rows()).length,
                                                32L + 8L * (lines.rows()).length)
                                ? lines.rows().clone()
                                : null);
                {
                    if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                    for (int i = 0; i < rows.length; i++) {
                        if (quarterBudget != null) FaintQuarterRestInk.charge(quarterBudget, 1, 0);
                        rows[i] += first;
                    }
                }

                if (found != null
                        && !((quarterBudget == null
                                        || quarterBudget.allocate(
                                                1L
                                                        + (found == null ? 0 : found.length)
                                                        + (rows == null ? 0 : rows.length),
                                                0))
                                && java.util.Arrays.equals(found, rows))) return null;
                found = rows;
            }
        }

        return found;
    }

    private static int pixel(byte[] raw, int w, int x, int y, FaintQuarterRestInk.Budget q) {
        FaintQuarterRestInk.charge(q, 1, 0);
        return raw[y * w + x] & 255;
    }

    private static boolean thin(
            byte[] raw, int w, int x, int y, int flank, float gap, FaintQuarterRestInk.Budget q) {
        FaintQuarterRestInk.charge(q, 8, 0);
        int ink = pixel(raw, w, x, y, q);
        if (ink > 225) return false;
        int paper = Math.min(pixel(raw, w, x, y - flank, q), pixel(raw, w, x, y + flank, q));
        if (paper < ink + 12) return false;
        double threshold = paper - (paper - ink) * .5;
        int lo = y, hi = y;
        while (lo > y - flank) {
            FaintQuarterRestInk.charge(q, 1, 0);
            if (pixel(raw, w, x, lo - 1, q) >= threshold) break;
            lo--;
        }
        while (hi < y + flank) {
            FaintQuarterRestInk.charge(q, 1, 0);
            if (pixel(raw, w, x, hi + 1, q) >= threshold) break;
            hi++;
        }
        return lo > y - flank && hi < y + flank && hi - lo + 1 <= Math.ceil(gap * .3f);
    }
}
