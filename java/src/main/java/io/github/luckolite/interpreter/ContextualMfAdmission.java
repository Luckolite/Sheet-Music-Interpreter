// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** Literal classification, not exhaustive foreground ownership or pixel rewriting. */
final class ContextualMfAdmission {
    private final byte[] gray;
    private final int width, height;
    private final List<PlayingTechniqueDetector.Staff> staffs;
    private final List<MeasureRegion> measures;
    private final List<ScoreNoteEvent> notes;
    private boolean contextAttempted;
    private DynamicNotationRawContext.Evidence context;

    ContextualMfAdmission(
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes) {
        this.gray = gray;
        this.width = width;
        this.height = height;
        this.staffs = staffs;
        this.measures = measures;
        this.notes = notes;
    }

    boolean proved(PlayingTechniqueDetector.Word word, PlayingTechniqueDetector.Staff owner) {
        if (word == null
                || !"mf".equalsIgnoreCase(word.text())
                || gray == null
                || width <= 0
                || height <= 0
                || gray.length != (long) width * height
                || staffs == null
                || measures == null
                || notes == null
                || owner == null
                || !Float.isFinite(owner.gap())
                || owner.gap() < 8
                || owner.gap() > 100) return false;
        boolean alias = false;
        for (var staff : staffs) if (staff == owner) alias = true;
        if (!alias) return false;
        if (!Float.isFinite(word.left() + word.right() + word.top() + word.bottom())
                || word.left() < 0
                || word.top() < 0
                || word.right() > 1
                || word.bottom() > 1
                || word.right() <= word.left()
                || word.bottom() <= word.top()) return false;
        int left = (int) Math.floor(word.left() * width),
                right = (int) Math.ceil(word.right() * width),
                top = (int) Math.floor(word.top() * height),
                bottom = (int) Math.ceil(word.bottom() * height);
        if (!contextAttempted) {
            contextAttempted = true;
            context =
                    DynamicNotationRawContext.capture(gray, width, height, staffs, measures, notes);
        }
        if (context == null || context.incomplete()) return false;
        var notation =
                ContextualMfBodyEvidence.captureNotation(
                        context, gray, width, height, staffs, owner, left, right, owner.gap());
        var evidence =
                ContextualMfBodyEvidence.inspectLocalized(
                        word.text(),
                        gray,
                        width,
                        height,
                        left,
                        top,
                        right,
                        bottom,
                        owner.gap(),
                        notation);
        // Independent literal, registered context, complete bounded core, all61-level anatomy,
        // and original-halo foreign counterevidence classify the word. UNKNOWN stays original.
        return evidence.body().coreFeaturesSupported();
    }
}
