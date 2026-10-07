// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Applies octave directions to sounding pitch while preserving written staff geometry. */
final class OctaveMarkDetector {
    private static final Pattern TEXT_SEPARATORS = Pattern.compile("[\\s()\\[\\].,:;_\\-–—]");
    private static final Pattern TERMINAL_HOOKS = Pattern.compile("[┘┐」]+$");
    private static final Pattern MEASURE_CONTINUATION =
            Pattern.compile(
                    "^\\s*\\d{1,4}\\s+(\\((?:8v[ab]|15m[ab])\\)[.\\s_\\-–—]*)$",
                    Pattern.CASE_INSENSITIVE);

    private static final class StaffSides {
        private static final boolean[] VALUES = {false, true};
    }

    private static final class DashSkewOffsets {
        private static final int[] VALUES = {-1, 1};
    }

    private OctaveMarkDetector() {}

    static int shift(String text) {
        if (text == null) return 0;
        String s = TEXT_SEPARATORS.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
        // OCR can append the printed octave-line end hook to its direction.
        s = TERMINAL_HOOKS.matcher(s).replaceAll("");
        return switch (s) {
            case "8va", "8vaa", "8vaalta", "ottava" -> 1;
            case "8vb", "8vab", "8vabassa" -> -1;
            case "15ma", "15maa", "15maalta" -> 2;
            case "15mb", "15mab", "15mabassa" -> -2;
            default -> 0;
        };
    }

    private record Span(PlayingTechniqueDetector.Staff staff, float left, float right, int shift) {}

    static List<ScoreNoteEvent> apply(
            List<PlayingTechniqueDetector.Word> words,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            byte[] gray,
            int width,
            int height) {
        if (staffs == null || staffs.isEmpty() || notes.isEmpty()) return notes;
        gray = contrastedInk(gray, width, height);
        List<PlayingTechniqueDetector.Word> combined =
                new ArrayList<>(words == null ? List.of() : words);
        combined.addAll(printedWords(gray, width, height, staffs, true));
        if (combined.isEmpty()) return notes;
        List<Span> spans = new ArrayList<>();
        for (var sourceWord : combined) {
            var word = directionAfterMeasureNumber(sourceWord);
            int shift = shift(word.text());
            if (shift == 0) continue;
            var owner = owner(word, staffs, shift, height);
            if (owner == null) continue;
            float gap = owner.gap(), left = word.left() * width - gap * .65f;
            float textRight =
                    Math.min(
                            word.right() * width,
                            word.left() * width
                                    + Math.max(
                                            gap * 2, (word.bottom() - word.top()) * height * 3.5f));
            float right =
                    dashEnd(
                            gray,
                            width,
                            height,
                            textRight,
                            word.top() * height,
                            word.bottom() * height,
                            gap);
            if (right < 0) {
                // An isolated octave direction applies to the nearest attack or chord only.
                float first = Float.POSITIVE_INFINITY;
                for (var note : notes)
                    if (note.kind() == ScoreNoteEvent.Kind.PITCHED
                            && onStaff(note, owner, staffs, height)) {
                        float x = x(note, measures, width);
                        if (x >= left && x <= textRight + gap * 1.5f) first = Math.min(first, x);
                    }
                if (!Float.isFinite(first)) continue;
                left = first - gap * .35f;
                right = first + gap * .35f;
            }
            spans.add(new Span(owner, left, right, shift));
        }
        if (spans.isEmpty()) return notes;
        List<Span> ambiguous = new ArrayList<>();
        for (var a : spans)
            for (var b : spans)
                if (a != b
                        && a.staff.equals(b.staff)
                        && a.shift != b.shift
                        && Math.abs(a.left - b.left) < a.staff.gap() * .75f) {
                    ambiguous.add(a);
                    ambiguous.add(b);
                }
        spans.removeAll(ambiguous);
        spans.sort(Comparator.comparingDouble(Span::left));
        for (int i = 0; i < spans.size(); i++) {
            var a = spans.get(i);
            float right = a.right;
            for (int j = i + 1; j < spans.size(); j++) {
                var b = spans.get(j);
                if (a.staff.equals(b.staff) && b.left > a.left + a.staff.gap() * .75f)
                    right = Math.min(right, b.left - .01f);
            }
            if (right != a.right) spans.set(i, new Span(a.staff, a.left, right, a.shift));
        }
        List<ScoreNoteEvent> result = new ArrayList<>(notes.size());
        for (var note : notes) {

            if (note.kind() != ScoreNoteEvent.Kind.PITCHED) {
                result.add(note);
                continue;
            }
            float x = x(note, measures, width);
            Span selected = null;
            for (var span : spans)
                if (onStaff(note, span.staff, staffs, height)
                        && x >= span.left
                        && x <= span.right) {
                    if (selected == null || span.left > selected.left) selected = span;
                }
            result.add(selected == null ? note : note.withOctaveShift(selected.shift));
        }
        return List.copyOf(result);
    }

    /** Remove dark paper texture from octave text and its dash evidence together. */
    private static byte[] contrastedInk(byte[] gray, int width, int height) {
        if (gray == null || gray.length != (long) width * height || width <= 0 || height <= 0)
            return gray;
        byte[] result = gray.clone();
        final int tile = 64;
        int[] histogram = new int[256];
        for (int top = 0; top < height; top += tile)
            for (int left = 0; left < width; left += tile) {
                java.util.Arrays.fill(histogram, 0);
                int bottom = Math.min(height, top + tile), right = Math.min(width, left + tile);
                for (int y = top; y < bottom; y++)
                    for (int x = left; x < right; x++) histogram[gray[y * width + x] & 255]++;
                int target = ((bottom - top) * (right - left) * 85 + 99) / 100,
                        total = 0,
                        background = 255;
                for (int value = 0; value < 256; value++) {
                    total += histogram[value];
                    if (total >= target) {
                        background = value;
                        break;
                    }
                }
                // Ordinary high-contrast scans retain their exact pixels. On darker
                // paper, a stroke must lie below its local paper by the same margin
                // used for the numeral counters, suffixes and dotted-line chain.
                int shift = Math.max(0, 165 - (background - 24));
                if (shift == 0) continue;
                for (int y = top; y < bottom; y++)
                    for (int x = left; x < right; x++)
                        result[y * width + x] =
                                (byte) Math.min(255, (gray[y * width + x] & 255) + shift);
            }
        return result;
    }

    private record InkBox(int left, int top, int right, int bottom, int area) {}

    /** Confirmed printed direction text cannot also be a sounding notehead. */
    static boolean containsPrintedMark(
            List<PlayingTechniqueDetector.Word> printedMarks,
            float centerX,
            float centerY,
            int width,
            int height) {
        for (var word : printedMarks)
            if (shift(word.text()) != 0
                    && centerX >= word.left() * width
                    && centerX < word.right() * width
                    && centerY >= word.top() * height
                    && centerY < word.bottom() * height) return true;
        return false;
    }

    /** OCR may join a system number to its parenthesized continuation mark.
     * Keep the direction's own horizontal anchor, not the preceding bar number. */
    private static PlayingTechniqueDetector.Word directionAfterMeasureNumber(
            PlayingTechniqueDetector.Word word) {
        if (word.text() == null) return word;
        var match = MEASURE_CONTINUATION.matcher(word.text());
        if (!match.matches()) return word;
        float left =
                word.left() + (word.right() - word.left()) * match.start(1) / word.text().length();
        return new PlayingTechniqueDetector.Word(
                match.group(1), left, word.top(), word.right(), word.bottom());
    }

    static List<PlayingTechniqueDetector.Word> printedWords(
            byte[] gray, int width, int height, List<PlayingTechniqueDetector.Staff> staffs) {
        return printedWords(gray, width, height, staffs, false);
    }

    private static List<PlayingTechniqueDetector.Word> printedWords(
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            boolean alreadyContrasted) {
        List<PlayingTechniqueDetector.Word> words = new ArrayList<>();
        if (gray == null || gray.length != width * height) return words;
        if (!alreadyContrasted) gray = contrastedInk(gray, width, height);
        for (var staff : staffs)
            for (boolean below : StaffSides.VALUES) {
                float gap = staff.gap();
                int top =
                        Math.max(
                                0,
                                Math.round(
                                        below
                                                ? staff.bottom() + gap * .3f
                                                : staff.top() - gap * 12));
                int bottom =
                        Math.min(
                                height - 1,
                                Math.round(
                                        below
                                                ? staff.bottom() + gap * 12
                                                : staff.top() - gap * .3f));
                if (top >= bottom) continue;
                int h = bottom - top + 1;
                boolean[] visited = new boolean[width * h];
                int[] stack = new int[width * h];
                List<InkBox> boxes = new ArrayList<>();
                for (int origin = 0; origin < visited.length; origin++) {
                    if (visited[origin] || (gray[top * width + origin] & 255) >= 165) continue;
                    int count = 0, size = 0, left = width, right = -1, y1 = h, y2 = -1;
                    stack[size++] = origin;
                    visited[origin] = true;
                    while (size > 0) {
                        int at = stack[--size], x = at % width, y = at / width;
                        count++;
                        left = Math.min(left, x);
                        right = Math.max(right, x);
                        y1 = Math.min(y1, y);
                        y2 = Math.max(y2, y);
                        for (int dy = -1; dy <= 1; dy++)
                            for (int dx = -1; dx <= 1; dx++) {
                                int xx = x + dx, yy = y + dy;
                                if (xx < 0 || xx >= width || yy < 0 || yy >= h) continue;
                                int next = yy * width + xx;
                                if (!visited[next] && (gray[top * width + next] & 255) < 165) {
                                    visited[next] = true;
                                    stack[size++] = next;
                                }
                            }
                    }
                    if (count >= Math.max(4, gap * gap * .025f)
                            && y2 - y1 + 1 >= gap * .55f
                            && y2 - y1 + 1 <= gap * 3.2f
                            && right - left + 1 >= gap * .12f
                            && right - left + 1 <= gap * 8)
                        boxes.add(new InkBox(left, top + y1, right, top + y2, count));
                }
                boxes.sort(Comparator.comparingInt(InkBox::left));
                boolean[] used = new boolean[boxes.size()];
                var bareBoxes = new ArrayList<>(boxes);
                bareBoxes.addAll(attachedEightBoxes(gray, width, height, top, bottom, gap));
                for (var box : bareBoxes) {
                    if (centeredInStaff(box, staffs)) continue;
                    int bw = box.right - box.left + 1, bh = box.bottom - box.top + 1;
                    if (bw < gap * .3f
                            || bw > gap * 1.6f
                            || bh < gap * .65f
                            || bh > gap * 2.3f
                            || bh < bw * .9f
                            || OctaveClefDigit.holes(gray, width, box.left, box.top, bw, bh) != 2)
                        continue;
                    float dashRight =
                            dashEnd(
                                    gray,
                                    width,
                                    height,
                                    box.right + 1,
                                    box.top,
                                    box.bottom,
                                    gap,
                                    6);
                    if (dashRight < 0) continue;
                    int hookDirection =
                            hookDirection(gray, width, height, dashRight, box.top, box.bottom, gap);
                    // A bare numeral has no va/vb suffix. Use only the nearest stave;
                    // do not apply the same inter-system mark to both adjacent rows.
                    PlayingTechniqueDetector.Staff nearest = null;
                    float nearestDistance = Float.POSITIVE_INFINITY;
                    boolean nearestBelow = false;
                    for (var candidate : staffs) {
                        float above = (candidate.top() - box.bottom) / candidate.gap();
                        float under = (box.top - candidate.bottom()) / candidate.gap();
                        // Between systems a lower-octave line can be closer to the next treble
                        // stave. Its terminal hook points back toward the actual owning stave.
                        if (hookDirection < 0 && under <= 0 || hookDirection > 0 && above <= 0)
                            continue;
                        float distance =
                                hookDirection < 0
                                        ? under
                                        : hookDirection > 0 ? above : above > 0 ? above : under;
                        if (distance >= .25f && distance <= 12 && distance < nearestDistance) {
                            nearest = candidate;
                            nearestDistance = distance;
                            nearestBelow = under > 0;
                        }
                    }
                    if (!staff.equals(nearest) || below != nearestBelow) continue;
                    // An attached fragment needs either a suffix or a proved upward end hook
                    // before it can establish a lower octave for the preceding staff.
                    if (below && !boxes.contains(box) && hookDirection >= 0) continue;
                    words.add(
                            new PlayingTechniqueDetector.Word(
                                    below ? "8vb" : "8va",
                                    box.left / (float) width,
                                    box.top / (float) height,
                                    (box.right + 1) / (float) width,
                                    (box.bottom + 1) / (float) height));
                }
                for (int i = 0; i < boxes.size(); i++) {
                    if (used[i]) continue;
                    var box = boxes.get(i);
                    int left = box.left, right = box.right, a = box.top, b = box.bottom;
                    used[i] = true;
                    var members = new ArrayList<InkBox>();
                    members.add(box);
                    for (int j = i + 1; j < boxes.size(); j++) {
                        var next = boxes.get(j);
                        if (next.left > right + gap * .7f) break;
                        if (used[j]
                                || next.right < left
                                || Math.min(b, next.bottom) - Math.max(a, next.top) < gap * .3f
                                || Math.max(right, next.right) - left > gap * 8) continue;
                        used[j] = true;
                        right = Math.max(right, next.right);
                        a = Math.min(a, next.top);
                        b = Math.max(b, next.bottom);
                        members.add(next);
                    }
                    int shift = OctaveWordShapes.match(gray, width, left, a, right, b);
                    if (shift == 0 && members.size() >= 4) {
                        InkBox first = members.get(0), last = members.get(members.size() - 1);
                        if (parenthesis(gray, width, first, gap, true)
                                && parenthesis(gray, width, last, gap, false)
                                && Math.abs(first.top - last.top) <= gap * .3f
                                && Math.abs(first.bottom - last.bottom) <= gap * .3f) {
                            int innerLeft = width,
                                    innerRight = -1,
                                    innerTop = height,
                                    innerBottom = -1;
                            for (int j = 1; j < members.size() - 1; j++) {
                                var inner = members.get(j);
                                innerLeft = Math.min(innerLeft, inner.left);
                                innerRight = Math.max(innerRight, inner.right);
                                innerTop = Math.min(innerTop, inner.top);
                                innerBottom = Math.max(innerBottom, inner.bottom);
                            }
                            int innerShift =
                                    OctaveWordShapes.match(
                                            gray,
                                            width,
                                            innerLeft,
                                            innerTop,
                                            innerRight,
                                            innerBottom);
                            if (innerShift != 0
                                    && dashEnd(
                                                    gray,
                                                    width,
                                                    height,
                                                    last.right + 1,
                                                    innerTop,
                                                    innerBottom,
                                                    gap)
                                            >= 0) {
                                shift = innerShift;
                                left = innerLeft;
                                right = innerRight;
                                a = innerTop;
                                b = innerBottom;
                            }
                        }
                    }
                    if (shift == 0 || (below ? shift > 0 : shift < 0)) continue;
                    String text =
                            switch (shift) {
                                case 1 -> "8va";
                                case -1 -> "8vb";
                                case 2 -> "15ma";
                                default -> "15mb";
                            };
                    words.add(
                            new PlayingTechniqueDetector.Word(
                                    text,
                                    left / (float) width,
                                    a / (float) height,
                                    (right + 1) / (float) width,
                                    (b + 1) / (float) height));
                }
            }
        // A proved suffix determines the direction. Do not also interpret its
        // numeral as a bare lower-octave mark for the preceding staff.
        var completeWords = List.copyOf(words);
        words.removeIf(
                word ->
                        completeWords.stream()
                                .anyMatch(
                                        other ->
                                                other != word
                                                        && other.left() * width
                                                                <= word.left() * width + 1
                                                        && other.top() * height
                                                                <= word.top() * height + 1
                                                        && other.right() * width
                                                                > word.right() * width + 2
                                                        && other.bottom() * height
                                                                >= word.bottom() * height - 1));
        return words;
    }

    // Staff rules can divide an accidental into two apparent counters. A bare
    // octave numeral belongs outside the staff, while complete suffix words
    // retain their separate recognition and ownership evidence.
    private static boolean centeredInStaff(
            InkBox box, List<PlayingTechniqueDetector.Staff> staffs) {
        float center = (box.top + box.bottom) * .5f;
        for (var staff : staffs) if (center >= staff.top() && center <= staff.bottom()) return true;
        return false;
    }

    /** Balanced thin curved brackets may enclose a printed continuation word. */
    private static boolean parenthesis(
            byte[] gray, int width, InkBox box, float gap, boolean left) {
        int w = box.right - box.left + 1, h = box.bottom - box.top + 1;
        if (w < gap * .25f
                || w > gap * .9f
                || h < gap * 1.3f
                || h > gap * 3.2f
                || box.area > h * gap * .4f) return false;
        float[] centers = new float[3];
        int[] counts = new int[3];
        for (int y = box.top; y <= box.bottom; y++)
            for (int x = box.left; x <= box.right; x++)
                if ((gray[y * width + x] & 255) < 165) {
                    int band = Math.min(2, (y - box.top) * 3 / h);
                    centers[band] += x;
                    counts[band]++;
                }
        for (int band = 0; band < 3; band++) {
            if (counts[band] == 0) return false;
            centers[band] /= counts[band];
        }
        float sign = left ? 1 : -1;
        // Italic brackets lean across their height; compare curvature after removing that slope.
        return ((centers[0] + centers[2]) * .5f - centers[1]) * sign + .5f > gap * .06f
                && Math.abs(centers[0] - centers[2]) < gap * .65f;
    }

    private static List<InkBox> attachedEightBoxes(
            byte[] gray, int w, int h, int top, int bottom, float gap) {
        int band = bottom - top + 1;
        boolean[] seen = new boolean[w * band];
        int[] queue = new int[w * band];
        var holes = new ArrayList<InkBox>();
        int[] neighbors = {-1, 1, -w, w};
        for (int seed = 0; seed < seen.length; seed++) {
            if (seen[seed] || (gray[top * w + seed] & 255) < 165) continue;
            int take = 0, size = 1, pixels = 0, x0 = w, x1 = -1, y0 = band, y1 = -1;
            boolean edge = false;
            seen[seed] = true;
            queue[0] = seed;
            while (take < size) {
                int at = queue[take++], x = at % w, y = at / w;
                if (w > 2) {
                    int left = x, right = x, row = y * w;
                    while (left > 0
                            && !seen[row + left - 1]
                            && (gray[top * w + row + left - 1] & 255) >= 165) left--;
                    while (right + 1 < w
                            && !seen[row + right + 1]
                            && (gray[top * w + row + right + 1] & 255) >= 165) right++;
                    java.util.Arrays.fill(seen, row + left, row + right + 1, true);
                    pixels += right - left + 1;
                    x0 = Math.min(x0, left);
                    x1 = Math.max(x1, right);
                    y0 = Math.min(y0, y);
                    y1 = Math.max(y1, y);
                    if (left == 0 || right == w - 1 || y == 0 || y == band - 1) edge = true;
                    for (int direction = -1; direction <= 1; direction += 2) {
                        int nextY = y + direction;
                        if (nextY < 0 || nextY >= band) continue;
                        int nextRow = nextY * w;
                        for (int nextX = left; nextX <= right; nextX++) {
                            int next = nextRow + nextX;
                            if (seen[next] || (gray[top * w + next] & 255) < 165) continue;
                            seen[next] = true;
                            queue[size++] = next;
                            while (nextX < right
                                    && !seen[nextRow + nextX + 1]
                                    && (gray[top * w + nextRow + nextX + 1] & 255) >= 165) nextX++;
                        }
                    }
                } else {
                    // Preserve the original neighbor convention for one- and two-column inputs.
                    pixels++;
                    x0 = Math.min(x0, x);
                    x1 = Math.max(x1, x);
                    y0 = Math.min(y0, y);
                    y1 = Math.max(y1, y);
                    if (x == 0 || x == w - 1 || y == 0 || y == band - 1) edge = true;
                    for (int d : neighbors) {
                        int next = at + d;
                        if (next < 0 || next >= seen.length || Math.abs(next % w - x) > 1) continue;
                        if (!seen[next] && (gray[top * w + next] & 255) >= 165) {
                            seen[next] = true;
                            queue[size++] = next;
                        }
                    }
                }
            }
            if (!edge
                    && pixels >= gap * gap * .015f
                    && pixels <= gap * gap * .65f
                    && x1 - x0 < gap
                    && y1 - y0 < gap) holes.add(new InkBox(x0, top + y0, x1, top + y1, pixels));
        }
        var result = new ArrayList<InkBox>();
        int pad = Math.max(2, Math.round(gap * .2f));
        for (var a : holes)
            for (var b : holes) {
                if (a.bottom >= b.top
                        || b.top - a.bottom > gap * .5f
                        || Math.abs(a.left + a.right - b.left - b.right) > gap * 1.1f) continue;
                int left = Math.max(0, Math.min(a.left, b.left) - pad),
                        right = Math.min(w - 1, Math.max(a.right, b.right) + pad);
                int y0 = Math.max(top, a.top - pad), y1 = Math.min(bottom, b.bottom + pad);
                if (y1 - y0 < gap * .65f || y1 - y0 > gap * 2.3f || right - left > gap * 1.6f)
                    continue;
                if (ledgerTouchesLeft(gray, w, h, left, right, y0, y1, gap))
                    result.add(new InkBox(left, y0, right, y1, a.area + b.area));
            }
        return result;
    }

    /** Recover a numeral merged with a thin ledger, not a second reading of every
     * isolated numeral or a pair of counters inside neighboring text. */
    private static boolean ledgerTouchesLeft(
            byte[] gray, int w, int h, int left, int right, int top, int bottom, float gap) {
        int reach = Math.max(3, Math.round(gap * .8f));
        if (left < reach + 1) return false;
        for (int y = Math.round(top + (bottom - top) * .3f);
                y <= Math.round(top + (bottom - top) * .7f);
                y++) {
            int ink = 0;
            for (int x = left - reach; x <= left + 2; x++) if ((gray[y * w + x] & 255) < 165) ink++;
            if (ink < (reach + 3) * .85f) continue;
            int x = left - reach / 2, a = y, b = y;
            while (a > 0 && (gray[(a - 1) * w + x] & 255) < 165) a--;
            while (b + 1 < h && (gray[(b + 1) * w + x] & 255) < 165) b++;
            if (b - a + 1 <= Math.max(3, Math.round(gap * .35f))) return true;
        }
        return false;
    }

    private static float x(ScoreNoteEvent note, List<MeasureRegion> measures, int width) {
        var m = measures.get(note.measureIndex());
        return (m.left() + note.positionInMeasure() * (m.right() - m.left())) * width;
    }

    private static boolean onStaff(
            ScoreNoteEvent note,
            PlayingTechniqueDetector.Staff staff,
            List<PlayingTechniqueDetector.Staff> staffs,
            int height) {
        PlayingTechniqueDetector.Staff nearest = null;
        float best = Float.POSITIVE_INFINITY;
        for (var candidate : staffs)
            if (note.staffIndex() == candidate.index() && note.staffCount() == candidate.count()) {
                float y = note.pageY() * height,
                        distance =
                                Math.max(0, Math.max(candidate.top() - y, y - candidate.bottom()))
                                        / candidate.gap();
                if (distance <= 7 && distance < best) {
                    best = distance;
                    nearest = candidate;
                }
            }
        return staff.equals(nearest);
    }

    private static PlayingTechniqueDetector.Staff owner(
            PlayingTechniqueDetector.Word word,
            List<PlayingTechniqueDetector.Staff> staffs,
            int shift,
            int height) {
        PlayingTechniqueDetector.Staff best = null;
        float distance = Float.POSITIVE_INFINITY;
        for (var staff : staffs) {
            float d =
                    shift > 0
                            ? (staff.top() - word.bottom() * height) / staff.gap()
                            : (word.top() * height - staff.bottom()) / staff.gap();
            if (d < .25f || d > 12 || d >= distance) continue;
            distance = d;
            best = staff;
        }
        return best;
    }

    /** Find a horizontal chain of short printed dashes, stopping at its actual end. */
    private static float dashEnd(
            byte[] gray, int width, int height, float start, float top, float bottom, float gap) {
        return dashEnd(gray, width, height, start, top, bottom, gap, 3);
    }

    private static float dashEnd(
            byte[] gray,
            int width,
            int height,
            float start,
            float top,
            float bottom,
            float gap,
            int minimum) {
        if (gray == null || gray.length != width * height) return -1;
        int left = Math.max(0, Math.round(start - gap * .35f));
        int y1 = Math.max(0, Math.round(top - gap * .15f)),
                y2 = Math.min(height - 1, Math.round(bottom + gap * .4f));
        int best = -1;
        for (int y = y1; y <= y2; y++) {
            int first = -1,
                    last = -1,
                    count = 0,
                    shortDots = 0,
                    interruptions = 0,
                    x = left,
                    scanY = y;
            boolean skippedSuffix = false;
            while (x < width) {
                int blank = 0;
                while (x < width && (gray[scanY * width + x] & 255) >= 165) {
                    // Once a real dashed chain is established, follow a one-pixel
                    // scan skew between dashes instead of dropping the octave mid-line.
                    boolean found = false;
                    if (count >= 3)
                        for (int offset : DashSkewOffsets.VALUES) {
                            int yy = scanY + offset;
                            if (yy >= 0
                                    && yy < height
                                    && Math.abs(yy - y) <= gap
                                    && x + 1 < width
                                    && (gray[yy * width + x] & 255) < 165
                                    && (gray[yy * width + x + 1] & 255) < 165) {
                                scanY = yy;
                                found = true;
                                break;
                            }
                        }
                    if (found) break;
                    blank++;
                    x++;
                }
                if (blank > gap * (count == 0 ? 2 : 1.6f)) break;
                int a = x;
                while (x < width && (gray[scanY * width + x] & 255) < 165) x++;
                int length = x - a;
                if (length < 2) continue;
                if (length > gap * 1.65f) break;
                boolean tall = false;
                for (int cx = a; cx < x; cx++) {
                    int ya = scanY, yb = scanY;
                    while (ya > 0 && (gray[(ya - 1) * width + cx] & 255) < 165) ya--;
                    while (yb + 1 < height && (gray[(yb + 1) * width + cx] & 255) < 165) yb++;
                    if (yb - ya + 1 > Math.max(3, gap * .35f)) {
                        tall = true;
                        break;
                    }
                }
                if (tall) {
                    if (count >= 3 && terminalHook(gray, width, height, a, x - 1, scanY, gap)) {
                        last = x - 1;
                        count++;
                        break;
                    }
                    // A superscript suffix may precede the dash chain of a proved eight.
                    // This fallback still requires six dashes and the two-counter numeral.
                    if (count == 0 && a < start) continue;
                    if (count == 0 && a < start + gap * 2) {
                        skippedSuffix = true;
                        continue;
                    }
                    if (count > 0 && ++interruptions <= 1) continue;
                    break;
                }
                if (length < gap * .18f) shortDots++;
                if (first < 0) first = a;
                last = x - 1;
                count++;
            }
            if (count >= Math.max(minimum, skippedSuffix ? 6 : shortDots > count / 2 ? 5 : 3)
                    && last - first >= gap * 3) best = Math.max(best, last);
        }
        return best < 0 ? -1 : best + gap * .55f;
    }

    private static boolean terminalHook(
            byte[] gray, int width, int height, int left, int right, int y, float gap) {
        if (right - left + 1 < gap * .3f) return false;
        boolean hook = false;
        for (int x = left; x <= right; x++) {
            int a = y, b = y;
            while (a > 0 && (gray[(a - 1) * width + x] & 255) < 165) a--;
            while (b + 1 < height && (gray[(b + 1) * width + x] & 255) < 165) b++;
            if (b - a + 1 > Math.max(3, gap * .35f)) {
                if (x < right - Math.max(2, gap * .2f) || b - a + 1 > gap * 1.3f) return false;
                hook = true;
            }
        }
        return hook;
    }

    /** The terminal vertical stroke points toward its staff: upward for 8vb, downward for 8va. */
    private static int hookDirection(
            byte[] gray,
            int width,
            int height,
            float dashRight,
            float top,
            float bottom,
            float gap) {
        int end = Math.round(dashRight - gap * .55f), direction = 0;
        int y1 = Math.max(0, Math.round(top - gap * .15f)),
                y2 = Math.min(height - 1, Math.round(bottom + gap * .4f));
        for (int x = Math.max(3, end - 1); x <= Math.min(width - 1, end + 1); x++)
            for (int y = y1; y <= y2; y++) {
                if ((gray[y * width + x] & 255) >= 165
                        || (gray[y * width + x - 2] & 255) >= 165
                        || (gray[y * width + x - 3] & 255) >= 165) continue;
                int a = y, b = y;
                while (a > 0 && (gray[(a - 1) * width + x] & 255) < 165) a--;
                while (b + 1 < height && (gray[(b + 1) * width + x] & 255) < 165) b++;
                int up = y - a, down = b - y;
                if (b - a + 1 > gap * 1.3f) continue;
                int candidate =
                        up >= Math.max(3, gap * .35f) && up > down * 2 + 1
                                ? -1
                                : down >= Math.max(3, gap * .35f) && down > up * 2 + 1 ? 1 : 0;
                if (candidate == 0) continue;
                if (direction != 0 && candidate != direction) return 0;
                direction = candidate;
            }
        return direction;
    }
}
