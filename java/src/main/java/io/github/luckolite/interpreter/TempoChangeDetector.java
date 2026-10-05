// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Conservative detector for printed note-equals-number tempo marks. */
final class TempoChangeDetector {
    private TempoChangeDetector() {}

    /** OCR may drop the note and equals sign while retaining a separate BPM numeral. */
    static List<MeasureNumberReconciler.NumberToken> withIsolatedDigits(
            List<MeasureNumberReconciler.NumberToken> tokens,
            List<PlayingTechniqueDetector.Word> words,
            byte[] gray,
            int width,
            int height) {
        var result = new ArrayList<>(tokens);
        if (gray == null || gray.length != width * height) return List.copyOf(result);
        for (var word : words) {
            String text = word.text().trim();
            if (!text.matches("[0-9]{2,3}")) continue;
            int value = Integer.parseInt(text);
            if (value < 30 || value > 400) continue;
            var token =
                    new MeasureNumberReconciler.NumberToken(
                            value, word.left(), word.top(), word.right(), word.bottom());
            int equals = equalsSignLeft(token, gray, width, height);
            if (equals < 0) continue;
            var bounded = printedDigitBounds(token, gray, width, height);
            if (!Double.isFinite(printedBeatUnit(bounded, gray, width, height, equals, 200))
                    && !Double.isFinite(printedBeatUnit(bounded, gray, width, height, equals, 165)))
                continue;
            if (result.stream()
                    .noneMatch(
                            old ->
                                    old.value() == value
                                            && old.left() < word.right()
                                            && old.right() > word.left()
                                            && old.top() < word.bottom()
                                            && old.bottom() > word.top())) result.add(token);
        }
        return List.copyOf(result);
    }

    static List<ScoreTempoChange> detect(
            List<MeasureNumberReconciler.NumberToken> tokens,
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures) {
        if (tokens == null
                || tokens.isEmpty()
                || gray == null
                || gray.length != width * height
                || width <= 0
                || height <= 0
                || measures == null
                || measures.isEmpty()) return List.of();
        List<ScoreTempoChange> result = new ArrayList<>();
        // Measure boxes include clefs and ledger ink above the actual staff.
        // Use a verified five-line top for direction ownership, never for score geometry.
        // The verified staff top is needed only after a token has an actual tempo equation.
        // Keep the original eager failure route for malformed lists or overflow-sized rasters.
        List<MeasureRegion> directionMeasures =
                canDeferStaffSearch(tokens, gray, width, height, measures)
                        ? null
                        : directionMeasures(measures, gray, width, height);
        for (MeasureNumberReconciler.NumberToken token : tokens) {
            if (token.value() < 30 || token.value() > 400) continue;
            int equalsLeft = equalsSignLeft(token, gray, width, height);
            if (equalsLeft < 0) continue;
            if (directionMeasures == null)
                directionMeasures = directionMeasures(measures, gray, width, height);
            token = printedDigitBounds(token, gray, width, height);
            // A direction such as "Lento, poco rubato (quarter = c. 88)" starts at the
            // change; the digits can extend to the next bar. Keep their ink box for symbol
            // recognition, but attach the change to the start of its OCR direction line.
            var anchor =
                    new MeasureNumberReconciler.NumberToken(
                            token.value(),
                            token.annotationLeft(),
                            token.top(),
                            token.annotationLeft() + (token.right() - token.left()),
                            token.bottom());
            int measureIndex =
                    nearestFollowingMeasure(
                            anchor,
                            directionMeasures,
                            token.annotationLeft() >= token.left() - .001f);
            // OCR may include the tempo note/equal sign in a line starting left of
            // the first bar. The verified digits may still clearly belong to the
            // opening staff. Do not relax ink checks or relocate later directions.
            if (measureIndex < 0 && nearestFollowingMeasure(token, directionMeasures, true) == 0)
                measureIndex = 0;
            if (measureIndex < 0) continue;
            MeasureRegion measure = directionMeasures.get(measureIndex);
            float position =
                    (anchor.left() - measure.left())
                            / Math.max(.0001f, measure.right() - measure.left());
            // Engravers place the number to the right of the note/equal sign. Snap a mark printed
            // near the barline to beat zero instead of delaying the change by the text width.
            position = Math.max(0f, Math.min(1f, position));
            if (measureIndex == 0 && token.bottom() <= measure.top()) position = 0f;
            if (position < .22f) position = 0f;
            double unit = printedBeatUnit(token, gray, width, height, equalsLeft);
            result.add(new ScoreTempoChange(measureIndex, position, token.value() * unit, unit));
        }
        result.sort(
                Comparator.comparingInt(ScoreTempoChange::measureIndex)
                        .thenComparingDouble(ScoreTempoChange::positionInMeasure));
        List<ScoreTempoChange> deduplicated = new ArrayList<>();
        for (ScoreTempoChange change : result) {
            if (!deduplicated.isEmpty()) {
                ScoreTempoChange previous = deduplicated.get(deduplicated.size() - 1);
                if (previous.measureIndex() == change.measureIndex()
                        && previous.bpm() == change.bpm()) continue;
            }
            deduplicated.add(change);
        }
        return List.copyOf(deduplicated);
    }

    private static boolean canDeferStaffSearch(
            List<MeasureNumberReconciler.NumberToken> tokens,
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures) {
        if ((long) width * height != gray.length) return false;
        for (Object measure : measures) if (!(measure instanceof MeasureRegion)) return false;
        for (Object token : tokens)
            if (!(token instanceof MeasureNumberReconciler.NumberToken)) return false;
        return true;
    }

    private static List<MeasureRegion> directionMeasures(
            List<MeasureRegion> measures, byte[] gray, int width, int height) {
        var staffs = RawStaffLineDetector.detect(gray, width, height);
        List<MeasureRegion> result = new ArrayList<>();
        for (var measure : measures) {
            float top = measure.top();
            for (var staff : staffs) {
                float delta = staff.top() - measure.top() * height;
                if (delta >= 0
                        && delta <= staff.gap() * 3
                        && staff.bottom() < measure.bottom() * height) {
                    top = staff.top() / (float) height;
                    break;
                }
            }
            result.add(new MeasureRegion(measure.left(), measure.right(), top, measure.bottom()));
        }
        return result;
    }

    /** OCR direction padding may cross the staff even though the actual digits do not. */
    private static MeasureNumberReconciler.NumberToken printedDigitBounds(
            MeasureNumberReconciler.NumberToken token, byte[] gray, int width, int height) {
        int top = height, bottom = -1, count = 0;
        for (int y = Math.max(0, Math.round(token.top() * height));
                y <= Math.min(height - 1, Math.round(token.bottom() * height));
                y++)
            for (int x = Math.max(0, Math.round(token.left() * width));
                    x <= Math.min(width - 1, Math.round(token.right() * width));
                    x++)
                if ((gray[y * width + x] & 255) <= 125) {
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                    count++;
                }
        if (count < 6 || bottom - top < 3) return token;
        // Generous OCR padding can include a separated slur or staff stroke.
        // Retain the dominant tall digit band only when every other band is thin.
        List<int[]> bands = new ArrayList<>();
        int start = -1;
        for (int y = top; y <= bottom + 1; y++) {
            boolean ink = false;
            if (y <= bottom)
                for (int x = Math.max(0, Math.round(token.left() * width));
                        x <= Math.min(width - 1, Math.round(token.right() * width));
                        x++)
                    if ((gray[y * width + x] & 255) <= 125) {
                        ink = true;
                        break;
                    }
            if (ink && start < 0) start = y;
            if (!ink && start >= 0) {
                bands.add(new int[] {start, y - 1});
                start = -1;
            }
        }
        if (bands.size() > 1) {
            int[] main =
                    bands.stream()
                            .max(Comparator.comparingInt(b -> b[1] - b[0]))
                            .orElseThrow(java.util.NoSuchElementException::new);
            int span = main[1] - main[0] + 1;
            boolean separate = span >= 6;
            for (int[] band : bands)
                if (band != main) {
                    int clearance = Math.max(main[0] - band[1] - 1, band[0] - main[1] - 1);
                    if (band[1] - band[0] + 1 > span * .3f || clearance < span * .3f)
                        separate = false;
                }
            if (separate) {
                top = main[0];
                bottom = main[1];
            }
        }
        return new MeasureNumberReconciler.NumberToken(
                token.value(),
                token.left(),
                top / (float) height,
                token.right(),
                (bottom + 1) / (float) height,
                token.annotationLeft());
    }

    private static int nearestFollowingMeasure(
            MeasureNumberReconciler.NumberToken token,
            List<MeasureRegion> measures,
            boolean inferOpening) {
        float centerX = (token.left() + token.right()) * .5f;
        float centerY = (token.top() + token.bottom()) * .5f;
        for (MeasureRegion staff : measures) {
            if (centerX >= staff.left() - .025f
                    && centerX <= staff.right() + .025f
                    && centerY > staff.top() + .002f
                    && centerY < staff.bottom()) return -1;
        }
        int best = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int index = 0; index < measures.size(); index++) {
            MeasureRegion measure = measures.get(index);
            float rowHeight = measure.bottom() - measure.top();
            if (centerX < measure.left() - .025f
                    || centerX > measure.right() + .025f
                    // Clefs, accidentals and ledger lines can OCR as digits with a spurious
                    // nearby '='. A tempo annotation belongs above the staff, not inside it.
                    // Take Flight's treble clef became 47 BPM at measure 13 on the phone.
                    || token.bottom() > measure.top() + Math.min(.002f, rowHeight * .05f)
                    || token.bottom() < measure.top() - Math.max(.015f, rowHeight * 1.1f)) continue;
            float distance = Math.abs(measure.top() - token.bottom());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }
        if (best < 0) return -1;
        MeasureRegion target = measures.get(best);
        float topmost = Float.MAX_VALUE;
        for (MeasureRegion measure : measures) topmost = Math.min(topmost, measure.top());
        float tolerance = Math.max(.012f, (target.bottom() - target.top()) * .22f);
        if (inferOpening
                && Math.abs(target.top() - topmost) <= tolerance
                && token.bottom() <= target.top()) {
            for (int index = 0; index < measures.size(); index++) {
                MeasureRegion measure = measures.get(index);
                if (Math.abs(measure.top() - target.top()) <= tolerance
                        && measure.left() < measures.get(best).left()) best = index;
            }
        }
        return best;
    }

    /** Two separated, short horizontal ink bands immediately left of the BPM digits. */
    private static int equalsSignLeft(
            MeasureNumberReconciler.NumberToken token, byte[] gray, int width, int height) {
        int strong = equalsSignLeft(token, gray, width, height, 125);
        if (strong >= 0) return strong;
        // Faded scans may retain two separated rules only at a lighter level.
        // Require pale paper first; do not turn shadow texture into an equals sign.
        int unit = Math.max(3, Math.round((token.bottom() - token.top()) * height));
        int left = Math.max(0, Math.round(token.left() * width) - unit * 2);
        int right = Math.min(width - 1, Math.round(token.right() * width));
        int top = Math.max(0, Math.round(token.top() * height)),
                bottom = Math.min(height - 1, Math.round(token.bottom() * height));
        int total = 0, paper = 0;
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) {
                total++;
                if ((gray[y * width + x] & 255) >= 230) paper++;
            }
        return total > 0 && paper >= total * .6f
                ? equalsSignLeft(token, gray, width, height, 175)
                : -1;
    }

    private static int equalsSignLeft(
            MeasureNumberReconciler.NumberToken token,
            byte[] gray,
            int width,
            int height,
            int inkLimit) {
        int tokenLeft = Math.max(0, Math.round(token.left() * width));
        int tokenRight = Math.min(width - 1, Math.round(token.right() * width));
        int tokenTop = Math.max(0, Math.round(token.top() * height));
        int tokenBottom = Math.min(height - 1, Math.round(token.bottom() * height));
        int tokenWidth = Math.max(2, tokenRight - tokenLeft + 1);
        int tokenHeight = Math.max(3, tokenBottom - tokenTop + 1);
        // Whole-line OCR can pad small tempo digits with the taller direction
        // text or parentheses. Size the equals strokes from the digits' ink,
        // not that padding, while preserving the original search/anchor bounds.
        int inkTop = height, inkBottom = -1;
        for (int y = tokenTop; y <= tokenBottom; y++)
            for (int x = tokenLeft; x <= tokenRight; x++)
                if ((gray[y * width + x] & 255) <= inkLimit) {
                    inkTop = Math.min(inkTop, y);
                    inkBottom = Math.max(inkBottom, y);
                }
        if (inkBottom >= inkTop) tokenHeight = Math.max(3, inkBottom - inkTop + 1);
        int left = Math.max(0, tokenLeft - Math.max(tokenWidth, tokenHeight * 2));
        int right = Math.min(width - 1, tokenLeft - 1);
        int top = Math.max(0, tokenTop - tokenHeight / 4);
        int bottom = Math.min(height - 1, tokenBottom + tokenHeight / 4);
        if (right < left || bottom < top) return -1;
        // An equals sign is two disconnected, aligned horizontal strokes. Counting arbitrary
        // dark scanlines also counts letter tops/bottoms in titles such as "Op. 101".
        int rw = right - left + 1, rh = bottom - top + 1;
        boolean[] visited = new boolean[rw * rh];
        int[] queue = new int[rw * rh];
        List<int[]> bands = new ArrayList<>();
        for (int sy = 0; sy < rh; sy++)
            for (int sx = 0; sx < rw; sx++) {
                int seed = sy * rw + sx;
                if (visited[seed] || (gray[(top + sy) * width + left + sx] & 0xff) > inkLimit)
                    continue;
                int read = 0, size = 1, minX = sx, maxX = sx, minY = sy, maxY = sy;
                queue[0] = seed;
                visited[seed] = true;
                while (read < size) {
                    int point = queue[read++], x = point % rw, y = point / rw;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || ny < 0 || nx >= rw || ny >= rh) continue;
                            int next = ny * rw + nx;
                            if (!visited[next]
                                    && (gray[(top + ny) * width + left + nx] & 0xff) <= inkLimit) {
                                visited[next] = true;
                                queue[size++] = next;
                            }
                        }
                }
                int bw = maxX - minX + 1, bh = maxY - minY + 1;
                if (bw >= Math.max(3, Math.round(tokenHeight * .22f))
                        && bw >= bh * 2
                        && bw <= tokenHeight * 1.5f
                        && bh <= Math.max(2, tokenHeight * .28f))
                    bands.add(new int[] {minX, maxX, minY, maxY});
            }
        int minimumSeparation = Math.max(2, Math.round(tokenHeight * .12f));
        int maximumSeparation = Math.max(minimumSeparation, Math.round(tokenHeight * .82f));
        for (int first = 0; first < bands.size(); first++)
            for (int second = first + 1; second < bands.size(); second++) {
                int[] a = bands.get(first), b = bands.get(second);
                float separation = Math.abs((b[2] + b[3] - a[2] - a[3]) * .5f);
                int overlap = Math.min(a[1], b[1]) - Math.max(a[0], b[0]) + 1;
                int wider = Math.max(a[1] - a[0] + 1, b[1] - b[0] + 1);
                if (overlap >= wider * .7f
                        && separation >= minimumSeparation
                        && separation <= maximumSeparation) return left + Math.min(a[0], b[0]);
            }
        return -1;
    }

    /** Quarter-beat length of a tempo note, retaining hollow heads, flags and augmentation dots. */
    private static double printedBeatUnit(
            MeasureNumberReconciler.NumberToken token,
            byte[] gray,
            int width,
            int height,
            int equalsLeft) {
        int digitHeight = printedDigitHeight(token, gray, width, height);
        double normal =
                printedBeatUnitWithDigitHeight(
                        token, gray, width, height, equalsLeft, 200, digitHeight);
        double core =
                printedBeatUnitWithDigitHeight(
                        token, gray, width, height, equalsLeft, 165, digitHeight);
        if (!Double.isFinite(normal)) normal = Double.isFinite(core) ? core : 1;
        if (!Double.isFinite(core)) core = normal;
        // A light scan bridge can attach the dot to its notehead. Only use the
        // darker segmentation to recover a complete dot, not to erase faint ink.
        return core == normal * 1.5 || normal == 1 && (core == 2 || core == 3) ? core : normal;
    }

    private static double printedBeatUnit(
            MeasureNumberReconciler.NumberToken token,
            byte[] gray,
            int width,
            int height,
            int equalsLeft,
            int inkLimit) {
        return printedBeatUnitWithDigitHeight(
                token,
                gray,
                width,
                height,
                equalsLeft,
                inkLimit,
                printedDigitHeight(token, gray, width, height));
    }

    private static int printedDigitHeight(
            MeasureNumberReconciler.NumberToken token, byte[] gray, int width, int height) {
        int unit = Math.max(3, Math.round((token.bottom() - token.top()) * height));
        // OCR boxes may include generous vertical padding (ML Kit's 38px box surrounds
        // 23px digits here). Measure the printed ink before comparing note/dot geometry.
        int inkTop = height, inkBottom = -1;
        for (int y = Math.max(0, Math.round(token.top() * height));
                y <= Math.min(height - 1, Math.round(token.bottom() * height));
                y++)
            for (int x = Math.max(0, Math.round(token.left() * width));
                    x <= Math.min(width - 1, Math.round(token.right() * width));
                    x++)
                if ((gray[y * width + x] & 255) <= 125) {
                    inkTop = Math.min(inkTop, y);
                    inkBottom = Math.max(inkBottom, y);
                }
        if (inkBottom >= inkTop) unit = Math.max(3, inkBottom - inkTop + 1);
        return unit;
    }

    private static double printedBeatUnitWithDigitHeight(
            MeasureNumberReconciler.NumberToken token,
            byte[] gray,
            int width,
            int height,
            int equalsLeft,
            int inkLimit,
            int unit) {
        int left = Math.max(0, equalsLeft - unit * 3), right = equalsLeft - 1;
        int top = Math.max(0, Math.round(token.top() * height) - unit);
        int bottom = Math.min(height - 1, Math.round(token.bottom() * height) + unit / 3);
        int rw = right - left + 1, rh = bottom - top + 1;
        if (rw <= 0 || rh <= 0) return Double.NaN;
        boolean[] seen = new boolean[rw * rh];
        int[] queue = new int[seen.length];
        List<int[]> parts = new ArrayList<>();
        for (int sy = 0; sy < rh; sy++)
            for (int sx = 0; sx < rw; sx++) {
                int seed = sy * rw + sx;
                if (seen[seed] || (gray[(top + sy) * width + left + sx] & 255) > inkLimit) continue;
                int count = 1, read = 0, minX = sx, maxX = sx, minY = sy, maxY = sy;
                seen[seed] = true;
                queue[0] = seed;
                while (read < count) {
                    int at = queue[read++], x = at % rw, y = at / rw;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || ny < 0 || nx >= rw || ny >= rh) continue;
                            int next = ny * rw + nx;
                            if (!seen[next]
                                    && (gray[(top + ny) * width + left + nx] & 255) <= inkLimit) {
                                seen[next] = true;
                                queue[count++] = next;
                            }
                        }
                }
                parts.add(new int[] {left + minX, left + maxX, top + minY, top + maxY, count});
            }
        for (int[] note : parts) {
            int nw = note[1] - note[0] + 1, nh = note[3] - note[2] + 1;
            if (nh < unit * 1.2f || nh > unit * 2.8f || nw < unit * .25f || nw > unit * 1.5f)
                continue;
            // The head's centre is dark for a quarter; a half-note centre remains paper.
            int cy = note[3] - Math.max(1, Math.round(unit * .12f));
            int headLeft = note[1], headRight = note[0];
            for (int x = note[0]; x <= note[1]; x++)
                if ((gray[cy * width + x] & 255) <= 200) {
                    headLeft = Math.min(headLeft, x);
                    headRight = Math.max(headRight, x);
                }
            int cx = (headLeft + headRight) / 2;
            int[] pocket = closedTempoHead(gray, width, note, unit, inkLimit);
            boolean hollow = pocket != null;
            if (hollow) {
                cx = pocket[0];
                cy = pocket[1];
            } else if ((gray[cy * width + cx] & 255) > 125) continue;
            // Locate the long upright stem independently of the flag's bounding box.
            int stem = -1, best = 0;
            for (int x = note[0]; x <= note[1]; x++) {
                int ink = 0;
                for (int y = note[2]; y < note[2] + nh * 2 / 3; y++)
                    if ((gray[y * width + x] & 255) <= inkLimit) ink++;
                if (ink >= best) {
                    best = ink;
                    stem = x;
                }
            }
            if (stem < 0 || best < nh * .5f) continue;
            int flaggedRows = 0;
            for (int y = note[2]; y < note[2] + nh * 2 / 3; y++) {
                boolean protrudes = false;
                for (int x = stem + Math.max(2, Math.round(unit * .2f)); x <= note[1]; x++)
                    if ((gray[y * width + x] & 255) <= inkLimit) {
                        protrudes = true;
                        break;
                    }
                if (protrudes) flaggedRows++;
            }
            boolean flagged = flaggedRows >= Math.max(3, Math.round(unit * .25f));
            if (hollow && flagged) continue;
            double beat = hollow ? 2 : flagged ? .5 : 1;
            for (int[] dot : parts) {
                int dw = dot[1] - dot[0] + 1, dh = dot[3] - dot[2] + 1;
                float gap = dot[0] - note[1], dy = Math.abs((dot[2] + dot[3]) * .5f - cy);
                // The light fringe of '=' can extend left of its dark-core bound.
                // It is clipped by this search window, unlike a complete printed dot.
                if (dot[1] < right
                        && gap > 0
                        && gap < unit * .6f
                        && dw >= 2
                        && dh >= 2
                        && dw <= unit * .35f
                        && dh <= unit * .35f
                        && dy <= unit * .22f
                        && dot[4] >= dw * dh * .45f) return beat * 1.5;
            }
            return beat;
        }
        return Double.NaN;
    }

    /** A half head needs a paper pocket enclosed by ink, not merely a light letter edge. */
    private static int[] closedTempoHead(
            byte[] gray, int width, int[] note, int unit, int inkLimit) {
        int w = note[1] - note[0] + 1, h = note[3] - note[2] + 1;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        for (int start = 0; start < seen.length; start++) {
            if (seen[start]
                    || (gray[(note[2] + start / w) * width + note[0] + start % w] & 255)
                            <= inkLimit) continue;
            int size = 1, take = 0, minX = w, maxX = 0, minY = h, maxY = 0;
            boolean edge = false;
            queue[0] = start;
            seen[start] = true;
            while (take < size) {
                int p = queue[take++], x = p % w, y = p / w;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                if (x == 0 || x == w - 1 || y == 0 || y == h - 1) edge = true;
                for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                    int nx = x + d[0], ny = y + d[1];
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                    int next = ny * w + nx;
                    if (!seen[next]
                            && (gray[(note[2] + ny) * width + note[0] + nx] & 255) > inkLimit) {
                        seen[next] = true;
                        queue[size++] = next;
                    }
                }
            }
            if (!edge
                    && size >= Math.max(2, unit * unit * .008f)
                    && size <= unit * unit * .18f
                    && maxX - minX + 1 >= unit * .12f
                    && maxY - minY + 1 >= unit * .08f
                    && maxX - minX + 1 <= unit * .8f
                    && maxY - minY + 1 <= unit * .45f
                    && minY >= h - unit * .6f)
                return new int[] {note[0] + (minX + maxX) / 2, note[2] + (minY + maxY) / 2};
        }
        return null;
    }
}
