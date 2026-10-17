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
            boolean merged = !text.matches("[0-9]{2,3}");
            MeasureNumberReconciler.NumberToken token;
            if (merged) {
                token = equalsPrefixedDigits(word, gray, width, height);
                if (token == null) continue;
            } else {
                int value = Integer.parseInt(text);
                if (value < 30 || value > 400) continue;
                token =
                        new MeasureNumberReconciler.NumberToken(
                                value, word.left(), word.top(), word.right(), word.bottom());
            }
            int value = token.value();
            int equals = equalsSignLeft(token, gray, width, height);
            if (equals < 0) continue;
            var bounded = printedDigitBounds(token, gray, width, height);
            {
                int[] anchor = {-1};
                int digitHeight = printedDigitHeight(bounded, gray, width, height);
                double unit =
                        printedBeatUnitWithDigitHeight(
                                bounded, gray, width, height, equals, 200, digitHeight, anchor);
                if (!Double.isFinite(unit))
                    unit =
                            printedBeatUnitWithDigitHeight(
                                    bounded, gray, width, height, equals, 165, digitHeight, anchor);
                if (!Double.isFinite(unit) || anchor[0] < 0) continue;
                // The printed beat note owns the direction start, before OCR's equals/digit edge.
                token =
                        new MeasureNumberReconciler.NumberToken(
                                value,
                                bounded.left(),
                                bounded.top(),
                                bounded.right(),
                                bounded.bottom(),
                                anchor[0] / (float) width);
            }
            final var accepted = token;
            // Replace only an overlapping OCR estimate that cannot prove its equals sign.
            result.removeIf(
                    old ->
                            old.value() == accepted.value()
                                    && old.left() < word.right()
                                    && old.right() > word.left()
                                    && old.top() < word.bottom()
                                    && old.bottom() > word.top()
                                    && equalsSignLeft(old, gray, width, height) < 0);
            if (result.stream()
                    .noneMatch(
                            old ->
                                    old.value() == accepted.value()
                                            && old.left() < word.right()
                                            && old.right() > word.left()
                                            && old.top() < word.bottom()
                                            && old.bottom() > word.top())) result.add(accepted);
        }
        return List.copyOf(result);
    }

    /** Merged OCR still needs separate printed digits and a complete optical tempo equation. */
    private static MeasureNumberReconciler.NumberToken equalsPrefixedDigits(
            PlayingTechniqueDetector.Word word, byte[] gray, int width, int height) {
        String text = word.text().trim();
        var match =
                java.util.regex.Pattern.compile(
                                "^(?:[Jj♩♪]\\s*\\.?\\s*)?=\\s*([0-9]{2,3})(?![0-9])")
                        .matcher(text);
        if (!match.find() || text.substring(match.end()).trim().matches("[-–—/].*|[.,]\\s*[0-9].*"))
            return null;
        int value = Integer.parseInt(match.group(1)), digits = match.group(1).length();
        if (value < 30
                || value > 400
                || width < 1
                || height < 1
                || gray.length != (long) width * height
                || !Float.isFinite(word.left())
                || !Float.isFinite(word.right())
                || !Float.isFinite(word.top())
                || !Float.isFinite(word.bottom())
                || word.left() < 0
                || word.top() < 0
                || word.right() > 1
                || word.bottom() > 1) return null;
        int left = Math.round(word.left() * width),
                right = Math.min(width - 1, Math.round(word.right() * width));
        int top = Math.round(word.top() * height),
                bottom = Math.min(height - 1, Math.round(word.bottom() * height));
        int w = right - left + 1, h = bottom - top + 1;
        if (w < 1 || h < 3 || (long) w * h > 500_000) return null;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        List<int[]> parts = new ArrayList<>();
        for (int sy = 0; sy < h; sy++)
            for (int sx = 0; sx < w; sx++) {
                int seed = sy * w + sx;
                if (seen[seed] || (gray[(top + sy) * width + left + sx] & 255) > 155) continue;
                int read = 0, count = 1, minX = sx, maxX = sx, minY = sy, maxY = sy;
                seen[seed] = true;
                queue[0] = seed;
                while (read < count) {
                    int at = queue[read++], x = at % w, y = at / w;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                            int next = ny * w + nx;
                            if (!seen[next]
                                    && (gray[(top + ny) * width + left + nx] & 255) <= 155) {
                                seen[next] = true;
                                queue[count++] = next;
                            }
                        }
                }
                if (count >= 3)
                    parts.add(new int[] {left + minX, top + minY, left + maxX, top + maxY, count});
            }
        parts.sort(Comparator.comparingInt(p -> p[0]));
        int[] first = null, previous = null;
        int n = 0, l = width, r = -1, t = height, b = -1;
        for (int[] part : parts) {
            int pw = part[2] - part[0] + 1, ph = part[3] - part[1] + 1;
            if (ph < h * .35f) continue;
            if (first == null && beforePrintedEquals(part, parts, h)) continue;
            if (pw > ph * .95f) return null;
            if (first == null) first = part;
            int fh = first[3] - first[1] + 1;
            if (Math.abs(part[1] - first[1]) > Math.max(2, fh * .2f)
                    || Math.abs(part[3] - first[3]) > Math.max(2, fh * .2f)
                    || previous != null
                            && (part[0] <= previous[2] || part[0] - previous[2] > fh * .65f))
                return null;
            l = Math.min(l, part[0]);
            r = Math.max(r, part[2]);
            t = Math.min(t, part[1]);
            b = Math.max(b, part[3]);
            previous = part;
            if (++n == digits) break;
        }
        return n == digits
                ? new MeasureNumberReconciler.NumberToken(
                        value,
                        l / (float) width,
                        t / (float) height,
                        r / (float) width,
                        b / (float) height)
                : null;
    }

    /** OCR can include the beat glyph even when its text begins at the equals sign. */
    private static boolean beforePrintedEquals(int[] part, List<int[]> parts, int height) {
        for (int i = 0; i < parts.size(); i++) {
            var a = parts.get(i);
            int aw = a[2] - a[0] + 1, ah = a[3] - a[1] + 1;
            if (a[0] <= part[2]
                    || a[0] - part[2] > height
                    || aw < 3
                    || aw < ah * 2
                    || ah > height * .2f) continue;
            for (int j = i + 1; j < parts.size(); j++) {
                var b = parts.get(j);
                int bw = b[2] - b[0] + 1, bh = b[3] - b[1] + 1;
                if (bw < 3 || bw < bh * 2 || bh > height * .2f) continue;
                float separation = Math.abs((a[1] + a[3] - b[1] - b[3]) * .5f);
                int overlap = Math.min(a[2], b[2]) - Math.max(a[0], b[0]) + 1;
                if (overlap >= Math.max(aw, bw) * .7f
                        && separation >= 2
                        && separation <= height * .5f) return true;
            }
        }
        return false;
    }

    static List<ScoreTempoChange> detect(
            List<MeasureNumberReconciler.NumberToken> tokens,
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures) {
        return detectProved(tokens, gray, width, height, measures, java.util.Map.of());
    }

    private static List<ScoreTempoChange> detectProved(
            List<MeasureNumberReconciler.NumberToken> tokens,
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            java.util.Map<MeasureNumberReconciler.NumberToken, Integer> provedEquals) {
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
            boolean provedApproximation = provedEquals.containsKey(token);
            int equalsLeft =
                    provedApproximation
                            ? provedEquals.get(token)
                            : equalsSignLeft(token, gray, width, height);
            if (equalsLeft < 0) continue;
            if (directionMeasures == null)
                directionMeasures = directionMeasures(measures, gray, width, height);
            token = printedDigitBounds(token, gray, width, height);
            float equationLeft = provedEquationLeft(token, gray, width, height, equalsLeft);
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
            // A section label can enlarge the original direction area above the verified staff.
            // Only a complete OCR/optical equation may use that retained measure envelope.
            if (measureIndex < 0 && provedApproximation)
                measureIndex = approximateFollowingMeasure(anchor, measures);
            if (measureIndex < 0)
                measureIndex =
                        provedOpeningTempo(
                                token, gray, width, height, equalsLeft, directionMeasures);
            if (measureIndex < 0)
                measureIndex =
                        provedElevatedSystemTempo(
                                token, gray, width, height, equalsLeft, directionMeasures);
            if (measureIndex < 0) continue;
            MeasureRegion measure = directionMeasures.get(measureIndex);
            float position =
                    (Math.min(anchor.left(), equationLeft) - measure.left())
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

    /** Retain an earlier verbal prefix, but never let OCR padding move a proved equation right. */
    private static float provedEquationLeft(
            MeasureNumberReconciler.NumberToken token,
            byte[] gray,
            int width,
            int height,
            int equals) {
        int digitHeight = printedDigitHeight(token, gray, width, height);
        int[] light = {-1, -1}, dark = {-1, -1};
        double a =
                printedBeatUnitWithDigitHeight(
                        token, gray, width, height, equals, 200, digitHeight, light);
        double b =
                printedBeatUnitWithDigitHeight(
                        token, gray, width, height, equals, 165, digitHeight, dark);
        if (!Double.isFinite(a)
                || !Double.isFinite(b)
                || Math.abs(a - b) > .001
                || light[0] < 0
                || dark[0] < 0
                || Math.abs(light[0] - dark[0]) > 2) return token.annotationLeft();
        return Math.min(token.annotationLeft(), Math.min(light[0], dark[0]) / (float) width);
    }

    /** A complete equation may precede the playable edge of the opening system. */
    private static int provedOpeningTempo(
            MeasureNumberReconciler.NumberToken token,
            byte[] gray,
            int width,
            int height,
            int equals,
            List<MeasureRegion> measures) {
        if (measures.isEmpty()) return -1;
        int first = 0;
        for (int i = 1; i < measures.size(); i++) {
            var a = measures.get(i);
            var b = measures.get(first);
            if (a.top() < b.top() - .002f
                    || Math.abs(a.top() - b.top()) <= .002f && a.left() < b.left()) first = i;
        }
        var opening = measures.get(first);
        for (var staff : RawStaffLineDetector.detect(gray, width, height)) {
            if (Math.abs(staff.top() - opening.top() * height) > staff.gap() * .3f
                    || staff.bottom() > opening.bottom() * height) continue;
            float y = token.bottom() * height;
            if (y >= staff.top() || staff.top() - y > staff.gap() * 8) continue;
            float left = opening.left() * width - staff.gap() * 10;
            float right = opening.right() * width;
            for (var m : measures)
                if (Math.abs(m.top() - opening.top()) * height < staff.gap())
                    right = Math.max(right, m.right() * width);
            if (token.annotationLeft() * width < left || token.right() * width > right) continue;
            if (Double.isFinite(printedBeatUnit(token, gray, width, height, equals, 200))
                    || Double.isFinite(printedBeatUnit(token, gray, width, height, equals, 165)))
                return first;
        }
        return -1;
    }

    /** A complete tempo equation may sit above a chord row at a later system start. */
    private static int provedElevatedSystemTempo(
            MeasureNumberReconciler.NumberToken token,
            byte[] gray,
            int width,
            int height,
            int equals,
            List<MeasureRegion> measures) {
        float cx = (token.left() + token.right()) * .5f, cy = (token.top() + token.bottom()) * .5f;
        for (var m : measures)
            if (cx >= m.left() - .025f
                    && cx <= m.right() + .025f
                    && cy > m.top() + .002f
                    && cy < m.bottom()) return -1;
        if (!Double.isFinite(printedBeatUnit(token, gray, width, height, equals, 200))
                && !Double.isFinite(printedBeatUnit(token, gray, width, height, equals, 165)))
            return -1;
        int found = -1;
        for (var staff : RawStaffLineDetector.detect(gray, width, height)) {
            float gap = staff.gap(), y = token.bottom() * height;
            if (y >= staff.top() || staff.top() - y > gap * 8) continue;
            int first = -1;
            for (int i = 0; i < measures.size(); i++) {
                var m = measures.get(i);
                if (Math.abs(m.top() * height - staff.top()) > gap * .3f
                        || staff.bottom() > m.bottom() * height) continue;
                if (first < 0 || m.left() < measures.get(first).left()) first = i;
            }
            if (first < 0) continue;
            var m = measures.get(first);
            if (Math.abs(token.annotationLeft() - m.left()) * width > gap * 3
                    || token.right() > m.right()) continue;
            if (found >= 0 && found != first) return -1;
            found = first;
        }
        return found;
    }

    private static int approximateFollowingMeasure(
            MeasureNumberReconciler.NumberToken anchor, List<MeasureRegion> measures) {
        int best = nearestFollowingMeasure(anchor, measures, false);
        if (best < 0) return -1;
        var row = measures.get(best);
        float distance = Float.MAX_VALUE;
        for (int i = 0; i < measures.size(); i++) {
            var m = measures.get(i);
            if (Math.abs(m.top() - row.top()) > .002f
                    || Math.abs(m.bottom() - row.bottom()) > .002f
                    || nearestFollowingMeasure(anchor, List.of(m), false) < 0) continue;
            float horizontal =
                    Math.max(0, Math.max(m.left() - anchor.left(), anchor.left() - m.right()));
            if (horizontal < distance) {
                distance = horizontal;
                best = i;
            }
        }
        return best;
    }

    static List<ScoreTempoChange> detectWithWords(
            List<MeasureNumberReconciler.NumberToken> tokens,
            List<PlayingTechniqueDetector.Word> words,
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures) {
        if (words == null
                || gray == null
                || width <= 0
                || height <= 0
                || (long) width * height != gray.length)
            return detect(tokens, gray, width, height, measures);
        var all = new ArrayList<MeasureNumberReconciler.NumberToken>();
        if (tokens != null) all.addAll(tokens);
        var proved = new java.util.HashMap<MeasureNumberReconciler.NumberToken, Integer>();
        var syntax =
                java.util.regex.Pattern.compile(
                        "^[♩♪♫♬√J.]?\\s*=\\s*(?:ca\\.?|c\\.|circa|~|\u2248)\\s*([0-9]{2,3})$",
                        java.util.regex.Pattern.CASE_INSENSITIVE);
        for (var word : words) {
            if (word == null || word.text() == null) continue;
            var match = syntax.matcher(word.text().trim());
            if (!match.matches()) continue;
            int value = Integer.parseInt(match.group(1)), count = match.group(1).length();
            if (value < 30
                    || value > 400
                    || !Float.isFinite(word.left())
                    || !Float.isFinite(word.right())
                    || !Float.isFinite(word.top())
                    || !Float.isFinite(word.bottom())
                    || word.left() < 0
                    || word.top() < 0
                    || word.right() > 1
                    || word.bottom() > 1) continue;
            int left = Math.round(word.left() * width),
                    right = Math.min(width - 1, Math.round(word.right() * width));
            int top = Math.round(word.top() * height),
                    bottom = Math.min(height - 1, Math.round(word.bottom() * height));
            int h = bottom - top + 1;
            if (h < 3
                    || right <= left
                    || right - left > 12 * h
                    || (long) (right - left + 1) * h > 500_000) continue;
            // The final numeral is a bounded sequence of separate vertical ink groups.
            int groups = 0, digitLeft = -1, digitRight = -1;
            boolean inside = false;
            for (int x = right; x >= left; x--) {
                boolean ink = false;
                for (int y = top; y <= bottom; y++)
                    if ((gray[y * width + x] & 255) <= 155) {
                        ink = true;
                        break;
                    }
                if (ink) {
                    if (!inside) {
                        groups++;
                        if (groups > count) break;
                    }
                    if (digitRight < 0) digitRight = x;
                    digitLeft = x;
                } else if (inside && groups == count) break;
                inside = ink;
            }
            if (groups != count || digitLeft <= left || digitRight < digitLeft) continue;
            var digits =
                    equalsPrefixedDigits(
                            new PlayingTechniqueDetector.Word(
                                    "=" + value,
                                    digitLeft / (float) width,
                                    word.top(),
                                    digitRight / (float) width,
                                    word.bottom()),
                            gray,
                            width,
                            height);
            if (digits == null) continue;
            int dh = printedDigitHeight(digits, gray, width, height);
            if (digitLeft - left > dh * 7) continue;
            int eq = equalsSignLeft(digits, gray, width, height, 125, left);
            if (eq < 0) eq = equalsSignLeft(digits, gray, width, height, 175, left);
            if (eq < left || digitLeft - eq > dh * 6) continue;
            int[] anchor = {-1, -1};
            double unit =
                    printedBeatUnitWithDigitHeight(
                            digits, gray, width, height, eq, 200, dh, anchor);
            if (!Double.isFinite(unit))
                unit =
                        printedBeatUnitWithDigitHeight(
                                digits, gray, width, height, eq, 165, dh, anchor);
            if (!Double.isFinite(unit) || anchor[0] < Math.max(0, left - 2 * dh) || anchor[0] >= eq)
                continue;
            var accepted =
                    new MeasureNumberReconciler.NumberToken(
                            value,
                            digits.left(),
                            digits.top(),
                            digits.right(),
                            digits.bottom(),
                            anchor[1] / (float) width);
            all.add(accepted);
            proved.put(accepted, eq);
        }
        var ordinary = detectProved(all, gray, width, height, measures, proved);
        if (measures == null || measures.isEmpty() || all.size() > 10000 || words.size() > 10000)
            return ordinary;
        var contextualMeasures = directionMeasures(measures, gray, width, height);
        int physicalOpening = OpeningPartTempo.firstMeasure(contextualMeasures);
        if (ordinary.stream()
                .anyMatch(
                        event ->
                                event.measureIndex() == physicalOpening
                                        && event.positionInMeasure() == 0f)) return ordinary;
        var ownedLaterEquations = new java.util.HashSet<PlayingTechniqueDetector.Word>();
        for (var token : all)
            for (var word : words) {
                if (!OpeningPartTempo.equationWord(word, token)) continue;
                int equals = equalsSignLeft(token, gray, width, height);
                if (equals < 0) continue;
                var bounded = printedDigitBounds(token, gray, width, height);
                if (!Double.isFinite(printedBeatUnit(bounded, gray, width, height, equals, 200))
                        && !Double.isFinite(
                                printedBeatUnit(bounded, gray, width, height, equals, 165)))
                    continue;
                var literal = detectProved(List.of(token), gray, width, height, measures, proved);
                if (literal.stream()
                        .anyMatch(
                                event ->
                                        event.measureIndex() == physicalOpening
                                                && event.positionInMeasure() > 0f
                                                && ordinary.contains(event)))
                    ownedLaterEquations.add(word);
            }
        var contextual = new ArrayList<ScoreTempoChange>();
        for (var token : all)
            for (var word : words) {
                if (!OpeningPartTempo.equationWord(word, token)) continue;
                int equals = equalsSignLeft(token, gray, width, height);
                if (equals < 0) continue;
                var bounded = printedDigitBounds(token, gray, width, height);
                double unit = printedBeatUnit(bounded, gray, width, height, equals, 200);
                if (!Double.isFinite(unit))
                    unit = printedBeatUnit(bounded, gray, width, height, equals, 165);
                if (!Double.isFinite(unit)) continue;
                int owner =
                        OpeningPartTempo.owner(
                                word,
                                words,
                                gray,
                                width,
                                height,
                                contextualMeasures,
                                ownedLaterEquations);
                if (owner < 0) continue;
                var event = new ScoreTempoChange(owner, 0, token.value() * unit, unit);
                if (!contextual.contains(event)) contextual.add(event);
            }
        if (contextual.size() != 1 || ordinary.contains(contextual.get(0))) return ordinary;
        var result = new ArrayList<ScoreTempoChange>(ordinary);
        result.add(contextual.get(0));
        result.sort(
                Comparator.comparingInt(ScoreTempoChange::measureIndex)
                        .thenComparingDouble(ScoreTempoChange::positionInMeasure));
        return List.copyOf(result);
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
        float bestDistance = Float.MAX_VALUE, bestHorizontal = Float.MAX_VALUE;
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
            float horizontal =
                    Math.max(
                            0,
                            Math.max(
                                    measure.left() - token.left(), token.left() - measure.right()));
            // Equal staff-row distances must not let a neighboring bar's margin steal the start.
            if (distance < bestDistance
                    || distance == bestDistance && horizontal < bestHorizontal) {
                bestDistance = distance;
                bestHorizontal = horizontal;
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
        return equalsSignLeft(token, gray, width, height, inkLimit, -1);
    }

    private static int equalsSignLeft(
            MeasureNumberReconciler.NumberToken token,
            byte[] gray,
            int width,
            int height,
            int inkLimit,
            int provedTextLeft) {
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
        if (provedTextLeft >= 0) left = Math.max(0, provedTextLeft);
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
        return printedBeatUnitWithDigitHeight(
                token, gray, width, height, equalsLeft, inkLimit, unit, null);
    }

    private static double printedBeatUnitWithDigitHeight(
            MeasureNumberReconciler.NumberToken token,
            byte[] gray,
            int width,
            int height,
            int equalsLeft,
            int inkLimit,
            int unit,
            int[] anchor) {
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
            if (anchor != null) {
                anchor[0] = note[0];
                if (anchor.length > 1) anchor[1] = stem;
            }
            for (int[] dot : parts) {
                int dw = dot[1] - dot[0] + 1, dh = dot[3] - dot[2] + 1;
                float gap = dot[0] - note[1], dy = Math.abs((dot[2] + dot[3]) * .5f - cy);
                // The light fringe of '=' can extend left of its dark-core bound.
                // It is clipped by this search window, unlike a complete printed dot.
                // A score stem clipped at a vertical edge is not a complete tempo dot.
                if (dot[1] < right
                        && dot[2] > top
                        && dot[3] < bottom
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
