// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A printed portamento continued by two straight arms across one physical row break. */
final class CrossRowPortamento {
    private CrossRowPortamento() {}

    private static final class ArmRowOffsets {
        private static final int[] VALUES = {0, -1, 1};
    }

    record Region(
            int sourceIndex,
            int targetIndex,
            float sourceX,
            float sourceY,
            float targetX,
            float targetY,
            float gap,
            int left,
            int top,
            int right,
            int bottom) {}

    record Found(int sourceIndex, int targetIndex, boolean fromAbove) {}

    static List<Region> regions(
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes) {
        if (gray == null || width <= 0 || height <= 0 || gray.length != (long) width * height)
            return List.of();
        var result = new ArrayList<Region>();
        for (int m = 0; m + 1 < measures.size(); m++) {
            var a = measures.get(m);
            var b = measures.get(m + 1);
            for (var staff : staffs) {
                float gap = staff.gap(), center = (staff.top() + staff.bottom()) * .5f / height;
                if (!Float.isFinite(gap)
                        || gap < 6
                        || center < a.top() - gap / height
                        || center > a.bottom() + gap / height
                        || b.top() - a.top() < gap * 5 / height
                        || b.left() > a.left() - gap * 2 / width) continue;
                int source = -1, target = -1;
                for (int i = 0; i < notes.size(); i++) {
                    var n = notes.get(i);
                    if (n.staffIndex() != staff.index()
                            || n.staffCount() != staff.count()
                            || n.crossStaffBeam()) continue;
                    if (n.measureIndex() == m
                            && (source < 0
                                    || n.positionInMeasure()
                                            > notes.get(source).positionInMeasure())) source = i;
                    if (n.measureIndex() == m + 1
                            && (target < 0
                                    || n.positionInMeasure()
                                            < notes.get(target).positionInMeasure())) target = i;
                }
                if (source < 0 || target < 0) continue;
                var from = notes.get(source);
                var to = notes.get(target);
                if (to.tiedFromPrevious()
                        || from.staffStep() == to.staffStep()
                        || Math.abs(from.staffStep() - to.staffStep()) > 8) continue;
                boolean chord = false;
                for (int i = 0; i < notes.size(); i++) {
                    var n = notes.get(i);
                    if (n.staffIndex() != staff.index() || n.staffCount() != staff.count())
                        continue;
                    if (i != source
                            && n.measureIndex() == m
                            && Math.abs(n.positionInMeasure() - from.positionInMeasure())
                                            * (a.right() - a.left())
                                            * width
                                    < gap * .35f) chord = true;
                    if (i != target
                            && n.measureIndex() == m + 1
                            && Math.abs(n.positionInMeasure() - to.positionInMeasure())
                                            * (b.right() - b.left())
                                            * width
                                    < gap * .35f) chord = true;
                }
                if (chord) continue;
                float x = (a.left() + from.positionInMeasure() * (a.right() - a.left())) * width;
                float tx = (b.left() + to.positionInMeasure() * (b.right() - b.left())) * width;
                var local =
                        PrintedDirectionStaff.local(staff, measures, notes, gray, width, height, x);
                if (local == null) continue;
                PlayingTechniqueDetector.Staff next = null;
                for (var other : staffs) {
                    float mid = (other.top() + other.bottom()) * .5f / height;
                    if (other.index() != staff.index()
                            || other.count() != staff.count()
                            || mid < b.top() - other.gap() / height
                            || mid > b.bottom() + other.gap() / height) continue;
                    var found =
                            PrintedDirectionStaff.local(
                                    other, measures, notes, gray, width, height, tx);
                    if (found != null) {
                        if (next != null) {
                            next = null;
                            break;
                        }
                        next = found;
                    }
                }
                if (next == null || Math.abs(next.gap() - local.gap()) > gap * .15f) continue;
                int left = Math.max(0, Math.round(x - gap)),
                        right = Math.min(width, Math.round(a.right() * width + gap * 2));
                int top = Math.max(0, Math.round(local.top() - gap * 5.5f)),
                        bottom = Math.min(height, Math.round(local.top() - gap * .25f));
                if (right <= left || bottom <= top || right - left > gap * 12) continue;
                result.add(
                        new Region(
                                source,
                                target,
                                x,
                                from.pageY() * height,
                                tx,
                                to.pageY() * height,
                                gap,
                                left,
                                top,
                                right,
                                bottom));
            }
        }
        return List.copyOf(result);
    }

    static PlayingTechniqueDetector.Word consensus(
            OcrText twice, OcrText thrice, Region region, int width, int height) {
        var a = reading(twice, region, 2, width, height);
        var b = reading(thrice, region, 3, width, height);
        if (a == null || b == null) return null;
        if (Math.abs(a.left() + a.right() - b.left() - b.right()) * width > region.gap() * 1.5f
                || Math.abs(a.top() + a.bottom() - b.top() - b.bottom()) * height
                        > region.gap() * 1.5f) return null;
        return a;
    }

    private static PlayingTechniqueDetector.Word reading(
            OcrText text, Region region, int scale, int width, int height) {
        if (text == null) return null;
        PlayingTechniqueDetector.Word result = null;
        for (var block : text.blocks())
            for (var line : block.lines())
                for (var element : line.elements()) {
                    if (!element.text().trim().toLowerCase(Locale.ROOT).matches("port\\.?"))
                        continue;
                    var box = element.box();
                    if (box == null
                            || result != null
                            || box.left < 0
                            || box.top < 0
                            || box.right > (region.right() - region.left()) * scale
                            || box.bottom > (region.bottom() - region.top()) * scale
                            || box.right <= box.left
                            || box.bottom <= box.top) return null;
                    result =
                            new PlayingTechniqueDetector.Word(
                                    "port",
                                    (region.left() + box.left / (float) scale) / width,
                                    (region.top() + box.top / (float) scale) / height,
                                    (region.left() + box.right / (float) scale) / width,
                                    (region.top() + box.bottom / (float) scale) / height);
                }
        return result;
    }

    static List<Found> find(
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            List<PlayingTechniqueDetector.Word> words) {
        var regions = regions(gray, width, height, staffs, measures, notes);
        if (regions.isEmpty()) return List.of();
        byte[] normalized = RestPaperTone.normalize(gray, width, height, regions.get(0).gap());
        var result = new ArrayList<Found>();
        for (var r : regions) {
            boolean printed = false;
            for (var word : words)
                if (word.text().equals("port")
                        && ScoreDynamicsDetector.containsInk(word, gray, width, height)) {
                    float w = (word.right() - word.left()) * width,
                            h = (word.bottom() - word.top()) * height;
                    float x = (word.left() + word.right()) * .5f * width,
                            y = (word.top() + word.bottom()) * .5f * height;
                    if (w >= r.gap() * 1.8f
                            && w <= r.gap() * 7
                            && h >= r.gap() * .6f
                            && h <= r.gap() * 4
                            && x > r.sourceX() + r.gap() * .5f
                            && x < r.right()
                            && y >= r.top()
                            && y <= r.bottom()) printed = true;
                }
            if (!printed) continue;
            int sign =
                    Integer.signum(
                            notes.get(r.sourceIndex()).staffStep()
                                    - notes.get(r.targetIndex()).staffStep());
            boolean outgoing =
                    arm(
                            normalized,
                            gray,
                            width,
                            height,
                            r.sourceX(),
                            r.sourceY(),
                            r.gap(),
                            true,
                            sign,
                            r.right());
            boolean incoming =
                    arm(
                            normalized,
                            gray,
                            width,
                            height,
                            r.targetX(),
                            r.targetY(),
                            r.gap(),
                            false,
                            sign,
                            r.right());
            if (outgoing
                    && incoming
                    && result.stream().noneMatch(f -> f.targetIndex() == r.targetIndex()))
                result.add(new Found(r.sourceIndex(), r.targetIndex(), sign > 0));
        }
        return List.copyOf(result);
    }

    /** Two independently thin, contrasted arms; neither a curved slur nor a staff rail qualifies. */
    private static boolean arm(
            byte[] plane,
            byte[] raw,
            int width,
            int height,
            float headX,
            float headY,
            float gap,
            boolean outgoing,
            int sign,
            int edge) {
        int near = Math.round(headX + (outgoing ? .75f : -.75f) * gap);
        int far =
                outgoing
                        ? Math.min(edge, Math.round(headX + gap * 8))
                        : Math.max(1, Math.round(headX - gap * 4));
        int flank = Math.max(3, Math.round(gap * .4f));
        for (float magnitude = .10f; magnitude <= 1.21f; magnitude += .025f) {
            float slope = magnitude * sign;
            for (int offset = -Math.round(gap); offset <= Math.round(gap); offset++) {
                int run = 0, holes = 0, firstX = 0, firstY = 0, lastX = 0, lastY = 0;
                for (int x = near; outgoing ? x < far : x > far; x += outgoing ? 1 : -1) {
                    int y = Math.round(headY + offset + (x - headX) * slope);
                    boolean hit = false;
                    int hitY = 0;
                    if (x >= 1 && x < width - 1 && y >= flank + 2 && y < height - flank - 2) {
                        for (int dy : ArmRowOffsets.VALUES) {
                            int yy = y + dy;
                            int center = raw[yy * width + x] & 255;
                            if ((plane[yy * width + x] & 255) < 145
                                    && (raw[(yy - flank) * width + x] & 255) - center >= 25
                                    && (raw[(yy + flank) * width + x] & 255) - center >= 25
                                    && (plane[(yy - flank) * width + x] & 255) >= 145
                                    && (plane[(yy + flank) * width + x] & 255) >= 145) {
                                hit = true;
                                hitY = yy;
                                break;
                            }
                        }
                    }
                    if (hit) {
                        if (run == 0) {
                            firstX = x;
                            firstY = hitY;
                        }
                        run++;
                        lastX = x;
                        lastY = hitY;
                    } else if (run > 0 && holes < 2) {
                        run++;
                        holes++;
                    } else {
                        run = 0;
                        holes = 0;
                    }
                    int dx = lastX - firstX, dy = lastY - firstY;
                    if (run >= gap * (outgoing ? 2.5f : 1.6f)
                            && Math.abs(dy) >= gap * .45f
                            && Math.abs(dy) >= Math.abs(dx) * .18f
                            && dy * (outgoing ? sign : -sign) > 0) return true;
                }
            }
        }
        return false;
    }
}
