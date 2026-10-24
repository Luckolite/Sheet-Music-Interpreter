// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Preserves a qualitative outgoing fall on one exact source note without inventing pitch/time. */
public final class PrintedFallOffDetector {
    private PrintedFallOffDetector() {}

    public static ScorePageInterpretation apply(
            ScorePageInterpretation score,
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<PlayingTechniqueDetector.Word> words,
            int physicalPage) {
        return apply(
                score,
                gray,
                width,
                height,
                staffs,
                words,
                physicalPage,
                128000000,
                128000000,
                128000000);
    }

    static ScorePageInterpretation apply(
            ScorePageInterpretation score,
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<PlayingTechniqueDetector.Word> words,
            int physicalPage,
            long ordinaryAllowance,
            long referenceAllowance,
            long calibratedAllowance) {
        if (score == null
                || gray == null
                || width < 3
                || height < 3
                || gray.length != (long) width * height
                || staffs == null
                || words == null
                || physicalPage < 0
                || score.notes().size() > 4096
                || staffs.size() > 128
                || words.size() > 10000
                || score.measures().isEmpty()) return score;
        var ordinary = new PrintedFallOffContours.Budget(ordinaryAllowance);
        var reference = new LiteralStaffContrast.CatalogBudget(referenceAllowance);
        var calibrated = new PrintedFallOffContours.Budget(calibratedAllowance);
        List<PrintedSlurPageCapture.Frame<PrintedSlurOwners.GeometryHead>> frames;
        try {
            if (staffs.stream().anyMatch(Objects::isNull)
                    || words.stream().anyMatch(Objects::isNull)) return score;
            for (var word : words)
                if (!Float.isFinite(word.left() + word.top() + word.right() + word.bottom())
                        || word.left() < 0
                        || word.top() < 0
                        || word.right() > 1
                        || word.bottom() > 1
                        || word.left() > word.right()
                        || word.top() > word.bottom()) return score;
            var heads =
                    PrintedSlurContext.geometryHeads(
                            score.notes(), score.measures(), width, height);
            frames =
                    PrintedSlurFrames.prepare(gray, width, height, staffs, score.notes(), heads)
                            .frames();
        } catch (IllegalArgumentException invalidPhysicalContext) {
            return score;
        }
        var first = PrintedFallOffContours.candidates(gray, width, height, frames, ordinary);
        if (ordinary.refused()) return score;
        var second =
                PrintedFallOffContours.calibratedCandidates(
                        gray, width, height, frames, reference, calibrated);
        if (reference.refused() || calibrated.refused()) return score;
        var accepted = agree(first, second, frames);
        if (accepted.isEmpty()) return score;
        var events = new ArrayList<>(score.expressiveEvents());
        var ids = new HashSet<String>();
        var alreadyOwned = new HashSet<Integer>();
        for (var mark : events) {
            ids.add(mark.eventId());
            int owner = ScoreFallOffNotation.owner(mark, score.notes());
            if (owner >= 0) alreadyOwned.add(owner);
        }
        for (var fall : accepted) {
            if (fall.owner() < 0
                    || fall.owner() >= score.notes().size()
                    || alreadyOwned.contains(fall.owner())) continue;
            var note = score.notes().get(fall.owner());
            String target = ScoreFallOffNotation.target(note);
            if (target == null || (note.articulations() & NoteOrnament.GRACE) != 0) continue;
            // OCR text is a rival, never a convenient part of an outgoing musical glyph.
            boolean text = false;
            for (var word : words)
                if (Float.isFinite(word.left() + word.right() + word.top() + word.bottom())
                        && word.right() * width >= fall.left()
                        && word.left() * width <= fall.right()
                        && word.bottom() * height >= fall.leftY()
                        && word.top() * height <= fall.rightY()) text = true;
            if (text) continue;
            String id = "printed-fall-off:" + target;
            if (!ids.add(id)) continue;
            events.add(
                    new ScoreExpressiveEvent(
                            id,
                            ScoreExpressiveEvent.Kind.FALL_OFF,
                            Optional.empty(),
                            Optional.empty(),
                            ScoreExpressiveEvent.Scope.UNRESOLVED,
                            note.staffIndex(),
                            note.staffCount(),
                            Optional.of(target),
                            ScoreExpressiveEvent.Strength.UNSPECIFIED,
                            ScoreFallOffNotation.CURVED,
                            List.of(
                                    new ScoreExpressiveEvent.Evidence(
                                            ScoreFallOffNotation.SOURCE,
                                            physicalPage,
                                            Math.max(0, Math.min(1, fall.left() / (float) width)),
                                            note.staffIndex(),
                                            note.staffCount(),
                                            "curved falloff"))));
        }
        return events.size() == score.expressiveEvents().size()
                ? score
                : score.withExpressiveEvents(events);
    }

    /** Every candidate survives until agreement. A rival dropped by either route cannot make
     * the other route appear unique. Cross-route agreement never replaces source identity. */
    static List<PrintedFallOffContours.Fall> agree(
            List<PrintedFallOffContours.Fall> first,
            List<PrintedFallOffContours.Fall> second,
            List<? extends PrintedSlurPageCapture.Frame<?>> frames) {
        var a = new TreeMap<Integer, List<PrintedFallOffContours.Fall>>();
        var b = new TreeMap<Integer, List<PrintedFallOffContours.Fall>>();
        for (var f : first) a.computeIfAbsent(f.owner(), i -> new ArrayList<>()).add(f);
        for (var f : second) b.computeIfAbsent(f.owner(), i -> new ArrayList<>()).add(f);
        var owners = new TreeSet<Integer>();
        owners.addAll(a.keySet());
        owners.addAll(b.keySet());
        var out = new ArrayList<PrintedFallOffContours.Fall>();
        for (int owner : owners) {
            var av = a.getOrDefault(owner, List.of());
            var bv = b.getOrDefault(owner, List.of());
            if (av.size() > 1 || bv.size() > 1) continue;
            if (av.isEmpty()) {
                out.add(bv.get(0));
                continue;
            }
            if (bv.isEmpty()) {
                out.add(av.get(0));
                continue;
            }
            float gap = Float.NaN;
            int frameOwners = 0;
            for (var frame : frames)
                for (var head : frame.heads())
                    if (head.owner() == owner) {
                        frameOwners++;
                        gap = frame.track().at(head.x())[1];
                    }
            if (frameOwners != 1 || !Float.isFinite(gap)) continue;
            var x = av.get(0);
            var y = bv.get(0);
            if (Math.abs(x.left() - y.left()) <= gap * .5f
                    && Math.abs(x.right() - y.right()) <= gap * .5f
                    && Math.abs(x.leftY() - y.leftY()) <= gap * .5f
                    && Math.abs(x.rightY() - y.rightY()) <= gap * .5f) out.add(x);
        }
        return List.copyOf(out);
    }
}
