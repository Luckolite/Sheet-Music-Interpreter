// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter.android;

import android.annotation.TargetApi;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.models.PageMatchBounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Native glyph identities and physical word bounds, independent of PDF text-object grouping. */
@TargetApi(35)
public final class PdfSourceWord {
    public record Glyph(int sourceIndex, String text, RectF bounds) {
        public Glyph {
            bounds = new RectF(bounds);
        }
    }

    public record Token(String text, RectF bounds, List<Glyph> glyphs) {
        public Token {
            bounds = new RectF(bounds);
            glyphs = List.copyOf(glyphs);
        }
    }

    private final PdfRenderer.Page page;
    private final String text;
    private final Map<String, List<PageMatchBounds>> searches = new HashMap<>();
    private final Map<Integer, Map<Integer, Glyph>> glyphs = new HashMap<>();
    private NavigableMap<Float, List<Glyph>> periodRows;

    public PdfSourceWord(PdfRenderer.Page page) {
        this.page = page;
        StringBuilder stream = new StringBuilder();
        for (var content : page.getTextContents()) stream.append(content.getText());
        text = stream.toString();
    }

    public String text() {
        return text;
    }

    public List<PageMatchBounds> matches(String value) {
        return searches.computeIfAbsent(value, page::searchText);
    }

    /** Returns a literal letter and its uniquely attached periods, preserving every native glyph. */
    public List<Token> tokens(String letter, int maximumPeriods) {
        if (letter.codePointCount(0, letter.length()) != 1
                || !Character.isLetter(letter.codePointAt(0))
                || maximumPeriods < 0
                || maximumPeriods > 2)
            throw new IllegalArgumentException(
                    "One source letter and zero to two periods required");
        int codePoint = letter.codePointAt(0);
        List<Token> result = new ArrayList<>();
        var bases =
                index(codePoint).values().stream()
                        .filter(java.util.Objects::nonNull)
                        .sorted(Comparator.comparingInt(Glyph::sourceIndex))
                        .toList();
        for (Glyph base : bases) {
            int start = base.sourceIndex();
            if (!boundary(base, start, -1) || !boundary(base, start + letter.length(), 1)) continue;
            List<Glyph> attached = new ArrayList<>();
            attached.add(base);
            RectF previous = base.bounds();
            boolean ambiguous = false;
            for (Glyph period : nearbyPeriods(base, maximumPeriods + 1)) {
                RectF bounds = period.bounds();
                if (bounds.left < previous.right) {
                    ambiguous = true;
                    break;
                }
                // The source letter supplies the font context. Tiny punctuation ink does not
                // define character advance (a monospaced pair of periods has a large ink gap).
                if (bounds.left - previous.right > base.bounds().height()) break;
                if (attached.size() > maximumPeriods) {
                    ambiguous = true;
                    break;
                }
                attached.add(period);
                previous = bounds;
            }
            if (ambiguous) continue;
            RectF bounds = new RectF(base.bounds());
            StringBuilder literal = new StringBuilder(letter);
            for (int i = 1; i < attached.size(); i++) {
                bounds.union(attached.get(i).bounds());
                literal.append('.');
            }
            result.add(new Token(literal.toString(), bounds, attached));
        }
        return List.copyOf(result);
    }

    private boolean boundary(Glyph base, int position, int direction) {
        int cursor = position;
        while (direction < 0 ? cursor > 0 : cursor < text.length()) {
            int codePoint = direction < 0 ? text.codePointBefore(cursor) : text.codePointAt(cursor);
            int index = direction < 0 ? cursor - Character.charCount(codePoint) : cursor;
            if (codePoint == '.' && index('.').get(index) == null) return false;
            if (!Character.isWhitespace(codePoint) && codePoint != '.') {
                if (!wordCharacter(codePoint)) return true;
                Glyph neighbor = index(codePoint).get(index);
                return neighbor != null && separated(base.bounds(), neighbor.bounds());
            }
            cursor += direction * Character.charCount(codePoint);
        }
        return true;
    }

    private Map<Integer, Glyph> index(int codePoint) {
        return glyphs.computeIfAbsent(
                codePoint,
                point -> {
                    String value = new String(Character.toChars(point));
                    Map<Integer, Glyph> indexed = new HashMap<>();
                    for (var match : matches(value)) {
                        int position = match.getTextStartIndex();
                        if (position < 0
                                || position + value.length() > text.length()
                                || !text.regionMatches(position, value, 0, value.length()))
                            continue;
                        RectF bounds = singleBounds(match);
                        Glyph found = bounds == null ? null : new Glyph(position, value, bounds);
                        if (!indexed.containsKey(position)) indexed.put(position, found);
                        else {
                            Glyph previous = indexed.get(position);
                            if (previous == null
                                    || found == null
                                    || !previous.bounds().equals(found.bounds()))
                                indexed.put(position, null);
                        }
                    }
                    return indexed;
                });
    }

    private List<Glyph> nearbyPeriods(Glyph base, int count) {
        if (periodRows == null) {
            periodRows = new TreeMap<>();
            for (Glyph period : index('.').values())
                if (period != null)
                    periodRows
                            .computeIfAbsent(
                                    period.bounds().centerY(), ignored -> new ArrayList<>())
                            .add(period);
            for (var row : periodRows.values())
                row.sort(Comparator.comparingDouble(glyph -> glyph.bounds().left));
        }
        RectF bounds = base.bounds();
        List<Glyph> result = new ArrayList<>();
        for (var row : periodRows.subMap(bounds.top, true, bounds.bottom, true).values()) {
            int first = 0, last = row.size();
            while (first < last) {
                int middle = (first + last) >>> 1;
                if (row.get(middle).bounds().left < bounds.right) first = middle + 1;
                else last = middle;
            }
            // Only the nearest maximum+1 periods can affect this token. Include their full
            // glyph advances rather than clipping a potential third dot by its ink width.
            for (int i = first; i < row.size() && i - first < count; i++) {
                Glyph period = row.get(i);
                if (Math.min(bounds.bottom, period.bounds().bottom)
                        > Math.max(bounds.top, period.bounds().top)) result.add(period);
            }
        }
        result.sort(Comparator.comparingDouble(glyph -> glyph.bounds().left));
        return result;
    }

    private static boolean wordCharacter(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isLetterOrDigit(codePoint)
                || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    private static RectF singleBounds(PageMatchBounds match) {
        if (match.getBounds().size() != 1) return null;
        RectF bounds = match.getBounds().get(0);
        return Float.isFinite(bounds.left)
                        && Float.isFinite(bounds.top)
                        && Float.isFinite(bounds.right)
                        && Float.isFinite(bounds.bottom)
                        && bounds.width() > 0
                        && bounds.height() > 0
                ? bounds
                : null;
    }

    private static boolean separated(RectF token, RectF neighbor) {
        float rowGap = Math.max(neighbor.top - token.bottom, token.top - neighbor.bottom);
        float wordGap = Math.max(neighbor.left - token.right, token.left - neighbor.right);
        return rowGap >= Math.min(token.height(), neighbor.height()) * .5f
                || wordGap >= Math.max(token.height(), neighbor.height()) * .5f;
    }
}
