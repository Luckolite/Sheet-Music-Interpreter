// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.IOException;
import java.math.BigInteger;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Recovers an omitted MusicXML division grid only from exact, consistent written durations. */
public final class MusicXmlDivisionGrid {
    private MusicXmlDivisionGrid() {}

    /**
     * Preserves the ordinary initial value when the part supplies division metadata.
     * A part with no divisions must prove one integral units-per-quarter grid using
     * its ordinary typed metric notes/rests. Tuplets cannot establish the grid:
     * rounded-only tuplet evidence is ambiguous. The consumer validates them with
     * MusicXmlWrittenDuration after resolving the grid. Unspecified rest types add no evidence.
     */
    public static long initialDivisions(Element part) throws IOException {
        if (part == null || !part.getTagName().equals("part"))
            throw new IOException("A MusicXML part is required to resolve divisions");
        if (part.getElementsByTagName("divisions").getLength() != 0) return 1;
        try {
            Long grid = null;
            var notes = part.getElementsByTagName("note");
            for (int i = 0; i < notes.getLength(); i++) {
                var note = (Element) notes.item(i);
                var metric = direct(note, "duration");
                if (direct(note, "grace") != null) {
                    if (metric != null)
                        throw new IOException("A grace note acquired metric duration");
                    continue;
                }
                if (metric == null)
                    throw new IOException("Missing metric duration while resolving divisions");
                long units = Long.parseLong(metric.getTextContent().trim());
                if (units <= 0)
                    throw new IOException("Invalid metric duration while resolving divisions");
                var type = direct(note, "type");
                if (type == null) continue;
                var modification = direct(note, "time-modification");
                if (modification != null) {
                    positive(modification, "actual-notes");
                    positive(modification, "normal-notes");
                    continue;
                }
                long[] base =
                        switch (type.getTextContent().trim()) {
                            case "maxima" -> new long[] {32, 1};
                            case "long" -> new long[] {16, 1};
                            case "breve" -> new long[] {8, 1};
                            case "whole" -> new long[] {4, 1};
                            case "half" -> new long[] {2, 1};
                            case "quarter" -> new long[] {1, 1};
                            case "eighth" -> new long[] {1, 2};
                            case "16th" -> new long[] {1, 4};
                            case "32nd" -> new long[] {1, 8};
                            case "64th" -> new long[] {1, 16};
                            case "128th" -> new long[] {1, 32};
                            case "256th" -> new long[] {1, 64};
                            case "512th" -> new long[] {1, 128};
                            case "1024th" -> new long[] {1, 256};
                            default ->
                                    throw new IOException(
                                            "Unknown written type while resolving divisions");
                        };
                int dots = 0;
                for (Node node = note.getFirstChild(); node != null; node = node.getNextSibling())
                    if (node instanceof Element child && child.getTagName().equals("dot")) dots++;
                if (dots > 8)
                    throw new IOException("Too many augmentation dots while resolving divisions");
                var numerator =
                        BigInteger.valueOf(base[0])
                                .multiply(BigInteger.valueOf((1L << (dots + 1)) - 1));
                var denominator =
                        BigInteger.valueOf(base[1]).multiply(BigInteger.valueOf(1L << dots));
                var candidate =
                        BigInteger.valueOf(units)
                                .multiply(denominator)
                                .divideAndRemainder(numerator);
                if (candidate[1].signum() != 0
                        || candidate[0].signum() <= 0
                        || candidate[0].bitLength() > 63)
                    throw new IOException("Missing divisions have no exact integral written grid");
                long value = candidate[0].longValue();
                if (grid != null && grid != value)
                    throw new IOException("Missing divisions have conflicting written grids");
                grid = value;
            }
            if (grid == null)
                throw new IOException("Missing divisions have no typed metric evidence");
            return grid;
        } catch (IOException error) {
            throw error;
        } catch (ArithmeticException | NumberFormatException error) {
            throw new IOException(
                    "Invalid written duration while resolving missing divisions", error);
        }
    }

    private static long positive(Element element, String tag) throws IOException {
        var child = direct(element, tag);
        if (child == null) throw new IOException("Missing written tuplet ratio");
        long value = Long.parseLong(child.getTextContent().trim());
        if (value <= 0) throw new IOException("Invalid written tuplet ratio");
        return value;
    }

    private static Element direct(Element element, String tag) {
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling())
            if (node instanceof Element child && child.getTagName().equals(tag)) return child;
        return null;
    }
}
