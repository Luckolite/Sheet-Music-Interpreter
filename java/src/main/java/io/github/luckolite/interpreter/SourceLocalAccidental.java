// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Actual picker lineage, separate from head admission and rhythmic position anchors. */
final class SourceLocalAccidental {
    record Glyph(int index, int label, ExactComponentMembers.Stats stats, List<Integer> pixels) {
        Glyph {
            pixels = List.copyOf(pixels);
        }
    }

    record Head(
            int index,
            List<Integer> pixels,
            List<Integer> unresolvedParentResidual,
            String status) {
        Head {
            pixels = List.copyOf(pixels);
            unresolvedParentResidual = List.copyOf(unresolvedParentResidual);
        }
    }

    record Choice(
            int note,
            ScoreNoteEvent originalNote,
            int glyph,
            int head,
            int accidental,
            int glyphAlternatives,
            int headAlternatives,
            boolean ambiguous,
            String status) {}

    static final class Role {
        private final List<Integer> pixels;

        private Role(List<Integer> pixels) {
            this.pixels = List.copyOf(pixels);
        }

        List<Integer> pixels() {
            return pixels;
        }
    }

    private static final Object ISSUER = new Object();
    private transient Object issuer;
    private final String rawSha, labelSha;
    private final int width, height, glyphCount, headCount;
    private final List<Glyph> glyphs;
    private final List<Choice> choices;
    private final List<Head> headAlternatives;
    private final List<Integer> unrepresentedGlyphMembers;
    private final boolean complete;

    private SourceLocalAccidental(
            String raw,
            String label,
            int width,
            int height,
            int glyphCount,
            int headCount,
            List<Glyph> glyphs,
            List<Head> headAlternatives,
            List<Choice> choices,
            List<Integer> unrepresentedGlyphMembers,
            boolean complete) {
        issuer = ISSUER;
        rawSha = raw;
        labelSha = label;
        this.width = width;
        this.height = height;
        this.glyphCount = glyphCount;
        this.headCount = headCount;
        this.glyphs = List.copyOf(glyphs);
        this.headAlternatives = List.copyOf(headAlternatives);
        this.choices = List.copyOf(choices);
        this.unrepresentedGlyphMembers = List.copyOf(unrepresentedGlyphMembers);
        this.complete = complete;
    }

    @Override
    public boolean equals(Object value) {
        return value instanceof SourceLocalAccidental other
                && width == other.width
                && height == other.height
                && glyphCount == other.glyphCount
                && headCount == other.headCount
                && complete == other.complete
                && rawSha.equals(other.rawSha)
                && labelSha.equals(other.labelSha)
                && glyphs.equals(other.glyphs)
                && headAlternatives.equals(other.headAlternatives)
                && choices.equals(other.choices)
                && unrepresentedGlyphMembers.equals(other.unrepresentedGlyphMembers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                rawSha,
                labelSha,
                width,
                height,
                glyphCount,
                headCount,
                glyphs,
                headAlternatives,
                choices,
                unrepresentedGlyphMembers,
                complete);
    }

    static SourceLocalAccidental unavailable() {
        return new SourceLocalAccidental(
                "", "", 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of(), false);
    }

    List<Choice> choices() {
        return choices;
    }

    List<Glyph> glyphs() {
        return glyphs;
    }

    List<Head> headAlternatives() {
        return headAlternatives;
    }

    List<Integer> unrepresentedGlyphMembers() {
        return unrepresentedGlyphMembers;
    }

    int headCount() {
        return headCount;
    }

    boolean complete() {
        return complete;
    }

    static SourceLocalAccidental capture(
            ExactComponentMembers.Collector symbols,
            ExactComponentMembers.Collector clefs,
            List<?> originalGlyphs,
            List<?> allHeads,
            ExactComponentMembers.Collector heads,
            Map<?, OmrScoreInterpreter.WrittenAccidentalChoice> actualChoices,
            List<?> finalHeads,
            List<ScoreNoteEvent> notes) {
        var budget = new ExactComponentMembers.Budget(64_000_000, 128L * 1024 * 1024);
        if (symbols == null || clefs == null || heads == null || finalHeads.size() != notes.size())
            return unavailable();
        var partitions = heads.producerPartitions(allHeads, budget);
        boolean authenticatedFrame =
                partitions != null
                        && partitions.sharesFrame(symbols)
                        && partitions.sharesFrame(clefs);
        var glyphIds = new IdentityHashMap<Object, Integer>();
        var entries = new ArrayList<Glyph>();
        boolean complete = authenticatedFrame;
        for (int ordinal = 0; ordinal < originalGlyphs.size(); ordinal++) {
            Object original = originalGlyphs.get(ordinal);
            if (glyphIds.containsKey(original)) complete = false;
            else glyphIds.put(original, ordinal);
            var token = symbols.acceptedGlyph(original);
            if (token == null) token = clefs.acceptedGlyph(original);
            if (token == null) {
                complete = false;
                continue;
            }
            int[] pixels = token.pixels(budget);
            if (pixels == null || pixels.length != token.stats().area()) {
                complete = false;
                continue;
            }
            var vector = new ArrayList<Integer>(pixels.length);
            for (int pixel : pixels) vector.add(pixel);
            entries.add(new Glyph(ordinal, token.label(), token.stats(), List.copyOf(vector)));
        }
        var unrepresented = new ArrayList<Integer>();
        int[] omittedSymbols = symbols.unrepresentedGlyphMembers((byte) 5, budget);
        int[] omittedClefs = clefs.unrepresentedGlyphMembers((byte) 3, budget);
        if (omittedSymbols == null || omittedClefs == null) complete = false;
        if (omittedSymbols != null) for (int pixel : omittedSymbols) unrepresented.add(pixel);
        if (omittedClefs != null) for (int pixel : omittedClefs) unrepresented.add(pixel);

        var headIds = new IdentityHashMap<Object, Integer>();
        var alternatives = new ArrayList<Head>();
        for (int id = 0; id < allHeads.size(); id++) {
            Object head = allHeads.get(id);
            boolean duplicate = headIds.containsKey(head);
            if (duplicate) complete = false;
            else headIds.put(head, id);
            var footprint = heads.producerFootprint(head, budget);
            var partition = partitions == null ? null : partitions.get(head);
            var pixels = new ArrayList<Integer>();
            String status = "UNKNOWN_MISSING_GENUINE_HEAD_MEMBERS";
            if (footprint != null) {
                for (int pixel : footprint.pixels()) pixels.add(pixel);
                status =
                        duplicate
                                ? "UNKNOWN_DUPLICATE_GENUINE_HEAD_IDENTITY"
                                : partition == null
                                        ? "GENUINE_HEAD_MEMBERS_UNKNOWN_PARENT_PARTITION"
                                        : partition.residual().isEmpty()
                                                ? "GENUINE_HEAD_TRAVERSAL_NOT_MUSICAL_COMPLETENESS"
                                                : "GENUINE_HEAD_TRAVERSAL_WITH_UNRESOLVED_PARENT_MEMBERS";
            } else complete = false;
            alternatives.add(
                    new Head(
                            id,
                            List.copyOf(pixels),
                            partition == null ? List.of() : partition.residual(),
                            status));
        }
        var rows = new ArrayList<Choice>();
        for (int n = 0; n < notes.size(); n++) {
            Object head = finalHeads.get(n);
            var choice = actualChoices.get(head);
            if (choice == null) continue;
            Integer glyph = glyphIds.get(choice.glyph()), headId = headIds.get(head);
            var note = notes.get(n);
            var headToken = heads.acceptedProbeHead(head, note.measureIndex(), note.staffIndex());
            String status = "UNKNOWN_UNBOUND_OR_CHANGED_PICKER_ROLE";
            if (choice.head() == head
                    && glyph != null
                    && headId != null
                    && headToken != null
                    && choice.accidental() != ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                    && choice.accidental() == note.writtenAccidental()
                    && !choice.ambiguous()) {
                int[] members = headToken.pixels(budget);
                var partition = partitions == null ? null : partitions.get(head);
                if (members != null
                        && members.length == headToken.stats().area()
                        && partition != null
                        && partition.residual().isEmpty())
                    status = "MODEL_SUPPORTED_ACTUAL_LOCAL_ACCIDENTAL";
            }
            rows.add(
                    new Choice(
                            n,
                            note,
                            glyph == null ? -1 : glyph,
                            headId == null ? -1 : headId,
                            choice.accidental(),
                            choice.glyphAlternatives(),
                            choice.headAlternatives(),
                            choice.ambiguous(),
                            status));
        }
        return new SourceLocalAccidental(
                symbols.rawSha,
                symbols.labelSha,
                symbols.width,
                symbols.height,
                originalGlyphs.size(),
                allHeads.size(),
                entries,
                alternatives,
                rows,
                unrepresented,
                complete);
    }

    Role role(
            AcceptedMeterHeaderBounds.Source source,
            MeterChangeDetector.Crop crop,
            int measure,
            List<ScoreNoteEvent> notes) {
        if (issuer != ISSUER
                || !complete
                || source == null
                || crop == null
                || !source.sameSnapshot()
                || source.width != width
                || source.height != height
                || !source.rawSha.equals(rawSha)
                || !source.labelSha.equals(labelSha)
                || crop.left() < 0
                || crop.right() >= width
                || crop.top() < 0
                || crop.bottom() >= height) return null;
        var covered = new BitSet(width * height);
        var used = new ArrayList<Integer>();
        boolean found = false;
        for (var glyph : glyphs) {
            var s = glyph.stats();
            if (s.maxX() < crop.left()
                    || s.minX() > crop.right()
                    || s.maxY() < crop.top()
                    || s.minY() > crop.bottom()) continue;
            int matchingOwners = 0;
            for (var choice : choices) {
                if (choice.glyph() != glyph.index()
                        || choice.ambiguous()
                        || !choice.status().equals("MODEL_SUPPORTED_ACTUAL_LOCAL_ACCIDENTAL")
                        || choice.note() < 0
                        || choice.note() >= notes.size()) continue;
                var note = notes.get(choice.note());
                if (!note.equals(choice.originalNote())
                        || note.measureIndex() != measure
                        || note.writtenAccidental() != choice.accidental()) continue;
                if (notes.stream().filter(choice.originalNote()::equals).count() != 1) return null;
                matchingOwners++;
            }
            if (matchingOwners != 1) return null;
            found = true;
            for (int pixel : glyph.pixels()) {
                covered.set(pixel);
                used.add(pixel);
            }
        }
        if (!found) return null;
        // Even discarded one/two-pixel class3/5 islands remain contrary/unexplained evidence.
        for (int y = crop.top(); y <= crop.bottom(); y++)
            for (int x = crop.left(); x <= crop.right(); x++) {
                int at = y * width + x, label = source.labels[at] & 255;
                if ((label == 3 || label == 5) && !covered.get(at)) return null;
            }
        return new Role(used);
    }
}
