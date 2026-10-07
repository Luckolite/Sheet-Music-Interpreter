// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Isolated dynamic words and long straight hairpins, outside the five-line staff. */
final class ScoreDynamicsDetector {
    private ScoreDynamicsDetector() {}

    static List<PlayingTechniqueDetector.Staff> alignStaffs(
            List<PlayingTechniqueDetector.Staff> staffs, List<ScoreNoteEvent> notes, int height) {
        List<PlayingTechniqueDetector.Staff> result = new ArrayList<>();
        if (staffs.isEmpty()) return result;
        // Staff geometry is immutable; reuse each note's distances without picking one tie owner.
        float[] centers = new float[staffs.size()], distances = new float[staffs.size()];
        List<Map<Integer, Integer>> allVotes = new ArrayList<>();
        int index = 0;
        for (var staff : staffs) {
            centers[index++] = (staff.top() + staff.bottom()) * .5f;
            allVotes.add(new HashMap<>());
        }
        for (var note : notes) {
            float y = note.pageY() * height, nearest = Float.POSITIVE_INFINITY;
            for (int s = 0; s < centers.length; s++) {
                float distance = Math.abs(y - centers[s]);
                distances[s] = distance;
                if (distance < nearest) nearest = distance;
            }
            index = 0;
            for (var staff : staffs) {
                float distance = distances[index];
                if (!(distance > staff.gap() * 5) && !(nearest < distance))
                    allVotes.get(index)
                            .merge(note.staffCount() * 16 + note.staffIndex(), 1, Integer::sum);
                index++;
            }
        }
        index = 0;
        for (var staff : staffs) {
            Map<Integer, Integer> votes = allVotes.get(index++);
            int lane = staff.count() * 16 + staff.index(), best = 0;
            for (var vote : votes.entrySet())
                if (vote.getValue() > best) {
                    best = vote.getValue();
                    lane = vote.getKey();
                }
            result.add(
                    new PlayingTechniqueDetector.Staff(
                            staff.top(), staff.bottom(), staff.gap(), lane % 16, lane / 16));
        }
        return result;
    }

    /** Isolated letter-sized raw components grouped into a word, without staff lines or beams. */
    static List<PlayingTechniqueDetector.Word> symbolBoxes(
            byte[] gray, List<PlayingTechniqueDetector.Staff> staffs, int width, int height) {
        boolean[] seen = new boolean[gray.length];
        int[] queue = new int[gray.length];
        List<int[]> boxes = new ArrayList<>();
        for (int p = 0; p < gray.length; p++) {
            if (seen[p] || (gray[p] & 255) >= 145) continue;
            int start = 0, n = 1;
            queue[0] = p;
            seen[p] = true;
            int left = p % width, right = left, top = p / width, bottom = top;
            while (start < n) {
                int at = queue[start++], x = at % width, y = at / width;
                left = Math.min(left, x);
                right = Math.max(right, x);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                        int next = ny * width + nx;
                        if (!seen[next] && (gray[next] & 255) < 145) {
                            seen[next] = true;
                            queue[n++] = next;
                        }
                    }
            }
            var staff = owner(staffs, top, bottom, 8f);
            if (staff == null) continue;
            float gap = staff.gap();
            // A nearby hairpin must not glue itself to mf/mp and make the whole
            // group too wide to be a word. Hairpins are detected independently.
            if (right - left >= gap * 4 && right - left > (bottom - top) * 4) continue;
            // Keep small adjacent letters too, so the p in "pizz." is not accepted as piano.
            if (n >= 3
                    && bottom - top >= gap * .2
                    && bottom - top <= gap * 3
                    && right - left >= gap * .12
                    && right - left <= gap * 8)
                boxes.add(new int[] {left, top, right + 1, bottom + 1, staffs.indexOf(staff)});
        }
        // A faded printed mark may have no pixels at the ordinary component threshold.
        // These are shape proposals only: the music-font matcher still proves its identity.
        for (var b : FaintDynamicGlyphComponents.find(gray, width, height, seen, queue)) {
            var staff = owner(staffs, b.top(), b.bottom() - 1, 8f);
            if (staff == null) continue;
            float gap = staff.gap();
            int w = b.right() - b.left(), h = b.bottom() - b.top();
            if (w - 1 >= gap * 4 && w - 1 > (h - 1) * 4) continue;
            if (h - 1 >= gap * .2 && h - 1 <= gap * 3 && w - 1 >= gap * .12 && w - 1 <= gap * 8)
                boxes.add(
                        new int[] {
                            b.left(), b.top(), b.right(), b.bottom(), staffs.indexOf(staff)
                        });
        }
        boxes.sort(Comparator.<int[]>comparingInt(b -> b[4]).thenComparingInt(b -> b[0]));
        List<int[]> joined = new ArrayList<>();
        for (int[] b : boxes) {
            int[] a = null;
            float gap = staffs.get(b[4]).gap();
            for (int i = joined.size() - 1; i >= 0; i--) {
                var candidate = joined.get(i);
                if (candidate[4] == b[4]
                        && b[0] - candidate[2] < gap * .5
                        && b[0] >= candidate[0]
                        && Math.max(candidate[1], b[1]) < Math.min(candidate[3], b[3])) {
                    a = candidate;
                    break;
                }
            }
            if (a != null) {
                a[1] = Math.min(a[1], b[1]);
                a[2] = Math.max(a[2], b[2]);
                a[3] = Math.max(a[3], b[3]);
            } else joined.add(b);
        }
        List<PlayingTechniqueDetector.Word> result = new ArrayList<>();
        for (int[] b : joined) {
            float gap = staffs.get(b[4]).gap();
            if (b[3] - b[1] >= gap * .55
                    && b[3] - b[1] <= gap * 3
                    && b[2] - b[0] >= gap * .3
                    && b[2] - b[0] <= gap * 8)
                result.add(
                        new PlayingTechniqueDetector.Word(
                                "",
                                b[0] / (float) width,
                                b[1] / (float) height,
                                b[2] / (float) width,
                                b[3] / (float) height));
        }
        return result;
    }

    /** Coordinate-free, complete letter bodies for supplemental bounded dynamic OCR.
     * Original OCR and glyph decisions stay authoritative; these boxes carry no level. */
    static List<PlayingTechniqueDetector.Word> paperSymbolBoxes(
            byte[] gray,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            int width,
            int height) {
        if (gray == null || gray.length != (long) width * height || staffs.isEmpty())
            return List.of();
        byte[] normalized = RestPaperTone.normalize(gray, width, height, staffs.get(0).gap());
        if (normalized == gray) return List.of();
        List<PlayingTechniqueDetector.Word> result = new ArrayList<>();
        for (var measure : measures)
            for (var staff : staffs) {
                float gap = staff.gap(), center = (staff.top() + staff.bottom()) * .5f / height;
                if (center < measure.top() - gap / height
                        || center > measure.bottom() + gap / height) continue;
                int left = Math.max(0, (int) Math.floor(measure.left() * width - gap * .75f));
                int right = Math.min(width, (int) Math.ceil(measure.right() * width + gap * .75f));
                if (right <= left) continue;
                var local =
                        PrintedDirectionStaff.local(
                                staff, measures, notes, gray, width, height, (left + right) * .5f);
                if (local == null) continue;
                int top = Math.max(0, (int) Math.floor(local.bottom() - gap * .5f));
                int bottom = Math.min(height, (int) Math.ceil(local.bottom() + gap * 8));
                if (bottom <= top) continue;
                int w = right - left, h = bottom - top;
                byte[] crop = new byte[w * h];
                for (int y = 0; y < h; y++)
                    System.arraycopy(normalized, (top + y) * width + left, crop, y * w, w);
                var localCrop =
                        new PlayingTechniqueDetector.Staff(
                                local.top() - top,
                                local.bottom() - top,
                                local.gap(),
                                local.index(),
                                local.count());
                for (var box : symbolBoxes(crop, List.of(localCrop), w, h)) {
                    float x0 = left + box.left() * w, x1 = left + box.right() * w;
                    float y0 = top + box.top() * h, y1 = top + box.bottom() * h;
                    if (x0 <= left + 1 || x1 >= right - 1 || y0 <= top + 1 || y1 >= bottom - 1)
                        continue;
                    boolean head = false;
                    for (var note : notes) {
                        if (note.measureIndex() < 0
                                || note.measureIndex() >= measures.size()
                                || note.staffIndex() != staff.index()
                                || note.staffCount() != staff.count()) continue;
                        var region = measures.get(note.measureIndex());
                        float nx =
                                (region.left()
                                                + note.positionInMeasure()
                                                        * (region.right() - region.left()))
                                        * width;
                        float ny = note.pageY() * height;
                        if (nx >= x0 - gap * .65f
                                && nx <= x1 + gap * .65f
                                && ny >= y0 - gap * .65f
                                && ny <= y1 + gap * .65f) {
                            head = true;
                            break;
                        }
                    }
                    if (head) continue;
                    var proposal =
                            new PlayingTechniqueDetector.Word(
                                    "", x0 / width, y0 / height, x1 / width, y1 / height);
                    boolean duplicate = false;
                    for (var old : result)
                        if (Math.abs(old.left() - proposal.left()) * width < gap * .5f
                                && Math.abs(old.top() - proposal.top()) * height < gap * .5f
                                && Math.abs(old.right() - proposal.right()) * width < gap * .5f
                                && Math.abs(old.bottom() - proposal.bottom()) * height < gap * .5f)
                            duplicate = true;
                    if (!duplicate) result.add(proposal);
                }
            }
        return List.copyOf(result);
    }

    static float level(String text) {
        if (text == null) return Float.NaN;
        String token = text.trim().toLowerCase(Locale.ROOT).replaceAll("[.,:;]$", "");
        // OCR can merge the sudden-dynamic qualifier with its level. Only accept
        // a complete qualified level, so ordinary words and lyrics stay excluded.
        token = token.replaceFirst("^(?:subito|sub)\\.?\\s*(?=(?:ppp|pp|p|mp|mf|fff|ff|f)$)", "");
        return switch (token) {
            case "ppp" -> -18;
            case "pp" -> -12;
            case "p" -> -8;
            case "mp" -> -4;
            case "m" -> -2;
            case "mf" -> 0;
            case "f" -> 3;
            case "ff" -> 6;
            case "fff" -> 9;
            default -> Float.NaN;
        };
    }

    static boolean dynamicLine(String text) {
        return text != null
                && text.trim()
                        .toLowerCase(Locale.ROOT)
                        .matches(
                                "(?:(?:subito|sub|sempre|poco|a|più|piu|cantabile|sostenuto|marcato|dolce|tranquillo|espressivo|crescendo|diminuendo|cresc|dim|decresc|ppp|pp|p|mp|m|mf|fff|ff|f)[.,:;]?\\s*)+");
    }

    static List<String> packedLevels(String text) {
        if (text == null || Float.isFinite(level(text))) return List.of();
        var matcher =
                java.util.regex.Pattern.compile(
                                "^(mf|mp|fff|ff|f|ppp|pp|p)(mf|mp|fff|ff|f|ppp|pp|p)$")
                        .matcher(text.trim().toLowerCase(Locale.ROOT).replaceAll("[.,:;]$", ""));
        return matcher.matches() ? List.of(matcher.group(1), matcher.group(2)) : List.of();
    }

    static List<String> joinedDirection(String text) {
        if (text == null) return List.of();
        var matcher =
                java.util.regex.Pattern.compile(
                                "^(ppp|pp|p|mf|mp|fff|ff|f)(crescendo|cresc|diminuendo|dim|decresc)[.,:;]?$")
                        .matcher(text.trim().toLowerCase(Locale.ROOT));
        return matcher.matches() ? List.of(matcher.group(1), matcher.group(2)) : List.of();
    }

    static int textDirection(String text) {
        if (text == null) return 0;
        return switch (text.toLowerCase(Locale.ROOT).replaceAll("[.,:;]$", "")) {
            case "cresc", "crescendo" -> 1;
            case "dim", "diminuendo", "decresc" -> -1;
            default -> 0;
        };
    }

    /** Keep identical token splitting and raw-ink checks in desktop and Android OCR. */
    static List<PlayingTechniqueDetector.Word> ocrWords(
            String line, PlayingTechniqueDetector.Word word, byte[] gray, int width, int height) {
        if (!dynamicLine(line) && textDirection(word.text()) == 0) return List.of();
        var packed = packedLevels(word.text());
        if (packed.isEmpty()) packed = joinedDirection(word.text());
        if (!packed.isEmpty()) {
            float split =
                    word.left()
                            + (word.right() - word.left())
                                    * packed.get(0).length()
                                    / (packed.get(0).length() + packed.get(1).length());
            float[] edges = {word.left(), split, word.right()};
            var result = new ArrayList<PlayingTechniqueDetector.Word>();
            for (int i = 0; i < 2; i++) {
                var part =
                        new PlayingTechniqueDetector.Word(
                                packed.get(i), edges[i], word.top(), edges[i + 1], word.bottom());
                if (containsInk(part, gray, width, height)) result.add(part);
            }
            return List.copyOf(result);
        }
        if (isSuddenLevel(line)
                && Float.isFinite(level(word.text()))
                && level(line) == level(word.text())) {
            word =
                    new PlayingTechniqueDetector.Word(
                            line.trim(), word.left(), word.top(), word.right(), word.bottom());
        }
        return (Float.isFinite(level(word.text())) || textDirection(word.text()) != 0)
                        && containsInk(word, gray, width, height)
                ? List.of(word)
                : List.of();
    }

    static boolean isSuddenLevel(String text) {
        return text != null
                && Float.isFinite(level(text))
                && text.trim()
                        .toLowerCase(Locale.ROOT)
                        .matches("(?:subito|sub)\\.?\\s*(?:ppp|pp|p|mp|mf|fff|ff|f)[.,:;]?");
    }

    static String glyphLevelText(String text) {
        return isSuddenLevel(text)
                ? text.trim()
                        .toLowerCase(Locale.ROOT)
                        .replaceFirst("^(?:subito|sub)\\.?\\s*", "")
                        .replaceAll("[.,:;]$", "")
                : text;
    }

    /** Glyph refinement changes bounds without erasing a verified sudden instruction. */
    static PlayingTechniqueDetector.Word recognizedWord(
            String glyph,
            PlayingTechniqueDetector.Word box,
            List<PlayingTechniqueDetector.Word> literalWords) {
        String text = glyph;
        for (var literal : literalWords) {
            if (isSuddenLevel(literal.text())
                    && level(literal.text()) == level(glyph)
                    && literal.left() < box.right()
                    && literal.right() > box.left()
                    && literal.top() < box.bottom()
                    && literal.bottom() > box.top()) {
                text = literal.text();
                break;
            }
        }
        return new PlayingTechniqueDetector.Word(
                text, box.left(), box.top(), box.right(), box.bottom());
    }

    static boolean containsInk(
            PlayingTechniqueDetector.Word word, byte[] gray, int width, int height) {
        int left = Math.max(0, Math.round(word.left() * width)),
                right = Math.min(width, Math.round(word.right() * width));
        int top = Math.max(0, Math.round(word.top() * height)),
                bottom = Math.min(height, Math.round(word.bottom() * height));
        if (right <= left || bottom <= top) return false;
        int ink = 0;
        for (int y = top; y < bottom; y++)
            for (int x = left; x < right; x++) if ((gray[y * width + x] & 255) < 145) ink++;
        return ink >= Math.max(4, (right - left) * (bottom - top) * .035);
    }

    static List<ScoreDynamicChange> detect(
            List<PlayingTechniqueDetector.Word> words,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            byte[] gray,
            int width,
            int height) {
        return detectWithEvidence(words, staffs, measures, notes, gray, width, height).changes();
    }

    record Detection(List<ScoreDynamicChange> changes, List<ScoreExpressiveEvent> events) {
        Detection {
            changes = List.copyOf(changes);
            events = List.copyOf(events);
        }
    }

    static Detection detectWithEvidence(
            List<PlayingTechniqueDetector.Word> words,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            byte[] gray,
            int width,
            int height) {
        return detectDirectionCore(words, staffs, measures, notes, gray, width, height, gray, true);
    }

    static Detection detectDirectionCore(
            List<PlayingTechniqueDetector.Word> words,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            byte[] gray,
            int width,
            int height,
            byte[] ownershipGray,
            boolean paperProposals) {
        List<ScoreDynamicChange> result = new ArrayList<>();
        Map<ScoreDynamicChange, PlayingTechniqueDetector.Word> openWords = new HashMap<>();
        float minimumGap = Float.MAX_VALUE, maximumGap = 0;
        for (var staff : staffs)
            if (Float.isFinite(staff.gap()) && staff.gap() > 0) {
                minimumGap = Math.min(minimumGap, staff.gap());
                maximumGap = Math.max(maximumGap, staff.gap());
            }
        var shared = GrandStaffDynamics.bracedPairs(staffs, measures, gray, width, height);
        var directionFloors = new DirectionFloors(staffs, notes, measures, height);
        for (var word : words) {
            float db = level(word.text());
            if (!Float.isFinite(db)) continue;
            if (PrintedDynamicNoteOwnership.containsBeamedHead(word, measures, notes)) continue;
            var owner = directionFloors.owner(word.top() * height, word.bottom() * height);
            if (owner == null)
                owner =
                        PrintedDirectionStaff.at(
                                staffs,
                                measures,
                                notes,
                                ownershipGray,
                                width,
                                height,
                                (word.left() + word.right()) * width * .5f,
                                word.top() * height,
                                word.bottom() * height);
            if (owner == null
                    || word.bottom() - word.top() > owner.gap() * 3 / height
                            && !ShadedTallDynamicWord.proved(
                                    word, ownershipGray, width, height, owner.gap())) continue;
            var common =
                    GrandStaffDynamics.directionPart(
                            shared, owner, word.top() * height, word.bottom() * height);
            var target = common == null ? owner : common;
            float anchor = literalAnchor(word, target, measures, notes, width, height);
            add(
                    result,
                    target,
                    anchor,
                    anchor,
                    db,
                    0,
                    measures,
                    notes,
                    width,
                    height,
                    common != null);
        }
        if (gray != null && gray.length == width * height) {
            boolean[] seen = new boolean[gray.length];
            int[] queue = new int[gray.length];
            var strokes = new ArrayList<HairpinContinuation.Stroke>();
            var wedges = new ArrayList<HairpinContinuation.Wedge>();
            var fragments = new ArrayList<ShadedHairpinFragments.Rail>();
            for (int p = 0; p < gray.length; p++) {
                if (seen[p] || (gray[p] & 255) >= 145) continue;
                int start = 0, n = 1;
                queue[0] = p;
                seen[p] = true;
                int left = p % width, right = left, top = p / width, bottom = top;
                while (start < n) {
                    int at = queue[start++], x = at % width, y = at / width;
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                    for (int dy = -2; dy <= 2; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                            int next = ny * width + nx;
                            if (!seen[next] && (gray[next] & 255) < 145) {
                                seen[next] = true;
                                queue[n++] = next;
                            }
                        }
                }
                var owner = directionFloors.owner(top, bottom);
                if (owner == null
                        && right - left + 1 >= minimumGap * 3
                        && bottom - top + 1 <= maximumGap * 3
                        && right - left + 1 >= (bottom - top + 1) * 2.5f)
                    owner =
                            PrintedDirectionStaff.at(
                                    staffs,
                                    measures,
                                    notes,
                                    ownershipGray,
                                    width,
                                    height,
                                    (left + right) * .5f,
                                    top,
                                    bottom);
                if (owner == null) owner = HairpinContinuation.distantOwner(staffs, top, bottom);
                if (owner == null) continue;
                float gap = owner.gap();
                int w = right - left + 1, h = bottom - top + 1;
                // Compact hairpins beside a printed level can have a steeper opening.
                // Isolated >/< still need the ordinary long, shallow shape.
                boolean qualifiedShort =
                        w >= gap * 3
                                && w <= gap * 6
                                && h <= gap * 1.75f
                                && w >= h * 2.5f
                                && precedingPrintedLevel(
                                        words, gray, width, height, left, top, bottom, gap);
                int[] upper = new int[w], lower = new int[w];
                Arrays.fill(upper, Integer.MAX_VALUE);
                Arrays.fill(lower, Integer.MIN_VALUE);
                for (int i = 0; i < n; i++) {
                    int x = queue[i] % width - left, y = queue[i] / width;
                    upper[x] = Math.min(upper[x], y);
                    lower[x] = Math.max(lower[x], y);
                }
                if (gray != ownershipGray
                        && w >= gap * 2
                        && h <= gap * 3
                        && n <= w * Math.max(8, gap))
                    fragments.addAll(
                            ShadedHairpinFragments.pieces(owner, left, right, upper, lower));
                if (!qualifiedShort && (w < gap * 4 || w < h * 4)
                        || h > gap * 3
                        || n > w * Math.max(8, gap)) continue;

                if (qualifiedShort && !openHairpinInterior(gray, width, left, upper, lower, gap))
                    continue;
                var stroke = HairpinContinuation.stroke(owner, left, right, upper, lower);
                if (stroke != null) strokes.add(stroke);
                if (h < gap * .4) continue;
                int direction = hairpinDirection(upper, lower, gap);
                if (direction == 0) {
                    var faint =
                            FaintHairpinArms.recover(
                                    gray, width, height, queue[0], left, right, top, bottom, upper,
                                    lower, gap);
                    if (faint != null) {
                        var recoveredOwner = directionFloors.owner(faint.top(), faint.bottom());
                        if (recoveredOwner == null)
                            recoveredOwner =
                                    PrintedDirectionStaff.at(
                                            staffs,
                                            measures,
                                            notes,
                                            ownershipGray,
                                            width,
                                            height,
                                            (left + right) * .5f,
                                            faint.top(),
                                            faint.bottom());
                        if (recoveredOwner == null)
                            recoveredOwner =
                                    HairpinContinuation.distantOwner(
                                            staffs, faint.top(), faint.bottom());
                        if (owner.equals(recoveredOwner)) {
                            top = faint.top();
                            bottom = faint.bottom();
                            upper = faint.upper();
                            lower = faint.lower();
                            direction = faint.direction();
                        }
                    }
                }
                var common = GrandStaffDynamics.directionPart(shared, owner, top, bottom);
                if (direction != 0) {
                    add(
                            result,
                            common == null ? owner : common,
                            left / (float) width,
                            right / (float) width,
                            0,
                            direction,
                            measures,
                            notes,
                            width,
                            height,
                            common != null);
                    if (common == null) {
                        int a = Math.min(w - 1, Math.max(0, w / 20)),
                                b = Math.max(0, w - 1 - w / 20);
                        if (upper[a] != Integer.MAX_VALUE && upper[b] != Integer.MAX_VALUE)
                            wedges.add(
                                    new HairpinContinuation.Wedge(
                                            owner,
                                            left,
                                            right,
                                            lower[a] - upper[a],
                                            lower[b] - upper[b],
                                            direction));
                    }
                }
            }
            for (var shape :
                    ShadedHairpinFragments.recover(fragments, ownershipGray, width, height))
                add(
                        result,
                        shape.staff(),
                        shape.left() / (float) width,
                        shape.right() / (float) width,
                        0,
                        shape.direction(),
                        measures,
                        notes,
                        width,
                        height,
                        false);
            for (var link :
                    HairpinContinuation.links(wedges, strokes, staffs, measures, width, height)) {
                var from =
                        slot(
                                link.from().left() / (float) width,
                                link.from().staff(),
                                measures,
                                notes,
                                width,
                                height);
                var end =
                        slot(
                                link.right() / (float) width,
                                link.staff(),
                                measures,
                                notes,
                                width,
                                height);
                if (from == null || end == null) continue;
                var originalEnd =
                        slot(
                                link.from().right() / (float) width,
                                link.from().staff(),
                                measures,
                                notes,
                                width,
                                height);
                boolean interrupted = false;
                for (var level : result)
                    if (level.direction() == 0
                            && sameDynamicPart(level, link.staff(), false)
                            && originalEnd != null
                            && (level.measureIndex() > originalEnd.measure
                                    || level.measureIndex() == originalEnd.measure
                                            && level.positionInMeasure() > originalEnd.position)
                            && (level.measureIndex() < end.measure
                                    || level.measureIndex() == end.measure
                                            && level.positionInMeasure() < end.position))
                        interrupted = true;
                if (interrupted) continue;
                // A nearby explicit level on this row supplies the arrival anchor.
                for (var level : result)
                    if (level.direction() == 0
                            && sameDynamicPart(level, link.staff(), false)
                            && level.measureIndex() >= end.measure
                            && level.measureIndex() <= end.measure + 1) {
                        var region = measures.get(level.measureIndex());
                        float y = (link.staff().top() + link.staff().bottom()) * .5f / height;
                        float x =
                                (region.left()
                                                + level.positionInMeasure()
                                                        * (region.right() - region.left()))
                                        * width;
                        if (y >= region.top()
                                && y <= region.bottom()
                                && x >= link.right()
                                && x - link.right() <= link.staff().gap() * 4)
                            end = new Slot(level.measureIndex(), level.positionInMeasure());
                    }
                for (int i = 0; i < result.size(); i++) {
                    var old = result.get(i);
                    if (old.direction() == link.from().direction()
                            && old.measureIndex() == from.measure
                            && Math.abs(old.positionInMeasure() - from.position) < .001
                            && sameDynamicPart(old, link.from().staff(), false))
                        result.set(
                                i,
                                new ScoreDynamicChange(
                                        old.measureIndex(),
                                        old.positionInMeasure(),
                                        old.staffIndex(),
                                        old.staffCount(),
                                        end.measure,
                                        end.position,
                                        old.decibels(),
                                        old.direction(),
                                        old.sharedStaffs(),
                                        old.fixedTarget(),
                                        old.sharedTiming()));
                }
            }
        }
        // Written cresc./dim. continues across systems until the next printed level (or
        // the page boundary). Do not invent geometry-sized note durations for the ramp.
        if (paperProposals)
            for (var word : words) {
                int direction = textDirection(word.text());
                if (direction == 0) continue;
                var owner = directionFloors.owner(word.top() * height, word.bottom() * height);
                if (owner == null) continue;
                var common =
                        GrandStaffDynamics.directionPart(
                                shared, owner, word.top() * height, word.bottom() * height);
                if (common != null) owner = common;
                Slot a = slot(word.left(), owner, measures, notes, width, height);
                if (a == null) continue;
                // A direction printed beside an absolute level begins with that level.
                for (var c : result)
                    if (c.direction() == 0
                            && c.measureIndex() == a.measure
                            && c.staffIndex() == owner.index()
                            && c.positionInMeasure() <= a.position
                            && a.position - c.positionInMeasure()
                                    < owner.gap()
                                            * 5
                                            / (width
                                                    * (measures.get(a.measure).right()
                                                            - measures.get(a.measure).left())))
                        a = new Slot(a.measure, c.positionInMeasure());
                Slot end = new Slot(measures.size() - 1, 1);
                for (var c : result)
                    if (c.direction() == 0
                            && sameDynamicPart(c, owner, common != null)
                            && (c.measureIndex() > a.measure
                                    || c.measureIndex() == a.measure
                                            && c.positionInMeasure() > a.position + .025f)
                            && (c.measureIndex() < end.measure
                                    || c.measureIndex() == end.measure
                                            && c.positionInMeasure() < end.position))
                        end = new Slot(c.measureIndex(), c.positionInMeasure());
                boolean duplicate = false;
                for (var c : result)
                    if (c.direction() == direction
                            && c.measureIndex() == a.measure
                            && c.staffIndex() == owner.index()
                            && Math.abs(c.positionInMeasure() - a.position) < .025f)
                        duplicate = true;
                if (!duplicate && (end.measure > a.measure || end.position > a.position)) {
                    var change =
                            new ScoreDynamicChange(
                                    a.measure,
                                    a.position,
                                    owner.index(),
                                    owner.count(),
                                    end.measure,
                                    end.position,
                                    0,
                                    direction,
                                    common != null);
                    result.add(change);
                    if (end.measure == measures.size() - 1 && end.position == 1)
                        openWords.put(change, word);
                }
            }
        // A keyboard brace inside an ensemble shares only its two staves, not the
        // soloist or every other part. Materialize those lanes in the existing wire format.
        List<ScoreDynamicChange> scoped = new ArrayList<>();
        List<ScoreExpressiveEvent> events = new ArrayList<>();
        for (var c : result) {
            int first = scoped.size();
            if (c.sharedStaffs() && c.staffCount() > 2) {
                for (int staff = c.staffIndex(); staff <= c.staffIndex() + 1; staff++)
                    scoped.add(
                            new ScoreDynamicChange(
                                    c.measureIndex(),
                                    c.positionInMeasure(),
                                    staff,
                                    c.staffCount(),
                                    c.endMeasureIndex(),
                                    c.endPosition(),
                                    c.decibels(),
                                    c.direction(),
                                    false,
                                    c.fixedTarget(),
                                    false));
            } else scoped.add(c);
            var word = openWords.get(c);
            if (word != null)
                for (int i = first; i < scoped.size(); i++)
                    events.add(
                            ScoreDynamicContinuation.evidence(
                                    scoped.get(i), word.text(), word.left()));
        }
        if (paperProposals && gray != null && !staffs.isEmpty()) {
            for (byte[] normalized :
                    new byte[][] {
                        RestPaperTone.normalize(gray, width, height, staffs.get(0).gap()),
                        RestPaperTone.normalizeDirectionInk(
                                gray, width, height, staffs.get(0).gap()),
                        RestPaperTone.normalizeSoftDirectionInk(
                                gray, width, height, staffs.get(0).gap())
                    }) {
                if (normalized != gray) {
                    var proposed =
                            detectDirectionCore(
                                    words,
                                    staffs,
                                    measures,
                                    notes,
                                    normalized,
                                    width,
                                    height,
                                    gray,
                                    false);
                    for (var change : proposed.changes()) {
                        if (change.direction() == 0) continue;
                        boolean claimed = false;
                        for (var old : scoped)
                            if (old.direction() == change.direction()
                                    && old.measureIndex() == change.measureIndex()
                                    && old.staffIndex() == change.staffIndex()
                                    && old.staffCount() == change.staffCount()
                                    && Math.abs(
                                                    old.positionInMeasure()
                                                            - change.positionInMeasure())
                                            < .06f) claimed = true;
                        if (!claimed) scoped.add(change);
                    }
                }
            }
        }
        scoped.sort(
                Comparator.comparingInt(ScoreDynamicChange::measureIndex)
                        .thenComparingDouble(ScoreDynamicChange::positionInMeasure)
                        .thenComparingInt(c -> c.direction() == 0 ? 0 : 1));
        return new Detection(scoped, events);
    }

    private static boolean openHairpinInterior(
            byte[] gray, int width, int left, int[] upper, int[] lower, float gap) {
        int eligible = 0, open = 0;
        for (int x = 0; x < upper.length; x++) {
            if (upper[x] == Integer.MAX_VALUE || lower[x] - upper[x] < gap * .5f) continue;
            eligible++;
            if ((gray[((upper[x] + lower[x]) / 2) * width + left + x] & 255) >= 145) open++;
        }
        return eligible >= upper.length / 4 && open >= eligible * .8f;
    }

    private static boolean precedingPrintedLevel(
            List<PlayingTechniqueDetector.Word> words,
            byte[] gray,
            int width,
            int height,
            int left,
            int top,
            int bottom,
            float gap) {
        for (var word : words) {
            if (!Float.isFinite(level(word.text()))) continue;
            // A literal OCR box can include the following hairpin. Require ink
            // before the wedge, independently of ink inside the wedge itself.
            float prefixRight = Math.min(word.right(), (left - gap * .25f) / width);
            float separation = left - prefixRight * width;
            float centerDistance =
                    Math.abs((word.top() + word.bottom()) * height * .5f - (top + bottom) * .5f);
            if (word.left() * width < left
                    && left - word.left() * width <= gap * 6
                    && separation <= gap * 3
                    && centerDistance <= gap * .9f
                    && containsInk(
                            new PlayingTechniqueDetector.Word(
                                    word.text(),
                                    word.left(),
                                    word.top(),
                                    prefixRight,
                                    word.bottom()),
                            gray,
                            width,
                            height)) return true;
        }
        return false;
    }

    private static boolean sameDynamicPart(
            ScoreDynamicChange change, PlayingTechniqueDetector.Staff owner, boolean pair) {
        if (change.staffCount() != owner.count()) return false;
        int changeLast = change.staffIndex() + (change.sharedStaffs() ? 1 : 0),
                ownerLast = owner.index() + (pair ? 1 : 0);
        return change.staffIndex() <= ownerLast && owner.index() <= changeLast;
    }

    static int hairpinDirection(int[] upper, int[] lower, float gap) {
        int w = upper.length;
        double[] u = new double[8], l = new double[8];
        for (int b = 0; b < 8; b++) {
            int count = 0;
            for (int x = b * w / 8; x < (b + 1) * w / 8; x++)
                if (upper[x] != Integer.MAX_VALUE) {
                    u[b] += upper[x];
                    l[b] += lower[x];
                    count++;
                }
            if (count < Math.max(1, w / 8 * .8)) return 0;
            u[b] /= count;
            l[b] /= count;
        }
        double opening = (l[7] - u[7]) - (l[0] - u[0]);
        if (Math.abs(opening) < gap * .4) return 0;
        if ((u[7] - u[0]) * (l[7] - l[0]) >= 0) return 0;
        for (int b = 0; b < 8; b++)
            if (Math.abs(u[b] - (u[0] + (u[7] - u[0]) * b / 7)) > Math.max(1.4, gap * .14)
                    || Math.abs(l[b] - (l[0] + (l[7] - l[0]) * b / 7)) > Math.max(1.4, gap * .14))
                return 0;
        return opening > 0 ? 1 : -1;
    }

    private static PlayingTechniqueDetector.Staff owner(
            List<PlayingTechniqueDetector.Staff> staffs, float top, float bottom) {
        return owner(staffs, top, bottom, 4.5f);
    }

    private static PlayingTechniqueDetector.Staff owner(
            List<PlayingTechniqueDetector.Staff> staffs, float top, float bottom, float limit) {
        PlayingTechniqueDetector.Staff best = null;
        float score = Float.MAX_VALUE;
        for (var staff : staffs) {
            float distance;
            if (top >= Math.round(staff.bottom() + staff.gap() * .25f))
                distance = (top - staff.bottom()) / staff.gap();
            else if (bottom <= Math.round(staff.top() - staff.gap() * .25f))
                distance = (staff.top() - bottom) / staff.gap() + .75f;
            else continue;
            if (distance <= limit && distance < score) {
                score = distance;
                best = staff;
            }
        }
        return best;
    }

    /** Ledger-heavy parts place directions beyond the nominal five-line staff. */
    static PlayingTechniqueDetector.Staff directionOwner(
            List<PlayingTechniqueDetector.Staff> staffs,
            float top,
            float bottom,
            List<ScoreNoteEvent> notes,
            List<MeasureRegion> measures,
            int height) {
        var best = owner(staffs, top, bottom);
        float score = Float.MAX_VALUE;
        for (var staff : staffs) {
            float center = (staff.top() + staff.bottom()) * .5f / height, floor = staff.bottom();
            for (var note : notes) {
                if (note.staffIndex() != staff.index()
                        || note.staffCount() != staff.count()
                        || note.measureIndex() < 0
                        || note.measureIndex() >= measures.size()) continue;
                var measure = measures.get(note.measureIndex());
                if (center < measure.top() || center > measure.bottom()) continue;
                floor = Math.max(floor, note.pageY() * height + staff.gap() * 1.5f);
            }
            if (floor <= staff.bottom() + staff.gap() * 2) floor = staff.bottom();
            float distance;
            if (top >= Math.round(staff.bottom() + staff.gap() * .25f)) {
                distance = Math.max(0, (top - floor) / staff.gap());
            } else if (bottom <= Math.round(staff.top() - staff.gap() * .25f))
                distance = (staff.top() - bottom) / staff.gap() + .75f;
            else continue;
            if (distance <= 4.5f && distance < score) {
                score = distance;
                best = staff;
            }
        }
        return best;
    }

    /** Call-local floors: detection reads these immutable records without modifying its lists. */
    private static final class DirectionFloors {
        private final List<PlayingTechniqueDetector.Staff> staffs;
        private final List<ScoreNoteEvent> notes;
        private final List<MeasureRegion> measures;
        private final int height;
        private PlayingTechniqueDetector.Staff[] keys;
        private float[] floors;

        DirectionFloors(
                List<PlayingTechniqueDetector.Staff> staffs,
                List<ScoreNoteEvent> notes,
                List<MeasureRegion> measures,
                int height) {
            this.staffs = staffs;
            this.notes = notes;
            this.measures = measures;
            this.height = height;
        }

        PlayingTechniqueDetector.Staff owner(float top, float bottom) {
            var best = ScoreDynamicsDetector.owner(staffs, top, bottom);
            float score = Float.MAX_VALUE;
            int position = 0;
            for (var staff : staffs) {
                float floor = floor(position++, staff);
                float distance;
                if (top >= Math.round(staff.bottom() + staff.gap() * .25f)) {
                    distance = Math.max(0, (top - floor) / staff.gap());
                } else if (bottom <= Math.round(staff.top() - staff.gap() * .25f))
                    distance = (staff.top() - bottom) / staff.gap() + .75f;
                else continue;
                if (distance <= 4.5f && distance < score) {
                    score = distance;
                    best = staff;
                }
            }
            return best;
        }

        private float floor(int position, PlayingTechniqueDetector.Staff staff) {
            if (keys != null && position < keys.length && keys[position] == staff)
                return floors[position];
            float center = (staff.top() + staff.bottom()) * .5f / height, floor = staff.bottom();
            for (var note : notes) {
                if (note.staffIndex() != staff.index()
                        || note.staffCount() != staff.count()
                        || note.measureIndex() < 0
                        || note.measureIndex() >= measures.size()) continue;
                var measure = measures.get(note.measureIndex());
                if (center < measure.top() || center > measure.bottom()) continue;
                floor = Math.max(floor, note.pageY() * height + staff.gap() * 1.5f);
            }
            if (floor <= staff.bottom() + staff.gap() * 2) floor = staff.bottom();
            if (keys == null) {
                keys = new PlayingTechniqueDetector.Staff[staffs.size()];
                floors = new float[keys.length];
            }
            // Position and object identity keep equal staff records in separate original slots.
            if (position < keys.length) {
                floors[position] = floor;
                keys[position] = staff;
            }
            return floor;
        }
    }

    // Italic dynamic marks can overhang the preceding bar while their body belongs to the next.
    private static float literalAnchor(
            PlayingTechniqueDetector.Word word,
            PlayingTechniqueDetector.Staff staff,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            int width,
            int height) {
        float anchor = printedLiteralAnchor(word, staff, measures, notes, width, height);
        if (!isSuddenLevel(word.text()))
            return narrowLiteralAttack(word, staff, measures, notes, width, height, anchor);
        Slot target = slot(anchor, staff, measures, notes, width, height);
        if (target == null) return anchor;
        var region = measures.get(target.measure);
        float bestDistance = staff.gap() * 3, best = anchor;
        boolean ambiguous = false;
        for (var note : notes) {
            if (note.measureIndex() != target.measure
                    || note.staffIndex() != staff.index()
                    || note.staffCount() != staff.count()) continue;
            float x = region.left() + note.positionInMeasure() * (region.right() - region.left());
            float distance = Math.abs(x - anchor) * width;
            if (distance < bestDistance - .1f) {
                bestDistance = distance;
                best = x;
                ambiguous = false;
            } else if (Math.abs(distance - bestDistance) < .1f
                    && Math.abs(x - best) * width > .1f) {
                ambiguous = true;
            }
        }
        return ambiguous ? anchor : best;
    }

    // A short engraved dynamic can be centered under a note while its left serif
    // lies just beyond the ordinary one-gap tolerance. Require one unambiguous
    // note column through the glyph body, and retain the established bar owner.
    static float narrowLiteralAttack(
            PlayingTechniqueDetector.Word word,
            PlayingTechniqueDetector.Staff staff,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            int width,
            int height,
            float anchor) {
        if (width <= 0
                || !Float.isFinite(word.left())
                || !Float.isFinite(word.right())
                || !Float.isFinite(staff.gap())
                || staff.gap() <= 0
                || word.right() <= word.left()
                || (word.right() - word.left()) * width > staff.gap() * 4) return anchor;
        var target = slot(anchor, staff, measures, notes, width, height);
        var middle =
                slot((word.left() + word.right()) * .5f, staff, measures, notes, width, height);
        if (target == null || middle == null || target.measure != middle.measure) return anchor;
        var region = measures.get(target.measure);
        float center = (word.left() + word.right()) * .5f;
        float best = anchor, bestDistance = staff.gap() * .6f;
        boolean found = false, ambiguous = false;
        for (var note : notes) {
            if (note.measureIndex() != target.measure
                    || note.staffIndex() != staff.index()
                    || note.staffCount() != staff.count()
                    || !Float.isFinite(note.positionInMeasure())) continue;
            float x = region.left() + note.positionInMeasure() * (region.right() - region.left());
            if (x < word.left() || x > word.right()) continue;
            float distance = Math.abs(x - center) * width;
            if (!found && distance < bestDistance) {
                best = x;
                bestDistance = distance;
                found = true;
            } else if (found && distance < staff.gap() * .6f) {
                if (Math.abs(x - best) * width > staff.gap() * .1f) ambiguous = true;
                else if (distance < bestDistance) {
                    best = x;
                    bestDistance = distance;
                }
            }
        }
        return found && !ambiguous ? best : anchor;
    }

    private static float printedLiteralAnchor(
            PlayingTechniqueDetector.Word word,
            PlayingTechniqueDetector.Staff staff,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            int width,
            int height) {
        float center = (word.left() + word.right()) * .5f, gap = staff.gap() / width;
        if (word.right() - word.left() > gap * 4) return word.left();
        Slot left = slot(word.left(), staff, measures, notes, width, height);
        Slot middle = slot(center, staff, measures, notes, width, height);
        if (left == null && middle != null) {
            var first = measures.get(middle.measure);
            // A leading italic mark can end beside the opening bar while its serif is
            // outside the left-anchor tolerance. Its body must still reach that bar.
            if (word.right() <= first.left() && first.left() - word.right() <= gap) return center;
        }
        if (left == null || middle == null || middle.measure != left.measure + 1)
            return word.left();
        var next = measures.get(middle.measure);
        if (word.left() < next.left()
                && next.left() - word.left() <= gap * 1.5f
                && center >= next.left()
                && center - next.left() <= gap * 1.5f) return center;
        return word.left();
    }

    private record Slot(int measure, float position) {}

    private static Slot slot(
            float x,
            PlayingTechniqueDetector.Staff staff,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            int width,
            int height) {
        int found = -1;
        float distance = Float.MAX_VALUE, y = (staff.top() + staff.bottom()) * .5f / height;
        for (int m = 0; m < measures.size(); m++) {
            var r = measures.get(m);
            if (y < r.top() - staff.gap() / height || y > r.bottom() + staff.gap() / height)
                continue;
            float d = Math.max(r.left() - x, Math.max(0, x - r.right()));
            if (d < distance) {
                distance = d;
                found = m;
            }
        }
        if (found < 0 || distance > staff.gap() * 3 / width) return null;
        var r = measures.get(found);
        float position = Math.max(0, Math.min(1, (x - r.left()) / (r.right() - r.left())));
        float nearest = staff.gap() / width / (r.right() - r.left()), printed = position;
        for (var note : notes)
            if (note.measureIndex() == found && note.staffIndex() == staff.index()) {
                float d = Math.abs(note.positionInMeasure() - printed);
                if (d < nearest) {
                    nearest = d;
                    position = note.positionInMeasure();
                }
            }
        return new Slot(found, position);
    }

    private static void add(
            List<ScoreDynamicChange> result,
            PlayingTechniqueDetector.Staff owner,
            float left,
            float right,
            float db,
            int direction,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            int width,
            int height,
            boolean shared) {
        Slot a = slot(left, owner, measures, notes, width, height),
                b = slot(right, owner, measures, notes, width, height);
        if (a == null
                || b == null
                || b.measure < a.measure
                || b.measure == a.measure && b.position < a.position) return;
        if (direction != 0 && a.measure == b.measure && b.position - a.position < .04) return;
        var change =
                new ScoreDynamicChange(
                        a.measure,
                        a.position,
                        owner.index(),
                        owner.count(),
                        b.measure,
                        b.position,
                        db,
                        direction,
                        shared);
        for (var old : result)
            if (old.staffIndex() == change.staffIndex()
                    && old.staffCount() == change.staffCount()
                    && old.measureIndex() == change.measureIndex()
                    && Math.abs(old.positionInMeasure() - change.positionInMeasure()) < .025
                    && old.direction() == direction
                    && old.decibels() == db) return;
        result.add(change);
    }
}
