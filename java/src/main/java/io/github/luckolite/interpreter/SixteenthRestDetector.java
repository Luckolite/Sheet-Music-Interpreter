// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the two left-facing bulbs and descending diagonal tail from raw score ink. HOMR often
 * labels this entire glyph as background, so semantic symbol components cannot seed it.
 */
final class SixteenthRestDetector {
    record Staff(
            float top, float bottom, float gap, int index, int count, StaffPitchTrack pitchTrack) {
        Staff(float top, float bottom, float gap, int index, int count) {
            this(top, bottom, gap, index, count, null);
        }
    }

    record RestDot(float x, float y, ScoreRestEvent rest) {}

    record Detection(List<ScoreRestEvent> rests, List<RestDot> dots) {}

    record InkDot(float x, float y) {}

    private record Placement(Staff staff, float printedCenter) {}

    static List<ScoreRestEvent> detect(
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<Staff> staffs,
            List<ScoreNoteEvent> notes) {
        return detectWithDots(gray, width, height, measures, staffs, notes).rests();
    }

    static Detection detectWithDots(
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<Staff> staffs,
            List<ScoreNoteEvent> notes) {
        Detection baseline = detectWithDotsStandard(gray, width, height, measures, staffs, notes);
        Detection contact =
                BeamedHeadQuarterRests.detectWithDots(gray, width, height, measures, staffs, notes);
        List<ScoreRestEvent> candidates = new ArrayList<>(contact.rests());
        List<RestDot> candidateDots = new ArrayList<>(contact.dots());
        for (Staff staff : staffs)
            if (staff.pitchTrack() != null) {
                Detection curved =
                        detectOnPrintedStaff(
                                gray, width, height, measures, staff, notes, false, true);
                candidates.addAll(curved.rests());
                candidateDots.addAll(curved.dots());
            }
        List<ScoreRestEvent> combined = new ArrayList<>(baseline.rests());
        List<RestDot> dots = new ArrayList<>(baseline.dots());
        for (ScoreRestEvent rest : candidates) {
            boolean duplicate = false;
            for (ScoreRestEvent old : baseline.rests()) {
                if (old.measureIndex() == rest.measureIndex()
                        && old.staffIndex() == rest.staffIndex()
                        && old.staffCount() == rest.staffCount()
                        && Math.abs(old.positionInMeasure() - rest.positionInMeasure()) < .025f
                        && Math.abs(old.pageY() - rest.pageY())
                                < Math.max(.001f, Math.max(old.pageHeight(), rest.pageHeight()))) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                combined.add(rest);
                for (RestDot dot : candidateDots) if (dot.rest().equals(rest)) dots.add(dot);
            }
        }
        return collected(combined, dots);
    }

    private static Detection detectWithDotsStandard(
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<Staff> staffs,
            List<ScoreNoteEvent> notes) {
        Detection original = detectWithDots(gray, width, height, measures, staffs, notes, true);
        var joined = joinedEighthRests(gray, width, height, measures, staffs, notes);
        joined.addAll(independentUpQuarterRests(gray, width, height, measures, staffs, notes));
        joined.addAll(independentDownQuarterContacts(gray, width, height, measures, staffs, notes));
        if (!joined.isEmpty()) {
            var combined = new ArrayList<>(original.rests());
            combined.addAll(joined);
            original = collected(combined, original.dots());
        }
        byte[] contrasted = contrastedRestInk(gray, width, height, staffs);
        Detection additional =
                contrasted == gray
                        ? new Detection(List.of(), List.of())
                        : detectWithDots(contrasted, width, height, measures, staffs, notes, true);
        List<ScoreRestEvent> rests = new ArrayList<>(original.rests());
        rests.addAll(additional.rests());
        List<RestDot> dots = new ArrayList<>(original.dots());
        dots.addAll(additional.dots());
        Detection stable = collected(rests, dots);
        List<Float> gaps = new ArrayList<>();
        for (Staff staff : staffs) gaps.add(staff.gap());
        gaps.sort(Float::compare);
        byte[] paper =
                gaps.isEmpty()
                        ? gray
                        : RestPaperTone.normalize(gray, width, height, gaps.get(gaps.size() / 2));
        if (paper != gray) {
            Detection isolated =
                    detectWithDots(paper, width, height, measures, staffs, notes, true);
            List<ScoreRestEvent> complete = new ArrayList<>(isolated.rests());
            List<RestDot> completeDots = new ArrayList<>(isolated.dots());
            java.util.Set<ScoreRestEvent> localSeeds = new java.util.HashSet<>();
            for (var body : QuarterRestLocalBody.find(paper, gray, width, height, staffs)) {
                List<ScoreRestEvent> local = new ArrayList<>();
                List<RestDot> localDots = new ArrayList<>();
                inspect(
                        paper,
                        width,
                        height,
                        measures,
                        notes,
                        body.staff(),
                        body.top(),
                        body.bottom(),
                        new boolean[body.bottom() - body.top() + 1],
                        body.left(),
                        body.right(),
                        local,
                        localDots,
                        true,
                        false,
                        false,
                        false,
                        false,
                        true,
                        false,
                        (body.staff().top() + body.staff().bottom()) * .5f / height,
                        false);
                for (ScoreRestEvent rest : local)
                    if (seededOrdinaryRest(
                            gray, width, height, measures, List.of(body.staff()), rest)) {
                        complete.add(rest);
                        localSeeds.add(rest);
                        for (RestDot dot : localDots)
                            if (dot.rest().equals(rest)) completeDots.add(dot);
                    }
            }
            isolated = new Detection(complete, completeDots);
            rests = new ArrayList<>(stable.rests());
            dots = new ArrayList<>(stable.dots());
            for (ScoreRestEvent rest : isolated.rests()) {
                if (!seededOrdinaryRest(gray, width, height, measures, staffs, rest)
                        && !localSeeds.contains(rest)) continue;
                if (continuedRecoveredTail(paper, width, height, measures, staffs, rest)) continue;
                boolean duplicate = false;
                for (ScoreRestEvent old : rests)
                    if (old.measureIndex() == rest.measureIndex()
                            && old.staffIndex() == rest.staffIndex()
                            && old.staffCount() == rest.staffCount()
                            && Math.abs(old.positionInMeasure() - rest.positionInMeasure()) < .025f
                            && Math.abs(old.pageY() - rest.pageY())
                                    < Math.max(old.pageHeight(), rest.pageHeight())) {
                        duplicate = true;
                        break;
                    }
                if (duplicate) continue;
                rests.add(rest);
                for (RestDot dot : isolated.dots()) if (dot.rest().equals(rest)) dots.add(dot);
            }
            stable = collected(rests, dots);
        }
        for (float paperLevel : new float[] {220, 230, 250}) {
            byte[] ordinaryPaper =
                    gaps.isEmpty()
                            ? gray
                            : RestPaperTone.normalizeOrdinaryRestInk(
                                    gray, width, height, gaps.get(gaps.size() / 2), paperLevel);
            if (ordinaryPaper != gray) {
                Detection ordinary =
                        detectWithDots(ordinaryPaper, width, height, measures, staffs, notes, true);
                rests = new ArrayList<>(stable.rests());
                dots = new ArrayList<>(stable.dots());
                for (ScoreRestEvent rest : ordinary.rests()) {
                    // Only a complete body in its printed ordinary phase can add silence.
                    // Keep the detector's note/flag owners and reject a continuing shaft.
                    if (!ordinaryPrintedBody(width, height, measures, staffs, rest)
                            || !seededOrdinaryRest(
                                    ordinaryPaper, width, height, measures, staffs, rest)
                            || continuedRecoveredTail(
                                    ordinaryPaper, width, height, measures, staffs, rest)) continue;
                    rests.add(rest);
                    for (RestDot dot : ordinary.dots()) if (dot.rest().equals(rest)) dots.add(dot);
                }
                if (paperLevel == 220)
                    rests.addAll(
                            shadedHalfRests(
                                    ordinaryPaper, width, height, measures, staffs, notes, dots));
                stable = collected(rests, dots);
            }
        }
        byte[] faint = contrastedRestInk(gray, width, height, staffs, 205);
        if (faint == gray) return stable;
        Detection recovered =
                detectWithDots(faint, width, height, measures, staffs, notes, true, true);
        rests = new ArrayList<>(stable.rests());
        dots = new ArrayList<>(stable.dots());
        for (ScoreRestEvent rest : recovered.rests()) {
            if (!seededOrdinaryRest(gray, width, height, measures, staffs, rest)) continue;
            ScoreRestEvent duplicate = null;
            for (ScoreRestEvent old : rests)
                if (old.measureIndex() == rest.measureIndex()
                        && old.staffIndex() == rest.staffIndex()
                        && old.staffCount() == rest.staffCount()
                        && Math.abs(old.positionInMeasure() - rest.positionInMeasure()) < .025f
                        && Math.abs(old.pageY() - rest.pageY())
                                < Math.max(old.pageHeight(), rest.pageHeight())) {
                    duplicate = old;
                    break;
                }
            if (duplicate != null) {
                // A dark lower contour can resemble two rest bulbs before the pale
                // zigzag is recovered. Prefer the proved full quarter only when it
                // encloses that shorter reading; adjacent rests retain their identity.
                if (!completeRecoveredQuarter(rest, duplicate)) continue;
                rests.remove(duplicate);
                ScoreRestEvent replaced = duplicate;
                dots.removeIf(dot -> dot.rest().equals(replaced));
            }
            rests.add(rest);
            for (RestDot dot : recovered.dots()) if (dot.rest().equals(rest)) dots.add(dot);
        }
        return collected(rests, dots);
    }

    /**
     * A shaded sitting rectangle needs its whole body and five printed rules. Sample along the
     * existing track and keep the stricter note-column owner.
     */
    private static List<ScoreRestEvent> shadedHalfRests(
            byte[] paper,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<Staff> staffs,
            List<ScoreNoteEvent> notes,
            List<RestDot> restDots) {
        List<ScoreRestEvent> result = new ArrayList<>();
        for (Staff staff : staffs) {
            int start = -1;
            for (int x = 0; x <= width; x++) {
                float[] frame = x < width ? restFrame(staff, x) : new float[] {0, 0};
                float gap = frame[1], middle = frame[0] - 2 * gap;
                int dark = 0;
                if (x < width
                        && Float.isFinite(frame[0])
                        && Float.isFinite(gap)
                        && gap >= 4
                        && gap <= height * .25f)
                    for (int y = Math.round(middle - gap * .55f);
                            y <= Math.round(middle - gap * .30f);
                            y++)
                        if (y >= 0 && y < height && (paper[y * width + x] & 255) < 170) dark++;
                if (x < width && dark >= Math.max(3, Math.round(gap * .2f))) {
                    if (start < 0) start = x;
                    continue;
                }
                if (start < 0) continue;
                int left = start, right = x - 1;
                start = -1;
                float center = (left + right) * .5f;
                frame = restFrame(staff, center);
                gap = frame[1];
                int[] body = sittingRectangle(paper, width, height, staff, left, right, frame);
                if (body == null) continue;
                float centerX = center / width, printedCenter = (frame[0] - 2 * gap) / height;
                for (int m = 0; m < measures.size(); m++) {
                    MeasureRegion region = measures.get(m);
                    if (centerX <= region.left()
                            || centerX >= region.right()
                            || printedCenter <= region.top()
                            || printedCenter >= region.bottom()) continue;
                    if (m > 0 && region.equals(measures.get(m - 1))
                            || m + 1 < measures.size() && region.equals(measures.get(m + 1))) break;
                    boolean owned = false;
                    for (ScoreNoteEvent note : notes)
                        if (note.measureIndex() == m
                                && note.staffIndex() == staff.index()
                                && note.staffCount() == staff.count()) {
                            float noteX =
                                    (region.left()
                                                    + note.positionInMeasure()
                                                            * (region.right() - region.left()))
                                            * width;
                            if (noteX >= left - gap * .65f && noteX <= right + gap * .65f) {
                                owned = true;
                                break;
                            }
                        }
                    if (!owned) {
                        Staff local =
                                new Staff(
                                        frame[0] - 4 * gap,
                                        frame[0],
                                        gap,
                                        staff.index(),
                                        staff.count());
                        List<InkDot> augmentation =
                                augmentationDots(
                                        paper, width, height, local, right, region, notes, m);
                        ScoreRestEvent rest =
                                new ScoreRestEvent(
                                        m,
                                        (centerX - region.left())
                                                / (region.right() - region.left()),
                                        (body[0] + body[1]) * .5f / height,
                                        (body[1] - body[0] + 1f) / height,
                                        staff.index(),
                                        staff.count(),
                                        augmentation.size() == 2
                                                ? 3.5
                                                : augmentation.size() == 1 ? 3 : 2);
                        result.add(rest);
                        for (InkDot dot : augmentation)
                            restDots.add(new RestDot(dot.x(), dot.y(), rest));
                    }
                    break;
                }
            }
        }
        return result;
    }

    private static float[] restFrame(Staff staff, float x) {
        return staff.pitchTrack() == null
                ? new float[] {staff.bottom(), staff.gap()}
                : staff.pitchTrack().at(x);
    }

    private static int restPixel(
            byte[] gray, int width, int height, Staff staff, int x, int y, float bottom) {
        if (x < 0 || x >= width) return 255;
        int sourceY = Math.round(y + restFrame(staff, x)[0] - bottom);
        return sourceY < 0 || sourceY >= height ? 255 : gray[sourceY * width + x] & 255;
    }

    private static int restRowInk(
            byte[] gray,
            int width,
            int height,
            Staff staff,
            int left,
            int right,
            int y,
            float bottom) {
        int count = 0;
        for (int x = left; x <= right; x++)
            if (restPixel(gray, width, height, staff, x, y, bottom) < 170) count++;
        return count;
    }

    private static int[] sittingRectangle(
            byte[] gray, int width, int height, Staff staff, int left, int right, float[] frame) {
        float bottom = frame[0], gap = frame[1];
        int bodyWidth = right - left + 1;
        if (!Float.isFinite(bottom)
                || !Float.isFinite(gap)
                || gap < 4
                || gap > height * .25f
                || bodyWidth < gap * .7f
                || bodyWidth > gap * 1.6f) return null;
        int reach = Math.max(4, Math.round(gap * 2)), margin = Math.max(2, Math.round(gap * .25f));
        if (left - reach < 0 || right + reach >= width) return null;
        int contrast = Math.max(2, Math.round(gap * .32f)),
                search = Math.max(1, Math.round(gap * .15f));
        for (int line = 0; line < 5; line++) {
            int expected = Math.round(bottom - (4 - line) * gap);
            boolean supported = false;
            for (int y = expected - search; y <= expected + search && !supported; y++) {
                int a = 0, b = 0;
                for (int x = left - reach; x < left - margin; x++) {
                    int ink = restPixel(gray, width, height, staff, x, y, bottom);
                    if (ink <= 225
                            && restPixel(gray, width, height, staff, x, y - contrast, bottom)
                                    >= ink + 12
                            && restPixel(gray, width, height, staff, x, y + contrast, bottom)
                                    >= ink + 12) a++;
                }
                for (int x = right + margin + 1; x <= right + reach; x++) {
                    int ink = restPixel(gray, width, height, staff, x, y, bottom);
                    if (ink <= 225
                            && restPixel(gray, width, height, staff, x, y - contrast, bottom)
                                    >= ink + 12
                            && restPixel(gray, width, height, staff, x, y + contrast, bottom)
                                    >= ink + 12) b++;
                }
                supported = a >= (reach - margin) * .5f && b >= (reach - margin) * .5f;
            }
            if (!supported) return null;
        }
        int rule = Math.round(bottom - 2 * gap), first = rule - 1;
        while (first >= 0
                && rule - first <= gap * .7f
                && restRowInk(gray, width, height, staff, left, right, first, bottom)
                        >= bodyWidth * .8f) first--;
        first++;
        int rows = rule - first;
        if (rows < Math.max(3, Math.round(gap * .25f))
                || rows > gap * .65f
                || restRowInk(gray, width, height, staff, left, right, first - 1, bottom)
                        > bodyWidth * .8f
                || restRowInk(gray, width, height, staff, left, right, first - 2, bottom)
                        > bodyWidth * .25f) return null;
        int ruleEdge = Math.max(2, Math.round(gap * .2f));
        for (int y = first; y < rule - ruleEdge; y++)
            if (restRowInk(gray, width, height, staff, left - margin, left - 1, y, bottom) > 1
                    || restRowInk(gray, width, height, staff, right + 1, right + margin, y, bottom)
                            > 1) return null;
        int outside = 0;
        for (int y = Math.round(bottom - 4 * gap + gap * .15f);
                y <= Math.round(bottom + gap * .3f);
                y++) {
            if (y >= first - 1 && y < rule) continue;
            float nearest = bottom + Math.round((y - bottom) / gap) * gap;
            if (Math.abs(y - nearest) <= ruleEdge) continue;
            outside += restRowInk(gray, width, height, staff, left, right, y, bottom);
        }
        if (outside > Math.max(2, Math.round(gap * .2f))) return null;
        return new int[] {first, rule - 1};
    }

    private static boolean ordinaryPrintedBody(
            int width,
            int height,
            List<MeasureRegion> measures,
            List<Staff> staffs,
            ScoreRestEvent rest) {
        if (rest.durationBeats() != .5 && rest.durationBeats() != 1
                || rest.measureIndex() < 0
                || rest.measureIndex() >= measures.size()) return false;
        MeasureRegion region = measures.get(rest.measureIndex());
        float x =
                (region.left() + rest.positionInMeasure() * (region.right() - region.left()))
                        * width;
        float first = (rest.pageY() - rest.pageHeight() * .5f) * height;
        float last = (rest.pageY() + rest.pageHeight() * .5f) * height - 1;
        for (Staff staff : staffs) {
            if (staff.index() != rest.staffIndex() || staff.count() != rest.staffCount()) continue;
            float[] frame =
                    staff.pitchTrack() == null
                            ? new float[] {staff.bottom(), staff.gap()}
                            : staff.pitchTrack().at(x);
            float bottom = frame[0], gap = frame[1], top = bottom - 4 * gap;
            if (rest.durationBeats() == .5) {
                if (last >= bottom - gap * 1.5f
                        && last <= bottom - gap * .75f
                        && first >= top + gap * .85f - 1
                        && first <= top + gap * 1.55f + 1) return true;
            } else if (first >= top + gap * .2f - 1
                    && first <= top + gap * 1.1f + 1
                    && last + 1 >= Math.floor(bottom - gap * 1.2f)
                    && last <= bottom - gap * .1f + 1) return true;
        }
        return false;
    }

    private static boolean completeRecoveredQuarter(ScoreRestEvent full, ScoreRestEvent cropped) {
        if (full.durationBeats() != 1
                || (cropped.durationBeats() != .25 && cropped.durationBeats() != .5)
                || full.pageHeight() <= cropped.pageHeight() * 1.1f) return false;
        float fullTop = full.pageY() - full.pageHeight() * .5f;
        float fullBottom = full.pageY() + full.pageHeight() * .5f;
        float croppedTop = cropped.pageY() - cropped.pageHeight() * .5f;
        float croppedBottom = cropped.pageY() + cropped.pageHeight() * .5f;
        return fullTop <= croppedTop + .000001f && fullBottom >= croppedBottom - .000001f;
    }

    /**
     * Recover an occluded eighth-rest bulb only from its separate diagonal tail, a continuing
     * quarter shaft, and an owned printed rest-plus-two-note triplet.
     */

    /** A separately proved quarter shaft cannot own a complete eighth-rest body beside it. */
    private static List<ScoreRestEvent> independentUpQuarterRests(
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<Staff> staffs,
            List<ScoreNoteEvent> notes) {
        var found = new ArrayList<ScoreRestEvent>();
        if (gray == null || (long) width * height != gray.length || notes == null) return found;
        for (var owner : notes) {
            if (owner == null
                    || owner.kind() != ScoreNoteEvent.Kind.PITCHED
                    || owner.beamCount() != 0
                    || owner.unbeamedDurationBeats() != 1
                    || owner.augmentationDots() != 0
                    || owner.tupletDivisor() != 1
                    || owner.tupletNormalNotes() != 1
                    || owner.crossStaffBeam()
                    || owner.tiedFromPrevious()
                    || (owner.articulations() & NoteOrnament.GRACE) != 0
                    || owner.measureIndex() < 0
                    || owner.measureIndex() >= measures.size()
                    || !Float.isFinite(owner.positionInMeasure())
                    || owner.positionInMeasure() < 0
                    || owner.positionInMeasure() > 1
                    || !Float.isFinite(owner.pageY())) continue;
            var region = measures.get(owner.measureIndex());
            float
                    hx =
                            (region.left()
                                            + owner.positionInMeasure()
                                                    * (region.right() - region.left()))
                                    * width,
                    hy = owner.pageY() * height;
            if (!Float.isFinite(hx) || !Float.isFinite(hy)) continue;
            for (var staff : staffs) {
                if (staff.index() != owner.staffIndex() || staff.count() != owner.staffCount())
                    continue;
                float[] frame = restFrame(staff, hx);
                float gap = frame[1];
                if (!Float.isFinite(gap)
                        || gap < 4
                        || gap > height * .25f
                        || !Float.isFinite(frame[0])
                        || hy < frame[0] - 6 * gap
                        || hy > frame[0] + 2 * gap
                        || PrintedStemDirection.detect(gray, width, height, hx, hy, gap) != 1)
                    continue;
                int shaftTop = Math.round(hy - gap * 2.8f),
                        shaftBottom = Math.round(hy - gap * .45f);
                if (shaftTop < 0 || shaftBottom >= height) continue;
                int stemLeft = -1, stemRight = -1;
                for (int x = Math.max(0, Math.round(hx + gap * .4f));
                        x <= Math.min(width - 1, Math.round(hx + gap * .85f));
                        x++) {
                    int dark = 0;
                    for (int y = shaftTop; y <= shaftBottom; y++)
                        if ((gray[y * width + x] & 255) < 170) dark++;
                    if (dark >= (shaftBottom - shaftTop + 1) * .9f) {
                        if (stemLeft < 0) stemLeft = x;
                        stemRight = x;
                    }
                }
                if (stemLeft < 0 || stemRight - stemLeft + 1 > gap * .25f) continue;
                int left = Math.max(0, Math.round(hx - gap * 1.1f));
                int right = stemLeft - Math.max(2, Math.round(gap * .12f));
                int top = Math.max(0, Math.round(hy - gap * 2.65f));
                int bottom = Math.min(height - 1, Math.round(hy - gap * .35f));
                if (right <= left
                        || top >= bottom
                        || bottom > region.bottom() * height
                        || top < region.top() * height
                        || left < region.left() * width
                        || right > region.right() * width) continue;
                boolean[] line = new boolean[bottom - top + 1];
                float printedTop = frame[0] - 4 * gap;
                for (int y = top; y <= bottom; y++)
                    for (int j = 0; j < 5; j++)
                        if (Math.abs(y - (printedTop + j * gap)) <= Math.max(1, gap * .12f))
                            line[y - top] = true;

                // A bare quarter shaft needs no page-sized scratch plane.
                // Any accepted rounded bulb must first have visible width away from its head.
                boolean bulbSeed = false;
                for (int y = top; y <= Math.min(bottom, Math.round(hy - gap * .9f)); y++) {
                    if (line[y - top]) continue;
                    int dark = 0;
                    for (int x = left; x <= right; x++)
                        if ((gray[y * width + x] & 255) < 170) dark++;
                    if (dark >= Math.max(2, Math.round(gap * .4f))) {
                        bulbSeed = true;
                        break;
                    }
                }
                if (!bulbSeed) continue;
                // Remove only the physically continuous independent shaft from a local copy.
                // The known lower head is excluded from this local component plane;
                // the upper glyph must still pass every bulb/tail contour check;
                // the caller plane and all other owners survive.
                byte[] separate = gray.clone();
                for (int y = shaftTop; y <= shaftBottom; y++)
                    for (int x = stemLeft; x <= stemRight; x++)
                        separate[y * width + x] = (byte) 255;
                for (int y = Math.max(0, (int) Math.ceil(hy - gap * .7f));
                        y <= Math.min(height - 1, Math.round(hy + gap * .6f));
                        y++)
                    for (int x = Math.max(0, Math.round(hx - gap * .9f));
                            x <= Math.min(width - 1, Math.round(hx + gap * .9f));
                            x++) separate[y * width + x] = (byte) 255;

                var kept = new ArrayList<>(notes);
                kept.remove(owner);

                int inkLeft = right + 1,
                        inkRight = left - 1,
                        inkTop = bottom + 1,
                        inkBottom = top - 1;
                for (int y = top; y <= bottom; y++)
                    if (!line[y - top])
                        for (int x = left; x <= right; x++)
                            if ((separate[y * width + x] & 255) < 170) {
                                inkLeft = Math.min(inkLeft, x);
                                inkRight = Math.max(inkRight, x);
                                inkTop = Math.min(inkTop, y);
                                inkBottom = Math.max(inkBottom, y);
                            }
                if (inkLeft > inkRight
                        || inkTop > inkBottom
                        || inkTop <= top
                        || inkBottom >= bottom) continue;
                // Existing complete-bulb/tail checks can bridge verified ruled-row gaps.
                // Component flood fill need not join an occluding ruled row to a note owner.
                for (var body :
                        List.of(new SeparatedRestInk.Body(inkLeft, inkRight, inkTop, inkBottom))) {

                    if (body.bottom() - body.top() < gap * 1.3f
                            || body.bottom() - body.top() > gap * 2.2f
                            || body.right() - body.left() < gap * .7f
                            || body.right() - body.left() > gap * 1.6f) continue;
                    var local =
                            new Staff(
                                    body.top() - gap,
                                    body.top() + 3 * gap,
                                    gap,
                                    staff.index(),
                                    staff.count());
                    List<ScoreRestEvent> candidates = new ArrayList<>();
                    inspect(
                            separate,
                            width,
                            height,
                            measures,
                            kept,
                            local,
                            top,
                            bottom,
                            line,
                            body.left(),
                            body.right(),
                            candidates,
                            new ArrayList<>(),
                            true,
                            false,
                            false,
                            false,
                            true,
                            false,
                            false,
                            (frame[0] - 2 * gap) / height,
                            false);
                    for (var rest : candidates)
                        if (rest.durationBeats() == .5
                                && rest.measureIndex() == owner.measureIndex()
                                && rest.staffIndex() == owner.staffIndex()
                                && rest.staffCount() == owner.staffCount()) found.add(rest);
                }
            }
        }
        return found;
    }

    /** A separate downward quarter can occlude the lower foot of another voice's rest. */
    private static List<ScoreRestEvent> independentDownQuarterContacts(
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<Staff> staffs,
            List<ScoreNoteEvent> notes) {
        var found = new ArrayList<ScoreRestEvent>();
        if (gray == null || gray.length != (long) width * height || notes == null) return found;
        for (var owner : notes) {
            if (owner == null
                    || owner.kind() != ScoreNoteEvent.Kind.PITCHED
                    || owner.beamCount() != 0
                    || owner.unbeamedDurationBeats() != 1
                    || owner.augmentationDots() != 0
                    || owner.tupletDivisor() != 1
                    || owner.tupletNormalNotes() != 1
                    || owner.crossStaffBeam()
                    || owner.tiedFromPrevious()
                    || (owner.articulations() & NoteOrnament.GRACE) != 0
                    || owner.measureIndex() < 0
                    || owner.measureIndex() >= measures.size()
                    || !Float.isFinite(owner.positionInMeasure())
                    || owner.positionInMeasure() < 0
                    || owner.positionInMeasure() > 1
                    || !Float.isFinite(owner.pageY())) continue;
            var region = measures.get(owner.measureIndex());
            float hx =
                    (region.left() + owner.positionInMeasure() * (region.right() - region.left()))
                            * width;
            float hy = owner.pageY() * height;
            for (var staff : staffs) {
                if (staff.index() != owner.staffIndex() || staff.count() != owner.staffCount())
                    continue;
                float[] frame = restFrame(staff, hx);
                float gap = frame[1], printedTop = frame[0] - 4 * gap;
                if (!Float.isFinite(gap)
                        || gap < 4
                        || gap > height * .25f
                        || !Float.isFinite(frame[0])
                        || !Float.isFinite(hx)
                        || !Float.isFinite(hy)
                        || PrintedStemDirection.detect(gray, width, height, hx, hy, gap) != -1)
                    continue;
                int left = Math.max(0, Math.round(hx - gap * 1.1f));
                int right = Math.min(width - 1, Math.round(hx + gap * .9f));
                int top = Math.max(0, Math.round(hy - gap * 2.8f)),
                        bottom = Math.min(height - 1, Math.round(hy + gap * .6f));
                if (left < region.left() * width
                        || right > region.right() * width
                        || top < region.top() * height
                        || bottom > region.bottom() * height) continue;
                boolean[] line = new boolean[bottom - top + 1];
                for (int y = top; y <= bottom; y++)
                    for (int j = 0; j < 5; j++)
                        if (Math.abs(y - (printedTop + j * gap)) <= Math.max(1, gap * .12f))
                            line[y - top] = true;
                int[] counts = new int[line.length],
                        ls = new int[line.length],
                        rs = new int[line.length];
                java.util.Arrays.fill(ls, right + 1);
                java.util.Arrays.fill(rs, left - 1);
                for (int y = top; y <= bottom; y++)
                    if (!line[y - top])
                        for (int x = left; x <= right; x++)
                            if ((gray[y * width + x] & 255) < 170) {
                                counts[y - top]++;
                                ls[y - top] = Math.min(ls[y - top], x);
                                rs[y - top] = Math.max(rs[y - top], x);
                            }
                int headTop = -1, headRows = 0;
                for (int y = Math.max(top, Math.round(hy - gap * .65f)); y <= bottom; y++)
                    if (!line[y - top]) {
                        if (headTop < 0 && counts[y - top] >= gap * .35f) headTop = y;
                        if (Math.abs(y - hy) < gap * .45f
                                && counts[y - top] >= gap
                                && counts[y - top] <= gap * 1.85f) headRows++;
                    }
                if (headTop < 0
                        || headRows < 3
                        || headTop > hy - gap * .2f
                        || headTop < hy - gap * .65f) continue;
                int inkTop = headTop,
                        inkBottom = top - 1,
                        inkLeft = right + 1,
                        inkRight = left - 1,
                        peak = -1,
                        peakCount = 0;
                for (int y = top; y < headTop; y++)
                    if (!line[y - top] && counts[y - top] > 0) {
                        inkTop = Math.min(inkTop, y);
                        inkBottom = y;
                        inkLeft = Math.min(inkLeft, ls[y - top]);
                        inkRight = Math.max(inkRight, rs[y - top]);
                        if (counts[y - top] > peakCount) {
                            peakCount = counts[y - top];
                            peak = y;
                        }
                    }
                if (peak >= 0) {
                    int plateauLast = peak;
                    for (int y = peak + 1; y < headTop; y++) {
                        if (line[y - top]) continue;
                        if (counts[y - top] != peakCount) break;
                        plateauLast = y;
                    }
                    peak = (peak + plateauLast) / 2;
                }
                if (inkTop <= top
                        || inkBottom < headTop - 3
                        || peak < 0
                        || peakCount < gap * .6f
                        || peakCount > gap * 1.3f
                        || inkRight - inkLeft < gap * .7f
                        || inkRight - inkLeft > gap * 1.6f
                        || inkBottom - inkTop < gap
                        || inkBottom - inkTop > gap * 1.8f
                        || peak - inkTop < gap * .2f
                        || peak - inkTop > gap * .65f
                        || inkBottom - peak < gap * .55f
                        || inkBottom - peak > gap * 1.35f) continue;
                int tailFirst = -1, tailLast = -1, tailRows = 0;
                boolean bad = false;
                for (int y = peak + Math.max(2, Math.round(gap * .3f)); y < headTop; y++)
                    if (!line[y - top]) {
                        if (counts[y - top] < 1 || counts[y - top] > gap * .35f) {
                            bad = true;
                            break;
                        }
                        if (tailFirst < 0) tailFirst = y;
                        tailLast = y;
                        tailRows++;
                        if (tailFirst != y) {
                            float before = (ls[tailFirst - top] + rs[tailFirst - top]) * .5f;
                            float now = (ls[y - top] + rs[y - top]) * .5f;
                            if (now > before + gap * .1f) {
                                bad = true;
                                break;
                            }
                        }
                    }
                if (bad || tailRows < Math.max(3, Math.round(gap * .3f)) || tailLast < 0) continue;
                float lastX = (ls[tailLast - top] + rs[tailLast - top]) * .5f;
                float firstX = (ls[tailFirst - top] + rs[tailFirst - top]) * .5f;
                // A rest tail descends leftward. A straight shaft is not that evidence.
                if (firstX - lastX < Math.max(1, gap * .1f)) continue;
                // Prove leftward motion before the occluding head's rounded rim.
                // Its first few pixels must not provide the diagonal evidence.
                int interiorLast = -1;
                for (int y = tailFirst; y <= headTop - Math.max(2, Math.round(gap * .25f)); y++)
                    if (!line[y - top]) interiorLast = y;
                if (interiorLast <= tailFirst) continue;
                float interiorX = (ls[interiorLast - top] + rs[interiorLast - top]) * .5f;
                if (firstX - interiorX < Math.max(1, gap * .06f)) continue;
                if (inkRight - lastX < gap * .2f || Math.abs(lastX - hx) > gap * .65f) continue;
                // The last independently visible tail pixel must enter the measured head body.
                boolean contact = false;
                for (int x = Math.max(left, Math.round(lastX) - 1);
                        x <= Math.min(right, Math.round(lastX) + 1);
                        x++) if ((gray[headTop * width + x] & 255) < 170) contact = true;
                if (!contact) continue;
                boolean otherOwner = false;
                for (var n : notes)
                    if (n != owner && n.measureIndex() == owner.measureIndex()) {
                        float
                                x =
                                        (region.left()
                                                        + n.positionInMeasure()
                                                                * (region.right() - region.left()))
                                                * width,
                                y = n.pageY() * height;
                        if (Math.abs(x - (inkLeft + inkRight) * .5f) < gap * .8f
                                && y >= inkTop - gap * .5f
                                && y <= inkBottom + gap * .5f) {
                            otherOwner = true;
                            break;
                        }
                    }
                if (otherOwner) continue;
                float centerX = (inkLeft + inkRight) * .5f / width;
                found.add(
                        new ScoreRestEvent(
                                owner.measureIndex(),
                                (centerX - region.left()) / (region.right() - region.left()),
                                (inkTop + inkBottom) * .5f / height,
                                (inkBottom - inkTop + 1f) / height,
                                owner.staffIndex(),
                                owner.staffCount(),
                                .5));
            }
        }
        return found;
    }

    private static List<ScoreRestEvent> joinedEighthRests(
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<Staff> staffs,
            List<ScoreNoteEvent> notes) {
        var result = new ArrayList<ScoreRestEvent>();
        if (gray == null || (long) width * height != gray.length || notes == null) return result;
        for (var owner : notes) {
            if (owner.beamCount() != 0
                    || owner.unbeamedDurationBeats() != 1
                    || owner.augmentationDots() != 0
                    || owner.crossStaffBeam()
                    || (owner.articulations() & NoteOrnament.GRACE) != 0
                    || owner.measureIndex() < 0
                    || owner.measureIndex() >= measures.size()) continue;
            var region = measures.get(owner.measureIndex());
            float hx =
                    (region.left() + owner.positionInMeasure() * (region.right() - region.left()))
                            * width;
            float hy = owner.pageY() * height;
            for (var staff : staffs) {
                if (staff.index() != owner.staffIndex() || staff.count() != owner.staffCount())
                    continue;
                float[] frame =
                        staff.pitchTrack() == null
                                ? new float[] {staff.bottom(), staff.gap()}
                                : staff.pitchTrack().at(hx);
                float gap = frame[1], top = frame[0] - gap * 4;
                if (gap < 4 || !Float.isFinite(gap)) continue;
                boolean independentUp =
                        independentUpQuarterHead(gray, width, height, owner, hx, hy, gap, top);
                for (int level = 0; level <= (independentUp ? 5 : 4); level++) {
                    int foot = Math.round(top + level * gap);
                    if (foot - hy < gap * .8f || foot - hy > gap * 2.3f) continue;
                    int
                            start =
                                    Math.max(
                                            Math.round(hy + gap * (independentUp ? .6f : .5f)),
                                            foot - Math.round(gap * .9f)),
                            end = foot - 2;
                    if (start < 0 || end >= height || end - start < gap * .7f) continue;
                    float tail = joinedRestTail(gray, width, height, hx, start, end, gap);
                    if (!Float.isFinite(tail)
                            || !(independentUp
                                    || continuingQuarterShaft(
                                            gray, width, height, hx, hy, tail, foot, gap)))
                        continue;
                    float x = tail / width,
                            pos = (x - region.left()) / (region.right() - region.left());
                    if (pos < 0 || pos > 1) continue;
                    var candidate =
                            new ScoreRestEvent(
                                    owner.measureIndex(),
                                    pos,
                                    (foot - gap * .925f) / height,
                                    gap * 1.85f / height,
                                    owner.staffIndex(),
                                    owner.staffCount(),
                                    .5);
                    boolean duplicate =
                            result.stream()
                                    .anyMatch(
                                            r ->
                                                    r.measureIndex() == candidate.measureIndex()
                                                            && r.staffIndex()
                                                                    == candidate.staffIndex()
                                                            && r.staffCount()
                                                                    == candidate.staffCount()
                                                            && Math.abs(r.positionInMeasure() - pos)
                                                                    < .018f);
                    if (duplicate) continue;
                    var rhythm =
                            TripletRhythmDetector.withRests(
                                    notes, List.of(candidate), measures, gray, width, height);
                    if (Math.abs(rhythm.rests().get(0).durationBeats() - 1. / 3) > .00001) continue;
                    var following = new ArrayList<ScoreNoteEvent>();
                    for (int i = 0; i < notes.size(); i++) {
                        var n = notes.get(i);
                        var marked = rhythm.notes().get(i);
                        if (n.measureIndex() == owner.measureIndex()
                                && n.staffIndex() == owner.staffIndex()
                                && n.staffCount() == owner.staffCount()
                                && n.beamCount() == 1
                                && n.positionInMeasure() > pos + .018f
                                && marked.tupletDivisor() == 3
                                && marked.tupletNormalNotes() == 2) following.add(n);
                        if (n.measureIndex() == owner.measureIndex()
                                && n.staffIndex() == owner.staffIndex()
                                && n.staffCount() == owner.staffCount()
                                && n.beamCount() == 0
                                && Math.abs(n.positionInMeasure() - pos) < .018f
                                && marked.tupletDivisor() != n.tupletDivisor()) {
                            following.clear();
                            break;
                        }
                    }
                    following.sort(
                            java.util.Comparator.comparingDouble(
                                    ScoreNoteEvent::positionInMeasure));
                    ScoreNoteEvent a = null, b = null;
                    for (var n : following)
                        if (a == null) a = n;
                        else if (n.positionInMeasure() > a.positionInMeasure() + .012f) {
                            b = n;
                            break;
                        }
                    if (a == null || b == null) continue;
                    float ax =
                            (region.left()
                                            + a.positionInMeasure()
                                                    * (region.right() - region.left()))
                                    * width;
                    float bx =
                            (region.left()
                                            + b.positionInMeasure()
                                                    * (region.right() - region.left()))
                                    * width;
                    int direction =
                            PrintedStemDirection.detect(
                                    gray, width, height, ax, a.pageY() * height, gap);
                    if (direction == 0
                            || PrintedStemDirection.detect(
                                            gray, width, height, bx, b.pageY() * height, gap)
                                    != direction
                            || !PrintedTupletBeamOwner.connectedHeads(
                                    gray,
                                    width,
                                    height,
                                    ax,
                                    a.pageY() * height,
                                    bx,
                                    b.pageY() * height,
                                    gap,
                                    direction)) continue;
                    result.add(candidate);
                }
            }
        }
        return result;
    }

    /** The quarter's actual upward shaft and broad head are independent of the tail below it. */
    private static boolean independentUpQuarterHead(
            byte[] gray,
            int width,
            int height,
            ScoreNoteEvent note,
            float hx,
            float hy,
            float gap,
            float staffTop) {
        if (note.kind() != ScoreNoteEvent.Kind.PITCHED
                || note.beamCount() != 0
                || note.unbeamedDurationBeats() != 1
                || note.augmentationDots() != 0
                || note.tupletDivisor() != 1
                || note.tupletNormalNotes() != 1
                || note.crossStaffBeam()
                || note.tiedFromPrevious()
                || (note.articulations() & NoteOrnament.GRACE) != 0
                || !Float.isFinite(hx)
                || !Float.isFinite(hy)
                || PrintedStemDirection.detect(gray, width, height, hx, hy, gap) != 1) return false;
        int rows = 0, upperRows = 0;
        for (int y = Math.max(0, Math.round(hy - gap * .35f));
                y <= Math.min(height - 1, Math.round(hy + gap * .15f));
                y++) {
            boolean line = false;
            for (int j = 0; j < 5; j++)
                if (Math.abs(y - (staffTop + j * gap)) <= Math.max(1, gap * .12f)) line = true;
            if (line) continue;
            int ink = 0;
            for (int x = Math.max(0, Math.round(hx - gap * .9f));
                    x <= Math.min(width - 1, Math.round(hx + gap * .9f));
                    x++) if ((gray[y * width + x] & 255) < 170) ink++;
            if (ink >= gap * .75f && ink <= gap * 1.85f) {
                rows++;
                if (y <= hy - gap * .1f) upperRows++;
            }
        }
        return rows >= 3 && upperRows >= 2;
    }

    private static float joinedRestTail(
            byte[] gray, int width, int height, float hx, int start, int end, float gap) {
        int left = Math.max(1, Math.round(hx - gap * 1.25f)),
                right = Math.min(width - 2, Math.round(hx + gap * .4f));
        for (int x = left; x <= right; x++) {
            if ((gray[start * width + x] & 255) >= 170 || (gray[start * width + x - 1] & 255) < 170)
                continue;
            int last = x;
            while (last + 1 <= right && (gray[start * width + last + 1] & 255) < 170) last++;
            if (last - x + 1 < 2 || last - x + 1 > gap * .34f) continue;
            float initial = (x + last) * .5f, previous = initial;
            boolean complete = true;
            for (int y = start + 1; y <= end; y++) {
                int seed = Math.round(previous);
                int lo = seed - 2, hi = seed + 2, found = -1;
                for (int px = Math.max(left, lo); px <= Math.min(right, hi); px++)
                    if ((gray[y * width + px] & 255) < 170) {
                        found = px;
                        break;
                    }
                if (found < 0) {
                    complete = false;
                    break;
                }
                int a = found, b = found;
                while (a > left && (gray[y * width + a - 1] & 255) < 170) a--;
                while (b < right && (gray[y * width + b + 1] & 255) < 170) b++;
                float middle = (a + b) * .5f;
                if (b - a + 1 < 2 || b - a + 1 > gap * .34f || Math.abs(middle - previous) > 1.1f) {
                    complete = false;
                    break;
                }
                previous = middle;
            }
            if (complete && initial - previous >= gap * .2f && initial - previous <= gap * .65f) {
                int continued = 0;
                for (int y = end + 5; y <= Math.min(height - 1, end + Math.round(gap * .6f)); y++) {
                    boolean ink = false;
                    for (int px = Math.max(left, Math.round(previous - gap * .18f));
                            px <= Math.min(right, Math.round(previous + gap * .18f));
                            px++) ink |= (gray[y * width + px] & 255) < 170;
                    if (ink) continued++;
                }
                if (continued <= 1) return (initial + previous) * .5f;
            }
        }
        return Float.NaN;
    }

    private static boolean continuingQuarterShaft(
            byte[] gray,
            int width,
            int height,
            float hx,
            float hy,
            float tail,
            int foot,
            float gap) {
        int start = Math.max(0, Math.round(hy + gap * .55f)),
                end = Math.min(height - 1, Math.round(foot + gap));
        if (end - start < gap * 1.4f) return false;
        for (int x = Math.max(1, Math.round(hx - gap * 1.35f));
                x <= Math.min(width - 2, Math.round(hx + gap * .1f));
                x++) {
            if (tail - x < gap * .25f || tail - x > gap * 1.2f) continue;
            int ink = 0;
            for (int y = start; y <= end; y++)
                if ((gray[y * width + x] & 255) < 170
                        && ((gray[y * width + x - 1] & 255) < 170
                                || (gray[y * width + x + 1] & 255) < 170)) ink++;
            if (ink >= (end - start + 1) * .85f) return true;
        }
        return false;
    }

    /** A cropped crossed head must retain the connected stem below its putative foot. */
    private static boolean continuedRecoveredTail(
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<Staff> staffs,
            ScoreRestEvent rest) {
        if (rest.durationBeats() != .5) return false;
        MeasureRegion region = measures.get(rest.measureIndex());
        float centerX =
                (region.left() + rest.positionInMeasure() * (region.right() - region.left()))
                        * width;
        int end = Math.round((rest.pageY() + rest.pageHeight() * .5f) * height) - 1;
        for (Staff staff : staffs)
            if (staff.index() == rest.staffIndex() && staff.count() == rest.staffCount()) {
                float gap =
                        staff.pitchTrack() == null
                                ? staff.gap()
                                : staff.pitchTrack().at(centerX)[1];
                int left = Math.max(0, Math.round(centerX - gap * .9f));
                int right = Math.min(width - 1, Math.round(centerX + gap * .9f));
                if (continuedRestTail(gray, width, height, left, right, end, gap)) return true;
            }
        return false;
    }

    private static boolean seededOrdinaryRest(
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<Staff> staffs,
            ScoreRestEvent rest) {
        if (rest.durationBeats() != .25 && rest.durationBeats() != .5 && rest.durationBeats() != 1)
            return false;
        if (rest.measureIndex() < 0 || rest.measureIndex() >= measures.size()) return false;
        MeasureRegion region = measures.get(rest.measureIndex());
        float x =
                (region.left() + rest.positionInMeasure() * (region.right() - region.left()))
                        * width;
        float y = rest.pageY() * height;
        for (Staff staff : staffs) {
            if (staff.index() != rest.staffIndex() || staff.count() != rest.staffCount()) continue;
            float[] frame =
                    staff.pitchTrack() == null
                            ? new float[] {staff.bottom(), staff.gap()}
                            : staff.pitchTrack().at(x);
            float bottom = frame[0], gap = frame[1];
            if (y < bottom - gap * 2.25f || y > bottom - gap * .6f) continue;
            int seeds = 0, seedRows = 0;
            int top = Math.max(0, Math.round(y - rest.pageHeight() * height * .5f));
            int end = Math.min(height - 1, Math.round(y + rest.pageHeight() * height * .5f));
            for (int row = top; row <= end; row++) {
                float rule = bottom + Math.round((row - bottom) / gap) * gap;
                if (Math.abs(row - rule) <= gap * .25f) continue;
                int count = 0;
                for (int col = Math.max(0, Math.round(x - gap * .65f));
                        col <= Math.min(width - 1, Math.round(x + gap * .65f));
                        col++) if ((gray[row * width + col] & 255) < 155) count++;
                seeds += count;
                if (count >= 2) seedRows++;
            }
            if (seeds >= 12 && seedRows >= 3) return true;
        }
        return false;
    }

    private static byte[] contrastedRestInk(
            byte[] gray, int width, int height, List<Staff> staffs) {
        return contrastedRestInk(gray, width, height, staffs, 185);
    }

    private static byte[] contrastedRestInk(
            byte[] gray, int width, int height, List<Staff> staffs, int ceiling) {
        if (gray == null || gray.length != (long) width * height || staffs.isEmpty()) return gray;
        List<Float> gaps = new ArrayList<>();
        for (Staff staff : staffs) gaps.add(staff.gap());
        gaps.sort(Float::compare);
        int radius = Math.max(3, Math.round(gaps.get(gaps.size() / 2) * .6f));
        byte[] result = null;
        for (int y = radius; y < height - radius; y++)
            for (int x = radius; x < width - radius; x++) {
                int at = y * width + x, ink = gray[at] & 255;
                if (ink < 170 || ink >= ceiling) continue;
                int bright = 0;
                if ((gray[at - radius] & 255) >= ink + 40) bright++;
                if ((gray[at + radius] & 255) >= ink + 40) bright++;
                if ((gray[at - radius * width] & 255) >= ink + 40) bright++;
                if ((gray[at + radius * width] & 255) >= ink + 40) bright++;
                if (bright >= 2) {
                    if (result == null) result = gray.clone();
                    result[at] = (byte) 169;
                }
            }
        return result == null ? gray : result;
    }

    private static Detection detectWithDots(
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<Staff> staffs,
            List<ScoreNoteEvent> notes,
            boolean refineSymbols) {
        return detectWithDots(gray, width, height, measures, staffs, notes, refineSymbols, false);
    }

    private static Detection detectWithDots(
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<Staff> staffs,
            List<ScoreNoteEvent> notes,
            boolean refineSymbols,
            boolean faintShapes) {
        if (gray == null || gray.length != width * height)
            return new Detection(List.of(), List.of());
        if (staffs.stream().anyMatch(staff -> staff.pitchTrack() != null)) {
            List<Staff> straight = new ArrayList<>();
            for (Staff staff : staffs) if (staff.pitchTrack() == null) straight.add(staff);
            Detection plain =
                    detectWithDots(
                            gray,
                            width,
                            height,
                            measures,
                            straight,
                            notes,
                            refineSymbols,
                            faintShapes);
            List<ScoreRestEvent> combined = new ArrayList<>(plain.rests());
            List<RestDot> dots = new ArrayList<>(plain.dots());
            for (Staff staff : staffs)
                if (staff.pitchTrack() != null) {
                    Detection curved =
                            detectOnPrintedStaff(
                                    gray, width, height, measures, staff, notes, faintShapes);
                    combined.addAll(curved.rests());
                    dots.addAll(curved.dots());
                }
            return collected(combined, dots);
        }
        List<ScoreRestEvent> result = new ArrayList<>();
        List<RestDot> restDots = new ArrayList<>();
        List<Runnable> componentReads = new ArrayList<>();
        List<Placement> placements = new ArrayList<>();
        for (Staff s : staffs) placements.add(new Placement(s, (s.top() + s.bottom()) * .5f));
        // In polyphonic engraving rests for the upper voice move one space above their
        // usual centre to clear the simultaneously held lower voice.
        for (Staff s : staffs)
            for (int offset = 1; offset <= 6; offset++)
                placements.add(
                        new Placement(
                                new Staff(
                                        s.top() - offset * s.gap(),
                                        s.bottom() - offset * s.gap(),
                                        s.gap(),
                                        s.index(),
                                        s.count()),
                                (s.top() + s.bottom()) * .5f));
        for (Staff s : staffs)
            for (int offset = 1; offset <= 6; offset++)
                placements.add(
                        new Placement(
                                new Staff(
                                        s.top() + offset * s.gap(),
                                        s.bottom() + offset * s.gap(),
                                        s.gap(),
                                        s.index(),
                                        s.count()),
                                (s.top() + s.bottom()) * .5f));
        int[][] rowColumns = null;
        int[] columnInk = null, rowDark = null, rowLongest = null;
        for (Placement placement : placements) {
            Staff staff = placement.staff();
            float gap = staff.gap();
            boolean ordinary =
                    staffs.stream()
                            .anyMatch(
                                    s ->
                                            s.index() == staff.index()
                                                    && s.count() == staff.count()
                                                    && Math.abs(s.top() - staff.top()) < gap * .1f);
            boolean deepVoice =
                    staffs.stream()
                            .anyMatch(
                                    s ->
                                            s.index() == staff.index()
                                                    && s.count() == staff.count()
                                                    && staff.top() - s.top() > gap * 1.9f
                                                    && staff.top() - s.top() < gap * 6.1f);
            boolean shallowLowered =
                    staffs.stream()
                            .anyMatch(
                                    s ->
                                            s.index() == staff.index()
                                                    && s.count() == staff.count()
                                                    && Math.abs(staff.top() - s.top() - gap)
                                                            < gap * .1f);
            int top = Math.max(0, Math.round(staff.top() + gap * .25f));
            boolean highVoice =
                    staffs.stream()
                            .anyMatch(
                                    s ->
                                            s.index() == staff.index()
                                                    && s.count() == staff.count()
                                                    && Math.abs(s.top() - staff.top() - gap * 2)
                                                            < gap * .1f);
            boolean farRaised =
                    staffs.stream()
                            .anyMatch(
                                    s ->
                                            s.index() == staff.index()
                                                    && s.count() == staff.count()
                                                    && s.top() - staff.top() > gap * 2.9f
                                                    && s.top() - staff.top() < gap * 6.1f);
            int bottom =
                    Math.min(height - 1, Math.round(staff.bottom() + (highVoice ? 0 : gap * .3f)));
            if (bottom < top) continue;
            boolean[] line = new boolean[bottom - top + 1];
            boolean[] narrowLine = new boolean[line.length];
            // Remove only long horizontal ink rows, including a line's antialiased edge.
            // Raw row counts are shared; each placement still builds its own staff mask.
            if (rowDark == null) {
                rowDark = new int[height];
                rowLongest = new int[height];
                java.util.Arrays.fill(rowDark, -1);
                rowColumns = new int[height][];
            }
            for (int y = top; y <= bottom; y++) {
                if (rowDark[y] < 0) {
                    int dark = 0, longest = 0, run = 0;
                    for (int x = 0; x < width; x++) {
                        if ((gray[y * width + x] & 255) < 170) {
                            dark++;
                            longest = Math.max(longest, ++run);
                        } else run = 0;
                    }
                    rowDark[y] = dark;
                    rowLongest[y] = longest;
                }
                int dark = rowDark[y], longest = rowLongest[y];
                float nearestLine = staff.top() + Math.round((y - staff.top()) / gap) * gap;
                // Rectification can leave only a local antialiased edge of a rule.
                // A continuous five-gap segment still establishes line ink; rest
                // bulbs are far narrower and cannot satisfy this support.
                boolean longInk = dark > width * .25f || longest >= gap * 5;
                narrowLine[y - top] = Math.abs(y - nearestLine) <= gap * .2f && longInk;
                line[y - top] = Math.abs(y - nearestLine) <= gap * .35f && longInk;
            }
            // A broad line halo can clean a warped rule but also erase a real
            // rest bulb. Read both masks, sharing the expensive raw-row counts.
            boolean[] edgeLine =
                    RestStaffRuleEdge.extend(
                            gray, width, height, top, staff.top(), gap, narrowLine);
            boolean sameMask = java.util.Arrays.equals(narrowLine, line);
            boolean[][] masks =
                    edgeLine == narrowLine
                            ? (sameMask
                                    ? new boolean[][] {line}
                                    : new boolean[][] {narrowLine, line})
                            : (sameMask
                                    ? new boolean[][] {line, edgeLine}
                                    : new boolean[][] {narrowLine, line, edgeLine});
            boolean[] contrasted =
                    ordinary
                            ? RestStaffRuleEdge.extendContrasted(
                                    gray, width, height, top, staff.top(), gap, narrowLine)
                            : narrowLine;
            if (contrasted != narrowLine && !java.util.Arrays.equals(contrasted, edgeLine)) {
                masks = java.util.Arrays.copyOf(masks, masks.length + 1);
                masks[masks.length - 1] = contrasted;
            }
            for (boolean[] baseMask : masks) {
                // A detached beam above a rest can join unrelated columns in a full-height
                // projection. A second, bulb-only band excludes that beam without clipping
                // an eighth or sixteenth rest; it cannot classify cropped quarter rectangles.
                for (int pass = 0;
                        pass < (ordinary ? 3 : shallowLowered || deepVoice || farRaised ? 2 : 1);
                        pass++) {
                    boolean quarterOnly = deepVoice && pass > 0;
                    int scanTop =
                            deepVoice && pass == 0
                                    ? Math.max(top, Math.round(staff.top() + gap * .75f))
                                    : (ordinary || shallowLowered || farRaised) && pass > 0
                                            ? Math.max(top, Math.round(staff.top() + gap * .85f))
                                            : top;
                    int scanBottom =
                            deepVoice && pass == 0 || farRaised && pass > 0 || ordinary && pass == 2
                                    ? Math.min(bottom, Math.round(staff.bottom() - gap * .7f))
                                    : bottom;
                    if (scanTop > scanBottom) continue;
                    boolean[] mask =
                            scanTop == top && scanBottom == bottom
                                    ? baseMask
                                    : java.util.Arrays.copyOfRange(
                                            baseMask, scanTop - top, scanBottom - top + 1);
                    if (columnInk == null) columnInk = new int[width];
                    else java.util.Arrays.fill(columnInk, 0);
                    for (int y = scanTop; y <= scanBottom; y++) {
                        if (mask[y - scanTop]) continue;
                        int[] dark = rowColumns[y];
                        if (dark == null) {
                            dark = new int[rowDark[y]];
                            int count = 0, row = y * width;
                            for (int x = 0; x < width; x++)
                                if ((gray[row + x] & 255) < 170) dark[count++] = x;
                            rowColumns[y] = dark;
                        }
                        for (int x : dark) columnInk[x]++;
                    }
                    int start = -1;
                    for (int x = 0; x <= width; x++) {
                        int ink = x < width ? columnInk[x] : 0;
                        if (ink >= 2) {
                            if (start < 0) start = x;
                        } else if (start >= 0) {
                            boolean lowered =
                                    staffs.stream()
                                            .anyMatch(
                                                    s ->
                                                            s.index() == staff.index()
                                                                    && s.count() == staff.count()
                                                                    && staff.top() - s.top()
                                                                            > gap * .9f
                                                                    && staff.top() - s.top()
                                                                            < gap * 6.1f);
                            boolean deepLowered =
                                    staffs.stream()
                                            .anyMatch(
                                                    s ->
                                                            s.index() == staff.index()
                                                                    && s.count() == staff.count()
                                                                    && staff.top() - s.top()
                                                                            > gap * 1.9f
                                                                    && staff.top() - s.top()
                                                                            < gap * 6.1f);
                            int previousRestCount = result.size();
                            inspect(
                                    gray,
                                    width,
                                    height,
                                    measures,
                                    notes,
                                    staff,
                                    scanTop,
                                    scanBottom,
                                    mask,
                                    start,
                                    x - 1,
                                    result,
                                    restDots,
                                    ordinary,
                                    lowered,
                                    deepLowered,
                                    baseMask != line && baseMask != narrowLine,
                                    (ordinary || shallowLowered || farRaised) && pass > 0,
                                    quarterOnly,
                                    farRaised,
                                    placement.printedCenter() / height,
                                    faintShapes);
                            if (result.size() == previousRestCount
                                    && x - start >= staff.gap() * .7f) {
                                for (var body :
                                        SeparatedRestInk.find(
                                                gray,
                                                width,
                                                height,
                                                start,
                                                x - 1,
                                                scanTop,
                                                scanBottom,
                                                mask,
                                                staff.gap())) {
                                    int bodyTop = Math.max(scanTop, body.top() - 1);
                                    int bodyBottom = Math.min(scanBottom, body.bottom() + 1);
                                    boolean[] bodyMask =
                                            java.util.Arrays.copyOfRange(
                                                    mask,
                                                    bodyTop - scanTop,
                                                    bodyBottom - scanTop + 1);
                                    boolean componentBulbOnly =
                                            (ordinary || shallowLowered || farRaised) && pass > 0;
                                    componentReads.add(
                                            () -> {
                                                if (SeparatedRestInk.represented(
                                                        body, staff, measures, result, width,
                                                        height)) return;
                                                inspect(
                                                        gray,
                                                        width,
                                                        height,
                                                        measures,
                                                        notes,
                                                        staff,
                                                        bodyTop,
                                                        bodyBottom,
                                                        bodyMask,
                                                        body.left(),
                                                        body.right(),
                                                        result,
                                                        restDots,
                                                        ordinary,
                                                        lowered,
                                                        deepLowered,
                                                        baseMask != line && baseMask != narrowLine,
                                                        componentBulbOnly,
                                                        quarterOnly,
                                                        farRaised,
                                                        placement.printedCenter() / height,
                                                        faintShapes);
                                            });
                                }
                            }
                            start = -1;
                        }
                    }
                }
            }
        }
        if (refineSymbols)
            for (Staff staff : staffs) {
                StaffPitchTrack track =
                        StaffPitchTrack.detectForSymbols(
                                gray, width, height, staff.top(), staff.bottom(), staff.gap());
                if (track == null) continue;
                float localGap = track.at(width * .5f)[1];
                Staff calibrated =
                        new Staff(
                                staff.bottom() - 4 * localGap,
                                staff.bottom(),
                                localGap,
                                staff.index(),
                                staff.count(),
                                track);
                Detection additional =
                        detectOnPrintedStaff(
                                gray, width, height, measures, calibrated, notes, faintShapes);
                result.addAll(additional.rests());
                restDots.addAll(additional.dots());
            }
        Detection standard = collected(result, restDots);
        result.clear();
        result.addAll(standard.rests());
        restDots.clear();
        restDots.addAll(standard.dots());
        for (Runnable read : componentReads) read.run();
        return collected(result, restDots);
    }

    private static Detection collected(List<ScoreRestEvent> result, List<RestDot> restDots) {
        List<ScoreRestEvent> unique = new ArrayList<>();
        for (ScoreRestEvent rest : result) {
            int duplicate = -1;
            for (int i = 0; i < unique.size(); i++) {
                ScoreRestEvent r = unique.get(i);
                if (r.measureIndex() == rest.measureIndex()
                        && r.staffIndex() == rest.staffIndex()
                        && r.staffCount() == rest.staffCount()
                        && Math.abs(r.positionInMeasure() - rest.positionInMeasure()) < .018f
                        && Math.abs(r.pageY() - rest.pageY())
                                < Math.max(
                                        .001f, Math.max(r.pageHeight(), rest.pageHeight()) * .5f)) {
                    duplicate = i;
                    break;
                }
            }
            if (duplicate < 0) unique.add(rest);
            // A shifted crop may see only the lower bulb of a complete sixteenth rest.
            // Prefer the taller complete glyph, not the crop with a slightly earlier x.
            else if (rest.pageHeight() > unique.get(duplicate).pageHeight() * 1.1f)
                unique.set(duplicate, rest);
        }
        // Original staff/full-glyph passes precede shifted crops. A subpixel x
        // difference must not give a later, cropped interpretation priority.
        unique.sort(
                java.util.Comparator.comparingInt(ScoreRestEvent::measureIndex)
                        .thenComparingDouble(ScoreRestEvent::positionInMeasure));
        List<RestDot> selectedDots = new ArrayList<>();
        for (RestDot dot : restDots) if (unique.contains(dot.rest())) selectedDots.add(dot);
        return new Detection(List.copyOf(unique), List.copyOf(selectedDots));
    }

    /**
     * Translate columns in a narrow staff band; map results back to the source page. Keep glyph
     * height intact: small local spacing errors must not stretch the rest and leave partial staff
     * rules connected to its hook.
     */
    private static Detection detectOnPrintedStaff(
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            Staff staff,
            List<ScoreNoteEvent> notes,
            boolean faintShapes) {
        return detectOnPrintedStaff(
                gray, width, height, measures, staff, notes, faintShapes, false);
    }

    private static Detection detectOnPrintedStaff(
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            Staff staff,
            List<ScoreNoteEvent> notes,
            boolean faintShapes,
            boolean contactOnly) {
        int first = Math.max(0, (int) Math.floor(staff.top() - staff.gap() * 6));
        int last = Math.min(height, (int) Math.ceil(staff.bottom() + staff.gap() * 6.4f));
        if (last <= first) return new Detection(List.of(), List.of());
        int bandHeight = last - first;
        byte[] flat = new byte[width * bandHeight];
        if (width > 0 && height > 0 && gray != null && (long) width * height <= gray.length) {
            float[] bottoms = new float[width];
            for (int x = 0; x < width; x++) bottoms[x] = staff.pitchTrack().at(x)[0];
            for (int y = 0; y < bandHeight; y++) {
                int offset = y * width;
                for (int x = 0; x < width; x++) {
                    int sourceY = Math.round(bottoms[x] + (first + y - staff.bottom()));
                    flat[offset + x] =
                            sourceY >= 0 && sourceY < height
                                    ? gray[sourceY * width + x]
                                    : (byte) 255;
                }
            }
        } else {
            for (int x = 0; x < width; x++) {
                float[] local = staff.pitchTrack().at(x);
                for (int y = 0; y < bandHeight; y++) {
                    int sourceY = Math.round(local[0] + (first + y - staff.bottom()));
                    flat[y * width + x] =
                            sourceY >= 0 && sourceY < height
                                    ? gray[sourceY * width + x]
                                    : (byte) 255;
                }
            }
        }
        List<MeasureRegion> mappedMeasures = new ArrayList<>();
        for (MeasureRegion region : measures) {
            float x = (region.left() + region.right()) * .5f * width;
            mappedMeasures.add(
                    new MeasureRegion(
                            region.left(),
                            region.right(),
                            (flatY(staff, x, region.top() * height) - first) / bandHeight,
                            (flatY(staff, x, region.bottom() * height) - first) / bandHeight));
        }
        List<ScoreNoteEvent> mappedNotes = new ArrayList<>();
        for (ScoreNoteEvent n : notes) {
            if (n.measureIndex() < 0 || n.measureIndex() >= measures.size()) continue;
            MeasureRegion region = measures.get(n.measureIndex());
            float x =
                    (region.left() + n.positionInMeasure() * (region.right() - region.left()))
                            * width;
            float y = (flatY(staff, x, n.pageY() * height) - first) / bandHeight;
            mappedNotes.add(
                    new ScoreNoteEvent(
                                    n.measureIndex(),
                                    n.positionInMeasure(),
                                    n.staffStep(),
                                    n.staffIndex(),
                                    n.staffCount(),
                                    y,
                                    n.tiedFromPrevious(),
                                    n.augmentationDots(),
                                    n.beamCount(),
                                    n.writtenAccidental(),
                                    n.unbeamedDurationBeats(),
                                    n.tupletDivisor(),
                                    n.followingRestBeats(),
                                    n.articulations(),
                                    n.clefBottomDiatonic(),
                                    n.crossStaffBeam(),
                                    n.leadingRestBeats(),
                                    n.compactOpening(),
                                    n.octaveShift(),
                                    n.boundaryTies(),
                                    n.tupletNormalNotes())
                            .withStemDirection(n.stemDirection())
                            .withTupletRatio(n.tupletDivisor(), n.tupletNormalNotes())
                            .withKind(n.kind()));
        }
        Staff rectified =
                new Staff(
                        staff.top() - first,
                        staff.bottom() - first,
                        staff.gap(),
                        staff.index(),
                        staff.count());
        Detection detected =
                contactOnly
                        ? BeamedHeadQuarterRests.detectWithDots(
                                flat,
                                width,
                                bandHeight,
                                mappedMeasures,
                                List.of(rectified),
                                mappedNotes)
                        : detectWithDots(
                                flat,
                                width,
                                bandHeight,
                                mappedMeasures,
                                List.of(rectified),
                                mappedNotes,
                                false,
                                faintShapes);
        List<ScoreRestEvent> rests = new ArrayList<>();
        List<RestDot> dots = new ArrayList<>();
        for (ScoreRestEvent rest : detected.rests())
            rests.add(sourceRest(rest, staff, measures, width, height, first, bandHeight));
        for (RestDot dot : detected.dots())
            dots.add(
                    new RestDot(
                            dot.x(),
                            sourceY(staff, dot.x(), first + dot.y()),
                            sourceRest(
                                    dot.rest(),
                                    staff,
                                    measures,
                                    width,
                                    height,
                                    first,
                                    bandHeight)));
        return new Detection(List.copyOf(rests), List.copyOf(dots));
    }

    private static float flatY(Staff staff, float x, float sourceY) {
        float[] local = staff.pitchTrack().at(x);
        return staff.bottom() + (sourceY - local[0]);
    }

    private static float sourceY(Staff staff, float x, float flatY) {
        float[] local = staff.pitchTrack().at(x);
        return local[0] + (flatY - staff.bottom());
    }

    private static ScoreRestEvent sourceRest(
            ScoreRestEvent rest,
            Staff staff,
            List<MeasureRegion> measures,
            int width,
            int height,
            int first,
            int bandHeight) {
        MeasureRegion region = measures.get(rest.measureIndex());
        float x =
                (region.left() + rest.positionInMeasure() * (region.right() - region.left()))
                        * width;
        float y = sourceY(staff, x, first + rest.pageY() * bandHeight) / height;
        float h = rest.pageHeight() * bandHeight / height;
        return new ScoreRestEvent(
                rest.measureIndex(),
                rest.positionInMeasure(),
                y,
                h,
                rest.staffIndex(),
                rest.staffCount(),
                rest.durationBeats());
    }

    private static void inspect(
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            Staff staff,
            int top,
            int bottom,
            boolean[] line,
            int left,
            int right,
            List<ScoreRestEvent> result,
            List<RestDot> restDots,
            boolean ordinary,
            boolean lowered,
            boolean deepLowered,
            boolean edgeFallback,
            boolean bulbOnly,
            boolean quarterOnly,
            boolean farRaised,
            float printedStaffCenter,
            boolean faintShapes) {
        float gap = staff.gap();
        if (right - left + 1 < gap * .7f || right - left + 1 > gap * 1.85f) return;
        int minY = bottom + 1, maxY = top - 1;
        int[] ink = new int[bottom - top + 1];
        for (int y = top; y <= bottom; y++)
            if (!line[y - top]) {
                for (int x = left; x <= right; x++)
                    if ((gray[y * width + x] & 255) < 170) ink[y - top]++;
                if (ink[y - top] > 0) {
                    minY = Math.min(minY, y);
                    maxY = y;
                }
            }
        int fullBottom = maxY;
        maxY =
                withoutFollowingNoteDot(
                        width, height, measures, notes, staff, left, right, top, ink, line, minY,
                        maxY);
        boolean noteDotRemoved = maxY < fullBottom;
        if (right - left + 1 > gap * 1.6f && !noteDotRemoved) return;
        // Shifted voices need the complete raw rectangle and supporting rule;
        // a masked beam fragment can otherwise resemble a half or whole rest.
        boolean half =
                !bulbOnly
                        && !quarterOnly
                        && ordinary
                        && halfRest(staff, top, line, ink, left, right, minY, maxY);
        if (!bulbOnly && !quarterOnly && !half) {
            int[] bounds =
                    HalfRestRuleBody.find(
                            gray, width, height, staff.top(), gap, left, right, top, ink);
            if (bounds != null) {
                half = true;
                minY = bounds[0];
                maxY = bounds[1];
            }
        }
        int[] wholeBounds =
                !bulbOnly && !quarterOnly && ordinary
                        ? wholeRest(gray, width, staff, top, ink, left, right)
                        : null;
        if (!bulbOnly && !quarterOnly && wholeBounds == null)
            wholeBounds =
                    HalfRestRuleBody.hanging(
                            gray, width, height, staff.top(), gap, left, right, top, ink);
        boolean whole = wholeBounds != null;
        if (whole) {
            minY = wholeBounds[0];
            maxY = wholeBounds[1];
        }
        // Three regularly spaced bulbs prove a flagged rest, even when its zigzag
        // silhouette also resembles a quarter rest. Read the complete glyph first.
        boolean thirtySecond =
                !quarterOnly
                        && !half
                        && !whole
                        && maxY - minY >= gap * 2.9f
                        && maxY - minY <= gap * 4.25f
                        && Math.abs(maxY - staff.bottom()) <= gap * .55f
                        && restBulbs(ink, line, top, minY, maxY, gap, faintShapes).size() == 3;
        boolean quarter =
                !thirtySecond
                        && !bulbOnly
                        && (!deepLowered || quarterOnly)
                        && quarterRest(gray, width, staff, top, line, left, right, minY, maxY);
        if (quarterOnly && !quarter) return;
        if (!bulbOnly && !quarter && !half && !whole && !thirtySecond && ordinary) {
            int tail = quarterTailWithoutSpeck(ink, top, minY, maxY, gap);
            if (tail < maxY
                    && quarterRest(gray, width, staff, top, line, left, right, minY, tail)) {
                quarter = true;
                maxY = tail;
            }
        }
        if (deepLowered && (minY <= top || maxY >= bottom)) return;
        if (lowered
                && !half
                && (quarter
                        ? straightShafts(gray, width, left, right, minY, maxY, line, top, gap, true)
                        : CompactQuarterRestContour.parallelSpines(
                                gray, width, left, right, minY, maxY, line, top, gap))) return;
        if (!half
                && !whole
                && !CompactQuarterRestContour.hasContrastedInk(
                        gray, width, left, right, minY, maxY, line, top, gap)) return;
        boolean eighth =
                maxY - minY >= gap * 1.3f
                        && maxY - minY <= gap * 2.2f
                        && Math.abs(maxY - (staff.bottom() - gap)) <= gap * .4f;
        boolean sixteenth =
                !deepLowered
                        && maxY - minY >= (faintShapes ? Math.round(gap * 2.35f) : gap * 2.35f)
                        && maxY - minY <= gap * 3.25f
                        && Math.abs(maxY - staff.bottom()) <= gap * .35f;
        if (sixteenth
                && !quarter
                && !half
                && !whole
                && straightShafts(gray, width, left, right, minY, maxY, line, top, gap, true))
            return;
        if (!quarter && !half && !whole) {
            if (eighth
                    && PrintedFlatGlyph.matches(gray, width, height, left, minY, right, maxY, gap))
                return;
            if (eighth
                    && straightShafts(
                            gray,
                            width,
                            left,
                            right,
                            Math.max(0, minY - Math.round(gap)),
                            maxY,
                            line,
                            top,
                            gap,
                            false)
                    && !(lowered
                            && separatedUpQuarterAboveRest(
                                    gray, width, height, measures, notes, staff, left, right, minY,
                                    maxY, line, top, gap))) return;
            // Three rest bulbs also reverse their row centres five times. The
            // complete bulb count, spacing and diagonal foot below adjudicate
            // those glyphs; a wave silhouette alone must not erase them.
            if (!thirtySecond
                    && RestVerticalWave.crosses(gray, width, height, left, right, minY, maxY, gap))
                return;
            if ((!eighth && !sixteenth && !thirtySecond)
                    || minY
                            < Math.round(
                                    staff.top()
                                            + gap
                                                    * (thirtySecond
                                                            ? .15f
                                                            : deepLowered ? .8f : .85f))
                    || minY > staff.top() + gap * 1.55f) return;
            List<Integer> lobes = restBulbs(ink, line, top, minY, maxY, gap, faintShapes);
            if (thirtySecond
                    ? lobes.size() != 3
                            || lobes.get(1) - lobes.get(0) < gap * .7f
                            || lobes.get(1) - lobes.get(0) > gap * 1.3f
                            || lobes.get(2) - lobes.get(1) < gap * .7f
                            || lobes.get(2) - lobes.get(1) > gap * 1.3f
                            || maxY - lobes.get(2) < gap * .8f
                    : eighth
                            ? lobes.size() != 1 || maxY - lobes.get(0) < gap * .8f
                            : lobes.size() != 2
                                    || lobes.get(1) - lobes.get(0) < gap * .7f
                                    || lobes.get(1) - lobes.get(0) > gap * 1.3f
                                    || maxY - lobes.get(1) < gap * .8f) return;
            // Below the second bulb only a narrow tail remains. Its foot slopes left of the tip;
            // accidentals, paired dots and isolated note flags do not have this geometry.
            int footRight = -1;
            for (int y = maxY - Math.round(gap * .45f); y <= maxY; y++)
                if (!line[y - top]) {
                    if (ink[y - top] > gap * .50f) {
                        // A thin antialiased edge of the next verified staff rule can join
                        // the tail for one row without widening the printed rest itself.
                        boolean ruleEdge = false;
                        int reach = noteDotRemoved ? Math.max(1, Math.round(gap * .2f)) : 1;
                        for (int dy = 1; dy <= reach && y + dy <= bottom; dy++)
                            if (line[y + dy - top]) {
                                ruleEdge = true;
                                break;
                            }
                        if (ruleEdge) continue;
                        return;
                    }
                    for (int x = left; x <= right; x++)
                        if ((gray[y * width + x] & 255) < 170) footRight = Math.max(footRight, x);
                }
            if (footRight < 0 || right - footRight < gap * .15f) return;
            if (RestDiagonalContinuation.crosses(gray, width, left, right, minY, maxY, gap)) return;
            if (eighth
                    && deepLowered
                    && continuedRestTail(gray, width, height, left, right, maxY, gap)) return;
        }
        if (eighth
                && deepLowered
                && ForteRestGuard.owns(gray, width, height, left, right, minY, maxY, gap)) return;
        if (eighth
                && deepLowered
                && eighthRestLetterRow(gray, width, height, left, right, minY, maxY, gap)) return;
        float centerX = (left + right) * .5f / width;
        float centerY = (minY + maxY) * .5f / height;
        for (int m = 0; m < measures.size(); m++) {
            MeasureRegion region = measures.get(m);
            if (centerX <= region.left()
                    || centerX >= region.right()
                    || printedStaffCenter < region.top()
                    || printedStaffCenter > region.bottom()) continue;
            if ((m > 0 && region.equals(measures.get(m - 1)))
                    || (m + 1 < measures.size() && region.equals(measures.get(m + 1)))) return;
            if (farRaised) {
                boolean heldBelow = false;
                for (ScoreNoteEvent note : notes)
                    if (note.measureIndex() == m
                            && note.staffIndex() == staff.index()
                            && note.staffCount() == staff.count()
                            && (ScoreNoteTiming.hasIndependentSustain(note)
                                    || quarter
                                            && (movingVoiceBelow(
                                                            gray, width, height, region, note, gap)
                                                    || connectedUpStemVoice(
                                                            gray, width, height, region, notes,
                                                            note, gap)))
                            && note.pageY() * height > maxY + gap * .65f
                            && Math.abs(
                                            (region.left()
                                                                    + note.positionInMeasure()
                                                                            * (region.right()
                                                                                    - region
                                                                                            .left()))
                                                            * width
                                                    - (left + right) * .5f)
                                    < gap * .9f) {
                        heldBelow = true;
                        break;
                    }
                if (!heldBelow) continue;
            }
            if (lowered && !half && !whole) {
                boolean heldAbove = false;
                for (ScoreNoteEvent note : notes)
                    if (note.measureIndex() == m
                            && note.staffIndex() == staff.index()
                            && note.staffCount() == staff.count()
                            && note.pageY() * height < minY - gap * .7f
                            && (ScoreNoteTiming.hasIndependentSustain(note)
                                    || movingVoiceAbove(gray, width, height, region, note, gap)
                                    || quarter
                                            && Math.abs(
                                                            (region.left()
                                                                                    + note
                                                                                                    .positionInMeasure()
                                                                                            * (region
                                                                                                            .right()
                                                                                                    - region
                                                                                                            .left()))
                                                                            * width
                                                                    - (left + right) * .5f)
                                                    > gap * 1.8f)) {
                        heldAbove = true;
                        break;
                    }
                if (!heldAbove
                        && !(quarter
                                && StackedVoiceRestEvidence.above(
                                        result,
                                        m,
                                        staff.index(),
                                        staff.count(),
                                        region,
                                        width,
                                        height,
                                        left,
                                        right,
                                        minY,
                                        gap))
                        && !((eighth || sixteenth)
                                && beamedVoiceAroundRest(
                                        gray, width, height, region, notes, m, staff, left, right,
                                        minY, maxY))) continue;
            }
            for (ScoreNoteEvent note : notes)
                if (note.measureIndex() == m
                        && note.staffIndex() == staff.index()
                        && note.staffCount() == staff.count()) {
                    float noteX =
                            (region.left()
                                            + note.positionInMeasure()
                                                    * (region.right() - region.left()))
                                    * width;
                    if (lowered
                            && !ScoreNoteTiming.hasIndependentSustain(note)
                            && RestStaffRuleEdge.noteStem(
                                    gray,
                                    width,
                                    height,
                                    left,
                                    right,
                                    minY,
                                    gap,
                                    noteX,
                                    note.pageY() * height)) return;
                    if (!ordinary
                            && !lowered
                            && !ScoreNoteTiming.hasIndependentSustain(note)
                            && RestStaffRuleEdge.noteStemAbove(
                                    gray,
                                    width,
                                    height,
                                    left,
                                    right,
                                    maxY,
                                    gap,
                                    noteX,
                                    note.pageY() * height)) return;
                    if (noteX >= left - gap * .65f && noteX <= right + gap * .65f) {
                        // A rest may share an attack column with a separate held voice. Keep
                        // rejecting note fragments unless the whole rest is clear of its head.
                        float noteY = note.pageY() * height;
                        boolean independentMovingVoice =
                                !ordinary
                                                && (lowered
                                                        || movingVoiceBelow(
                                                                gray, width, height, region, note,
                                                                gap)
                                                        || quarter
                                                                && noteY > maxY + gap * .65f
                                                                && connectedUpStemVoice(
                                                                        gray, width, height, region,
                                                                        notes, note, gap))
                                        || ordinary
                                                && noteY > maxY + gap * .65f
                                                && (movingVoiceBelow(
                                                                gray, width, height, region, note,
                                                                gap)
                                                        || quarter
                                                                && connectedUpStemVoice(
                                                                        gray, width, height, region,
                                                                        notes, note, gap));
                        if (!ScoreNoteTiming.hasIndependentSustain(note) && !independentMovingVoice
                                || noteY >= minY - gap * .65f && noteY <= maxY + gap * .65f) return;
                    }
                    if (note.writtenAccidental() != ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                            && noteX > right
                            && noteX < right + gap * 1.8f
                            && note.pageY() * height >= minY
                            && note.pageY() * height <= maxY
                            && (!sixteenth
                                    || CompactQuarterRestContour.parallelSpines(
                                            gray, width, left, right, minY, maxY, line, top, gap)
                                    || note.writtenAccidental() == ScoreNoteEvent.ACCIDENTAL_SHARP
                                            && straightShafts(
                                                    gray, width, left, right, minY, maxY, line, top,
                                                    gap, true, true))) return;
                }
            List<InkDot> dots =
                    augmentationDots(gray, width, height, staff, right, region, notes, m);
            double duration =
                    (whole ? 4 : half ? 2 : quarter ? 1 : thirtySecond ? .125 : eighth ? .5 : .25)
                            * (dots.size() == 2 ? 1.75 : dots.size() == 1 ? 1.5 : 1);
            ScoreRestEvent rest =
                    new ScoreRestEvent(
                            m,
                            (centerX - region.left()) / (region.right() - region.left()),
                            centerY,
                            (maxY - minY + 1f) / height,
                            staff.index(),
                            staff.count(),
                            duration);
            if (edgeFallback
                    && result.stream()
                            .anyMatch(
                                    r ->
                                            r.measureIndex() == rest.measureIndex()
                                                    && r.staffIndex() == rest.staffIndex()
                                                    && r.staffCount() == rest.staffCount()
                                                    && Math.abs(
                                                                    r.positionInMeasure()
                                                                            - rest
                                                                                    .positionInMeasure())
                                                            < .018f)) return;
            result.add(rest);
            for (InkDot dot : dots) restDots.add(new RestDot(dot.x(), dot.y(), rest));
            return;
        }
    }

    /** A separate upward quarter shaft cannot be the tail of the complete rest below it. */
    private static boolean separatedUpQuarterAboveRest(
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            Staff staff,
            int left,
            int right,
            int minY,
            int maxY,
            boolean[] line,
            int top,
            float gap) {
        if (straightShafts(gray, width, left, right, minY, maxY, line, top, gap, false))
            return false;
        for (var note : notes) {
            if (note.kind() != ScoreNoteEvent.Kind.PITCHED
                    || note.staffIndex() != staff.index()
                    || note.staffCount() != staff.count()
                    || note.measureIndex() < 0
                    || note.measureIndex() >= measures.size()
                    || note.beamCount() != 0
                    || note.unbeamedDurationBeats() != 1
                    || note.augmentationDots() != 0
                    || note.tupletDivisor() != 1
                    || note.tupletNormalNotes() != 1
                    || note.crossStaffBeam()
                    || note.tiedFromPrevious()
                    || (note.articulations() & NoteOrnament.GRACE) != 0) continue;
            var measure = measures.get(note.measureIndex());
            float x =
                    (measure.left() + note.positionInMeasure() * (measure.right() - measure.left()))
                            * width;
            float y = note.pageY() * height;
            if (!Float.isFinite(x)
                    || !Float.isFinite(y)
                    || note.positionInMeasure() < 0
                    || note.positionInMeasure() > 1
                    || x < left - gap * .65f
                    || x > right + gap * .65f
                    || minY - y < gap * .65f
                    || minY - y > gap * 2.3f
                    || minY < measure.top() * height
                    || minY > measure.bottom() * height
                    || PrintedStemDirection.detect(gray, width, height, x, y, gap) != 1) continue;
            int blank = 0;
            for (int row = Math.max(0, Math.round(y + gap * .35f)); row < minY; row++) {
                boolean empty = true;
                for (int col = left; col <= right; col++)
                    if ((gray[row * width + col] & 255) < 170) {
                        empty = false;
                        break;
                    }
                blank = empty ? blank + 1 : 0;
                if (blank >= Math.max(2, Math.round(gap * .12f))) return true;
            }
        }
        return false;
    }

    /** Count rounded flag bulbs without letting suppressed staff rows split them. */
    private static List<Integer> restBulbs(
            int[] source,
            boolean[] line,
            int top,
            int minY,
            int maxY,
            float gap,
            boolean faintShapes) {
        int[] ink = source.clone();
        for (int y = minY; y <= maxY; y++)
            if (line[y - top]) {
                int before = y - 1, after = y + 1;
                while (before >= minY && line[before - top]) before--;
                while (after <= maxY && line[after - top]) after++;
                if (before >= minY && after <= maxY)
                    ink[y - top] = Math.round((ink[before - top] + ink[after - top]) * .5f);
            }
        var lobes = new ArrayList<Integer>();
        int run = 0, start = 0;
        int bulbWidth = Math.max(2, Math.round(gap * (faintShapes ? .50f : .58f)));
        int bulbRows = Math.max(2, Math.round(gap * .22f));
        for (int y = minY; y <= maxY + 1; y++) {
            boolean bulb =
                    y <= maxY
                            && (ink[y - top] >= bulbWidth
                                    || y > minY
                                            && y < maxY
                                            && ink[y - top] == bulbWidth - 1
                                            && ink[y - 1 - top] >= bulbWidth
                                            && ink[y + 1 - top] >= bulbWidth);
            if (bulb) {
                if (run++ == 0) start = y;
            } else {
                if (run >= bulbRows)
                    appendRestBulbs(lobes, ink, line, top, start, y - 1, gap, bulbRows, false);
                run = 0;
            }
        }
        return lobes;
    }

    /**
     * A broad mask can keep the valley above the width threshold. Separately rounded, visible peaks
     * still prove two flags at the printed spacing.
     */
    private static void appendRestBulbs(
            List<Integer> lobes,
            int[] ink,
            boolean[] line,
            int top,
            int start,
            int end,
            float gap,
            int bulbRows,
            boolean refined) {
        int reach = Math.max(2, Math.round(gap * 1.25f));
        int prominence = Math.max(2, (int) Math.ceil(gap * .18f));
        int support = Math.max(1, Math.round(gap * .22f));
        for (int valley = start + bulbRows; valley <= end - bulbRows; valley++) {
            int left = -1, right = -1;
            for (int y = Math.max(start, valley - reach); y < valley; y++)
                if (left < 0 || ink[y - top] >= ink[left - top]) left = y;
            for (int y = valley + 1; y <= Math.min(end, valley + reach); y++)
                if (right < 0 || ink[y - top] > ink[right - top]) right = y;
            if (left < 0
                    || right < 0
                    || right - left < gap * .7f
                    || right - left > gap * 1.3f
                    || Math.min(ink[left - top], ink[right - top]) - ink[valley - top] < prominence)
                continue;
            int visibleLeft = 0, visibleRight = 0;
            for (int y = Math.max(start, left - support);
                    y <= Math.min(valley - 1, left + support);
                    y++) if (!line[y - top] && ink[y - top] >= ink[left - top] - 2) visibleLeft++;
            for (int y = Math.max(valley + 1, right - support);
                    y <= Math.min(end, right + support);
                    y++) if (!line[y - top] && ink[y - top] >= ink[right - top] - 2) visibleRight++;
            if (visibleLeft < bulbRows || visibleRight < bulbRows) continue;
            appendRestBulbs(lobes, ink, line, top, start, valley, gap, bulbRows, true);
            appendRestBulbs(lobes, ink, line, top, valley + 1, end, gap, bulbRows, true);
            return;
        }
        int peak = start;
        for (int y = start + 1; y <= end; y++) if (ink[y - top] > ink[peak - top]) peak = y;
        lobes.add(refined ? peak : (start + end) / 2);
    }

    /**
     * An italic descender can resemble a lowered eighth rest. Three separately bounded outline
     * letters with a shared baseline establish the text row.
     */
    private static boolean eighthRestLetterRow(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            int minY,
            int maxY,
            float gap) {
        int x0 = Math.max(0, Math.round(left - gap * 5)),
                x1 = Math.min(width - 1, Math.round(right + gap * 5));
        int y0 = Math.max(0, Math.round(minY - gap * .55f)),
                y1 = Math.min(height - 1, Math.round(maxY + gap * .15f));
        int w = x1 - x0 + 1, h = y1 - y0 + 1;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        List<int[]> letters = new ArrayList<>();
        for (int origin = 0; origin < seen.length; origin++) {
            if (seen[origin] || (gray[(y0 + origin / w) * width + x0 + origin % w] & 255) >= 170)
                continue;
            int take = 0, size = 1, area = 0, a = x1, b = x0, c = y1, d = y0;
            queue[0] = origin;
            seen[origin] = true;
            while (take < size) {
                int at = queue[take++], x = x0 + at % w, y = y0 + at / w;
                area++;
                a = Math.min(a, x);
                b = Math.max(b, x);
                c = Math.min(c, y);
                d = Math.max(d, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < x0 || xx > x1 || yy < y0 || yy > y1) continue;
                        int next = (yy - y0) * w + xx - x0;
                        if (!seen[next] && (gray[yy * width + xx] & 255) < 170) {
                            seen[next] = true;
                            queue[size++] = next;
                        }
                    }
            }
            int glyphWidth = b - a + 1, glyphHeight = d - c + 1;
            if (a == x0
                    || b == x1
                    || c == y0
                    || d == y1
                    || a <= right && b >= left
                    || glyphWidth < gap * .35f
                    || glyphWidth > gap * 1.4f
                    || glyphHeight < gap * .65f
                    || glyphHeight > gap * 1.65f
                    || Math.abs(c - minY) > gap * .4f
                    || d > maxY - gap * .25f
                    || area < gap * gap * .10f
                    || area > glyphWidth * glyphHeight * .72f) continue;
            letters.add(new int[] {a, b, d});
        }
        for (int[] seed : letters) {
            int count = 0, first = right, last = left;
            for (int[] glyph : letters)
                if (Math.abs(glyph[2] - seed[2]) <= gap * .22f) {
                    count++;
                    first = Math.min(first, glyph[0]);
                    last = Math.max(last, glyph[1]);
                }
            if (count >= 3 && last - first >= gap * 3) return true;
        }
        return false;
    }

    /** A staff mask must not turn a longer connected glyph into an eighth rest. */
    private static boolean continuedRestTail(
            byte[] gray, int width, int height, int left, int right, int end, float gap) {
        int above = Math.max(3, Math.round(gap * .5f)), below = Math.max(4, Math.round(gap * .65f));
        if (end - above < 0 || end + below >= height) return false;
        for (int origin = left; origin <= right; origin++)
            for (int step = 2; step <= 12; step++) {
                float slope = -step * .1f;
                int inside = 0, outside = 0;
                for (int dy = -above; dy <= below; dy++) {
                    int x = Math.round(origin + slope * dy), y = end + dy;
                    if (x < 1 || x >= width - 1) continue;
                    boolean ink =
                            (gray[y * width + x] & 255) < 170
                                    && ((gray[y * width + x - 1] & 255) < 170
                                            || (gray[y * width + x + 1] & 255) < 170);
                    if (ink) {
                        if (dy <= 0) inside++;
                        else outside++;
                    }
                }
                if (inside >= (above + 1) * .85f && outside >= below * .85f) return true;
            }
        return false;
    }

    /** A sharp has two straight shafts; rest tails do not keep two fixed ink columns. */
    private static boolean straightShafts(
            byte[] gray,
            int width,
            int left,
            int right,
            int minY,
            int maxY,
            boolean[] line,
            int top,
            float gap,
            boolean pair) {
        return straightShafts(gray, width, left, right, minY, maxY, line, top, gap, pair, false);
    }

    /**
     * Faded shaft evidence is allowed only beside an independently recognized sharp. Applying it to
     * unowned rest shapes can erase real paired-bulb rests.
     */
    private static boolean straightShafts(
            byte[] gray,
            int width,
            int left,
            int right,
            int minY,
            int maxY,
            boolean[] line,
            int top,
            float gap,
            boolean pair,
            boolean faded) {
        int span = Math.round(gap * 1.65f);
        List<Integer> shafts = new ArrayList<>();
        int flank = Math.max(2, Math.round(gap * .25f));
        for (int x = left; x <= right; x++)
            for (int start = minY; start + span <= maxY + 1; start++) {
                int ink = 0, total = 0;
                for (int y = start; y < start + span; y++)
                    if (y < top || y - top >= line.length || !line[y - top]) {
                        total++;
                        int shade = gray[y * width + x] & 255;
                        boolean contrasted =
                                faded
                                        && pair
                                        && shade < 185
                                        && x >= flank
                                        && x + flank < width
                                        && ((gray[y * width + x - flank] & 255)
                                                                        + (gray[
                                                                                        y * width
                                                                                                + x
                                                                                                + flank]
                                                                                & 255))
                                                                * .5f
                                                        - shade
                                                >= 30;
                        if (shade < 125 || contrasted) ink++;
                    }
                // Broad staff-rule masks can hide over half of a short shaft. Require
                // a full-space span, but count only the independently visible rows.
                if (total >= gap * .65f && ink >= total * .9f) {
                    shafts.add(x);
                    break;
                }
            }
        if (!pair) return !shafts.isEmpty();
        for (int a : shafts)
            for (int b : shafts) if (b - a >= gap * .35f && b - a <= gap * .9f) return true;
        return false;
    }

    /** A separate staccato above a lower note must not lengthen the rest above it. */
    private static int withoutFollowingNoteDot(
            int width,
            int height,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            Staff staff,
            int left,
            int right,
            int top,
            int[] ink,
            boolean[] line,
            int minY,
            int maxY) {
        float gap = staff.gap();
        if (maxY - minY < gap * 2) return maxY;
        int start = maxY;
        while (start > minY && (ink[start - 1 - top] > 0 || line[start - 1 - top])) start--;
        if (maxY - start + 1 > gap * .6f) return maxY;
        for (int y = start; y <= maxY; y++) if (ink[y - top] > gap * .6f) return maxY;
        int end = start - 1;
        while (end > minY && ink[end - top] == 0) end--;
        if (start - end < gap * .25f
                || start - end > gap * .8f
                || end - minY < gap * 1.3f
                || end - minY > gap * 2.2f) return maxY;
        for (ScoreNoteEvent note : notes) {
            if (note.staffIndex() != staff.index()
                    || note.staffCount() != staff.count()
                    || note.measureIndex() < 0
                    || note.measureIndex() >= measures.size()) continue;
            float y = note.pageY() * height;
            if (y < maxY + gap * .3f || y > maxY + gap * 2) continue;
            MeasureRegion region = measures.get(note.measureIndex());
            float x =
                    (region.left() + note.positionInMeasure() * (region.right() - region.left()))
                            * width;
            if (Math.abs(x - (left + right) * .5f) <= gap * .65f) return end;
        }
        return maxY;
    }

    /** A complete rest can interrupt a beam only when both printed shafts support that beam. */
    private static boolean beamedVoiceAroundRest(
            byte[] gray,
            int width,
            int height,
            MeasureRegion region,
            List<ScoreNoteEvent> notes,
            int measure,
            Staff staff,
            int left,
            int right,
            int restTop,
            int restBottom) {
        float gap = staff.gap();
        for (boolean above : new boolean[] {true, false}) {
            ScoreNoteEvent before = null, after = null;
            float beforeX = -1, afterX = width;
            for (ScoreNoteEvent note : notes) {
                if (note.measureIndex() != measure
                        || note.staffIndex() != staff.index()
                        || note.staffCount() != staff.count()
                        || note.beamCount() < 1
                        || (above
                                ? note.pageY() * height < restTop + gap * .65f
                                : note.pageY() * height > restBottom - gap * .65f)) continue;
                float x =
                        (region.left()
                                        + note.positionInMeasure()
                                                * (region.right() - region.left()))
                                * width;
                if (x < left - gap && x > beforeX) {
                    before = note;
                    beforeX = x;
                }
                if (x > right + gap && x < afterX) {
                    after = note;
                    afterX = x;
                }
            }
            if (before == null || after == null || afterX - beforeX > gap * 18) continue;
            int a = Math.round(beforeX + (above ? 1 : -1) * gap * .55f),
                    b = Math.round(afterX + (above ? 1 : -1) * gap * .55f);
            if (a < 0 || b >= width) continue;
            int thickness = Math.max(3, Math.round(gap * .18f));
            int top = Math.max(0, Math.round(above ? restTop - gap * 3 : restBottom + gap * .4f));
            int bottom =
                    Math.min(
                            height - thickness - 1,
                            Math.round(above ? restTop - gap * .4f : restBottom + gap * 3));
            for (int y = top; y <= bottom; y++)
                for (int end = Math.max(top, y - Math.round(gap));
                        end <= Math.min(bottom, y + gap);
                        end++) {
                    if (!beamStemReaches(
                                    gray,
                                    width,
                                    height,
                                    a,
                                    Math.min(y, Math.round(before.pageY() * height)),
                                    Math.max(y, Math.round(before.pageY() * height)),
                                    gap)
                            || !beamStemReaches(
                                    gray,
                                    width,
                                    height,
                                    b,
                                    Math.min(end, Math.round(after.pageY() * height)),
                                    Math.max(end, Math.round(after.pageY() * height)),
                                    gap)) continue;
                    int ink = 0;
                    for (int x = a; x <= b; x++) {
                        int row = Math.round(y + (end - y) * (x - a) / (float) (b - a));
                        int rows = 0;
                        for (int dy = 0; dy < thickness; dy++)
                            if ((gray[(row + dy) * width + x] & 255) < 170) rows++;
                        if (rows == thickness) ink++;
                    }
                    if (ink < (b - a + 1) * .95f) continue;
                    return true;
                }
        }
        return false;
    }

    private static boolean beamStemReaches(
            byte[] gray, int width, int height, int x, int top, int bottom, float gap) {
        if (bottom >= height || bottom - top < gap * 2) return false;
        for (int column = Math.max(0, x - 2); column <= Math.min(width - 1, x + 2); column++) {
            int ink = 0;
            for (int y = top; y <= bottom; y++) if ((gray[y * width + column] & 255) < 170) ink++;
            if (ink >= (bottom - top + 1) * .9f) return true;
        }
        return false;
    }

    /** A displaced lower rest may accompany a moving, independently up-stemmed voice. */
    private static boolean movingVoiceAbove(
            byte[] gray,
            int width,
            int height,
            MeasureRegion region,
            ScoreNoteEvent note,
            float gap) {
        return movingVoiceStem(gray, width, height, region, note, gap, true);
    }

    /** A full attached beam independently identifies a moving voice below a separate rest. */
    private static boolean connectedUpStemVoice(
            byte[] gray,
            int width,
            int height,
            MeasureRegion region,
            List<ScoreNoteEvent> notes,
            ScoreNoteEvent note,
            float gap) {
        float nx =
                (region.left() + note.positionInMeasure() * (region.right() - region.left()))
                        * width;
        int direction =
                note.stemDirection() != 0
                        ? note.stemDirection()
                        : PrintedStemDirection.detect(
                                gray, width, height, nx, note.pageY() * height, gap);
        if (direction != 1
                || note.beamCount() < 1
                || note.beamCount() > 2
                || note.crossStaffBeam()
                || (note.articulations() & NoteOrnament.GRACE) != 0) return false;
        float x =
                (region.left() + note.positionInMeasure() * (region.right() - region.left()))
                        * width;
        for (var other : notes) {
            if (other == note
                    || other.measureIndex() != note.measureIndex()
                    || other.staffIndex() != note.staffIndex()
                    || other.staffCount() != note.staffCount()
                    || other.beamCount() != note.beamCount()
                    || other.crossStaffBeam()
                    || (other.articulations() & NoteOrnament.GRACE) != 0) continue;
            float ox =
                    (region.left() + other.positionInMeasure() * (region.right() - region.left()))
                            * width;
            int otherDirection =
                    other.stemDirection() != 0
                            ? other.stemDirection()
                            : PrintedStemDirection.detect(
                                    gray, width, height, ox, other.pageY() * height, gap);
            if (otherDirection != 1) continue;
            if (Math.abs(ox - x) < gap * .8f || Math.abs(ox - x) > gap * 26) continue;
            if (PrintedTupletBeamOwner.connectedHeads(
                    gray,
                    width,
                    height,
                    Math.min(x, ox),
                    (x < ox ? note.pageY() : other.pageY()) * height,
                    Math.max(x, ox),
                    (x < ox ? other.pageY() : note.pageY()) * height,
                    gap,
                    1)) return true;
        }
        return false;
    }

    private static boolean movingVoiceBelow(
            byte[] gray,
            int width,
            int height,
            MeasureRegion region,
            ScoreNoteEvent note,
            float gap) {
        return movingVoiceStem(gray, width, height, region, note, gap, false);
    }

    private static boolean movingVoiceStem(
            byte[] gray,
            int width,
            int height,
            MeasureRegion region,
            ScoreNoteEvent note,
            float gap,
            boolean upward) {
        float x =
                (region.left() + note.positionInMeasure() * (region.right() - region.left()))
                        * width;
        float y = note.pageY() * height;
        int top = Math.max(0, Math.round(y + gap * (upward ? -2 : .4f))),
                bottom = Math.min(height - 1, Math.round(y + gap * (upward ? -.4f : 2)));
        if (bottom - top < gap * 1.4f) return false;
        for (int col = Math.max(0, Math.round(x + gap * (upward ? .4f : -.85f)));
                col <= Math.min(width - 1, Math.round(x + gap * (upward ? .85f : -.4f)));
                col++) {
            int ink = 0, blank = 0, longest = 0;
            for (int row = top; row <= bottom; row++) {
                if ((gray[row * width + col] & 255) < 170) {
                    ink++;
                    blank = 0;
                } else longest = Math.max(longest, ++blank);
            }
            if (ink >= (bottom - top + 1) * .9f && longest <= Math.max(1, Math.round(gap * .15f)))
                return true;
        }
        return false;
    }

    /** Dots belong to a recognized rest only in the adjacent upper staff space. */
    static List<InkDot> augmentationDots(
            byte[] gray,
            int width,
            int height,
            Staff staff,
            int restRight,
            MeasureRegion region,
            List<ScoreNoteEvent> notes,
            int measure) {
        float gap = staff.gap();
        int left = Math.max(0, restRight + Math.max(2, Math.round(gap * .12f)));
        int right =
                Math.min(
                        width - 1,
                        Math.min(
                                Math.round(region.right() * width) - 1,
                                restRight + Math.round(gap * 2.4f)));
        int top = Math.max(0, Math.round(staff.top() + gap * 1.03f));
        int bottom = Math.min(height - 1, Math.round(staff.top() + gap * 1.97f));
        if (left >= right || top >= bottom) return List.of();
        int w = right - left + 1, h = bottom - top + 1;
        boolean[] seen = new boolean[w * h];
        int[] stack = new int[w * h];
        // A dot can touch the antialiased edge of a thick staff rule. Exclude
        // only long rows at the expected rule height before tracing components;
        // otherwise that small round mark becomes a crop-wide rejected component.
        for (int y = top; y <= bottom; y++) {
            float nearestLine = staff.top() + Math.round((y - staff.top()) / gap) * gap;
            if (Math.abs(y - nearestLine) > gap * .2f) continue;
            int dark = 0;
            for (int x = 0; x < width; x++) if ((gray[y * width + x] & 255) < 170) dark++;
            if (dark > width * .25f)
                java.util.Arrays.fill(seen, (y - top) * w, (y - top + 1) * w, true);
        }
        List<InkDot> dots = new ArrayList<>();
        for (int seed = 0; seed < seen.length; seed++) {
            int sx = seed % w, sy = seed / w;
            if (seen[seed] || (gray[(top + sy) * width + left + sx] & 255) >= 170) continue;
            int size = 0;
            stack[size++] = seed;
            seen[seed] = true;
            int area = 0, minX = w, maxX = -1, minY = h, maxY = -1;
            while (size > 0) {
                int at = stack[--size], x = at % w, y = at / w;
                area++;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                        int next = ny * w + nx;
                        if (!seen[next] && (gray[(top + ny) * width + left + nx] & 255) < 170) {
                            seen[next] = true;
                            stack[size++] = next;
                        }
                    }
            }
            int dw = maxX - minX + 1, dh = maxY - minY + 1;
            if (minX == 0
                    || maxX == w - 1
                    || minY == 0
                    || maxY == h - 1
                    || dw < gap * .16f
                    || dh < gap * .16f
                    || dw > gap * .7f
                    || dh > gap * .7f
                    || dw > dh * 2
                    || dh > dw * 2
                    || area < gap * gap * .025f
                    || area > gap * gap * .32f
                    || area < dw * dh * .5f) continue;
            float x = left + (minX + maxX) * .5f, y = top + (minY + maxY) * .5f;
            if (Math.abs(y - (staff.top() + gap * 1.5f)) > gap * .25f) continue;
            boolean ownedByNote = false;
            for (ScoreNoteEvent note : notes)
                if (note.measureIndex() == measure
                        && note.staffIndex() == staff.index()
                        && note.staffCount() == staff.count()) {
                    float nx =
                            (region.left()
                                            + note.positionInMeasure()
                                                    * (region.right() - region.left()))
                                    * width;
                    if (Math.abs(nx - x) < gap * .65f) {
                        ownedByNote = true;
                        break;
                    }
                }
            if (!ownedByNote) dots.add(new InkDot(x, y));
        }
        dots.sort(java.util.Comparator.comparingDouble(InkDot::x));
        List<InkDot> accepted = new ArrayList<>();
        float previous = restRight;
        for (InkDot dot : dots) {
            float distance = dot.x() - previous;
            if (accepted.isEmpty()
                    ? distance < gap * .25f || distance > gap * 1.3f
                    : distance < gap * .4f || distance > gap * 1.1f) continue;
            accepted.add(dot);
            previous = dot.x();
            if (accepted.size() == 2) break;
        }
        return List.copyOf(accepted);
    }

    /**
     * A half-rest is a filled rectangle sitting on the middle staff rule. Require flat, wide rows:
     * an oval head or thin articulation is not a rest.
     */
    private static boolean halfRest(
            Staff staff,
            int top,
            boolean[] line,
            int[] ink,
            int left,
            int right,
            int minY,
            int maxY) {
        float gap = staff.gap(), middle = staff.top() + 2 * gap;
        int h = maxY - minY + 1, w = right - left + 1;
        // The last retained row lies on an integer raster after line-edge removal.
        // Round its allowed distance up so a fractional staff gap cannot reject
        // an otherwise complete rectangle by less than one pixel.
        if (h < gap * .25f
                || h > gap * .65f
                || minY < middle - gap * .7f
                || maxY > middle
                || middle - maxY > Math.ceil(gap * .25f)) return false;
        int rows = 0;
        for (int y = minY; y <= maxY; y++)
            if (!line[y - top]) {
                if (ink[y - top] < w * .80f) return false;
                rows++;
            }
        return rows >= Math.max(3, Math.round(gap * .25f));
    }

    /**
     * A whole-rest rectangle hangs below the second rule, unlike a half rest resting above the
     * middle rule. Retain the same flat-row/height proof.
     */
    private static int[] wholeRest(
            byte[] gray, int width, Staff staff, int top, int[] ink, int left, int right) {
        float gap = staff.gap(), rule = staff.top() + gap;
        int w = right - left + 1, first = ink.length + top, last = -1, outside = 0;
        for (int i = 0; i < ink.length; i++) {
            int y = top + i;
            if (y >= rule && y <= rule + gap * .7f && ink[i] >= w * .45f) {
                first = Math.min(first, y);
                last = y;
            }
        }
        if (last < first) return null;
        for (int i = 0; i < ink.length; i++)
            if (top + i < first || top + i > last) outside += ink[i];
        // An isolated scan speck is not part of the hanging rectangle; a stem,
        // hook, oval edge or any substantial disconnected ink still rejects it.
        if (outside > Math.max(1, Math.round(gap * .12f))) return null;
        int minY = first, maxY = last, h = maxY - Math.round(rule);
        if (h < gap * .25f
                || h > gap * .65f
                || maxY > rule + gap * .7f
                || minY < rule
                || minY - rule > Math.ceil(gap * .25f)) return null;
        // A detached rectangle between the rules is not a hanging rest. The
        // broad rule mask may hide the join, so inspect the original pixels.
        int blankRows = 0;
        for (int y = Math.max(0, Math.round(rule) + 1); y < minY; y++) {
            int dark = 0;
            for (int x = left; x <= right; x++) if ((gray[y * width + x] & 255) < 170) dark++;
            if (dark < w * .8f && ++blankRows > 1) return null;
        }
        int rows = 0;
        for (int y = Math.max(0, Math.round(rule) + 1); y <= maxY; y++) {
            int dark = 0;
            for (int x = left; x <= right; x++) if ((gray[y * width + x] & 255) < 170) dark++;
            if (dark < w * .80f) {
                if (y >= minY && (y != maxY || dark < w * .45f)) return null;
            } else rows++;
        }
        return rows >= Math.max(3, Math.round(gap * .25f)) ? new int[] {minY, maxY} : null;
    }

    /** One isolated retained pixel cannot extend an otherwise provable quarter-rest contour. */
    static int quarterTailWithoutSpeck(int[] ink, int top, int minY, int maxY, float gap) {
        if (ink == null
                || !Float.isFinite(gap)
                || gap < 4
                || minY < top
                || maxY - top >= ink.length
                || maxY < minY
                || ink[maxY - top] != 1) return maxY;
        int tail = maxY - 1;
        while (tail >= minY && ink[tail - top] == 0) tail--;
        return tail - minY + 1 >= gap * 2.1f && maxY - tail >= Math.ceil(gap * .25f) ? tail : maxY;
    }

    /** Quarter rests have a narrow zigzag above a left-facing lower hook. */
    private static boolean quarterRest(
            byte[] gray,
            int width,
            Staff staff,
            int top,
            boolean[] line,
            int left,
            int right,
            int minY,
            int maxY) {
        float gap = staff.gap();
        int h = maxY - minY + 1;
        if (h < gap * 2.1f
                || h > gap * 3.6f
                || minY < staff.top() + gap * .2f
                || minY > staff.top() + gap * 1.1f
                || maxY + 1 < Math.floor(staff.bottom() - gap * 1.2f)
                || maxY > staff.bottom() - gap * .1f) return false;
        double[] centers = new double[h];
        java.util.Arrays.fill(centers, Double.NaN);
        double[] leftEdges = new double[h], rightEdges = new double[h];
        java.util.Arrays.fill(leftEdges, Double.NaN);
        java.util.Arrays.fill(rightEdges, Double.NaN);
        int widest = 0;
        for (int y = minY; y <= maxY; y++)
            if (!line[y - top]) {
                int n = 0;
                double sum = 0;
                for (int x = left; x <= right; x++)
                    if ((gray[y * width + x] & 255) < 170) {
                        if (n == 0) leftEdges[y - minY] = x - left;
                        rightEdges[y - minY] = x - left;
                        n++;
                        sum += x - left;
                    }
                if (n > 0) centers[y - minY] = sum / n;
                widest = Math.max(widest, n);
            }
        if (widest < gap * .65f) return false;
        for (int i = 0; i < h; i++)
            if (!Double.isFinite(centers[i])) {
                int a = i - 1, b = i + 1;
                while (a >= 0 && !Double.isFinite(centers[a])) a--;
                while (b < h && !Double.isFinite(centers[b])) b++;
                if (a < 0 || b >= h) return false;
                centers[i] = centers[a] + (centers[b] - centers[a]) * (i - a) / (b - a);
                leftEdges[i] = leftEdges[a] + (leftEdges[b] - leftEdges[a]) * (i - a) / (b - a);
                rightEdges[i] = rightEdges[a] + (rightEdges[b] - rightEdges[a]) * (i - a) / (b - a);
            }
        double a = bandCenter(centers, 0, .18),
                b = bandCenter(centers, .22, .38),
                c = bandCenter(centers, .43, .58),
                d = bandCenter(centers, .62, .73),
                e = bandCenter(centers, .80, .91),
                f = bandCenter(centers, .94, 1);
        if (b - a > gap * .10
                && b > c
                && b - c + .5 > gap * .055
                && d - c > gap * .055
                && d - e > gap * .12
                && f - e > gap * .10) return true;
        // Some engravings end the lower hook with a straight downstroke, without a curled foot.
        double hookRight = -Double.MAX_VALUE, hookLeft = Double.MAX_VALUE;
        int window = Math.max(2, Math.round(gap * .18f));
        for (int i = (int) (h * .58); i + window <= h * .80; i++) {
            double mean = 0;
            for (int j = 0; j < window; j++) mean += centers[i + j];
            hookRight = Math.max(hookRight, mean / window);
        }
        for (int i = (int) (h * .78); i + window <= h * .94; i++) {
            double mean = 0;
            for (int j = 0; j < window; j++) mean += centers[i + j];
            hookLeft = Math.min(hookLeft, mean / window);
        }
        return b - a > gap * .10
                        && b - c > gap * .055
                        && hookRight - c > gap * .08
                        && hookRight - hookLeft > gap * .18
                        && f - hookLeft >= -gap * .06
                        && f - hookLeft < gap * .15
                || shortQuarterHook(a, b, c, hookRight, hookLeft, f, gap)
                        && CompactQuarterRestContour.hasContrastedInk(
                                gray, width, left, right, minY, maxY, line, top, gap)
                        && !CompactQuarterRestContour.parallelSpines(
                                gray, width, left, right, minY, maxY, line, top, gap)
                || (CompactQuarterRestContour.matches(centers, gap)
                                || CompactQuarterRestContour.leftSilhouette(
                                        leftEdges, rightEdges, gap))
                        && CompactQuarterRestContour.hasContrastedInk(
                                gray, width, left, right, minY, maxY, line, top, gap);
    }

    /** Full zigzag with a short lower hook; allow half a raster pixel at its rounded turns. */
    static boolean shortQuarterHook(
            double start,
            double peak,
            double valley,
            double hookRight,
            double hookLeft,
            double foot,
            float gap) {
        return Float.isFinite(gap)
                && gap >= 4
                && peak - start + .5 > gap * .14
                && peak - valley + .5 > gap * .18
                && hookRight - valley + .5 > gap * .08
                && hookRight - hookLeft + .5 > gap * .10
                && foot - hookLeft >= -gap * .06
                && foot - hookLeft < gap * .15;
    }

    private static double bandCenter(double[] rows, double from, double to) {
        double sum = 0;
        int n = 0;
        for (int i = (int) (from * (rows.length - 1));
                i <= Math.min(rows.length - 1, (int) (to * (rows.length - 1)));
                i++) {
            sum += rows[i];
            n++;
        }
        return sum / Math.max(1, n);
    }
}
