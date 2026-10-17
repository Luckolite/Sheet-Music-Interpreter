// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Objects;

/** Immutable, platform-independent OCR evidence in original input-pixel coordinates.
 * Preserve ordering, punctuation, duplicate tokens and missing boxes. This is
 * evidence, not a normalized interpretation; absent symbols must not be invented.
 */
public record OcrText(String text, List<Block> blocks, Grouping grouping) {
    public enum Grouping {
        UNKNOWN,
        ORIGINAL_BLOCKS,
        DETECTED_LINES
    }

    public OcrText(String text, List<Block> blocks) {
        this(text, blocks, Grouping.UNKNOWN);
    }

    public OcrText {
        Objects.requireNonNull(text);
        Objects.requireNonNull(grouping);
        blocks = List.copyOf(blocks);
    }

    public String getText() {
        return text;
    }

    public List<Block> getTextBlocks() {
        return blocks;
    }

    public static final class Box {
        public final int left, top, right, bottom;

        public Box(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public int width() {
            return right - left;
        }

        public int height() {
            return bottom - top;
        }

        public float exactCenterX() {
            return ((float) left + right) / 2;
        }

        public float exactCenterY() {
            return ((float) top + bottom) / 2;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Box b
                    && left == b.left
                    && top == b.top
                    && right == b.right
                    && bottom == b.bottom;
        }

        @Override
        public int hashCode() {
            return Objects.hash(left, top, right, bottom);
        }

        @Override
        public String toString() {
            return "Box(" + left + "," + top + "," + right + "," + bottom + ")";
        }
    }

    public record Symbol(String text, Box box) {
        public Symbol {
            Objects.requireNonNull(text);
        }

        public String getText() {
            return text;
        }

        public Box getBoundingBox() {
            return box;
        }
    }

    public record Element(String text, Box box, List<Symbol> symbols) {
        public Element {
            Objects.requireNonNull(text);
            symbols = List.copyOf(symbols);
        }

        public String getText() {
            return text;
        }

        public Box getBoundingBox() {
            return box;
        }

        public List<Symbol> getSymbols() {
            return symbols;
        }
    }

    public record Line(String text, Box box, List<Element> elements) {
        public Line {
            Objects.requireNonNull(text);
            elements = List.copyOf(elements);
        }

        public String getText() {
            return text;
        }

        public Box getBoundingBox() {
            return box;
        }

        public List<Element> getElements() {
            return elements;
        }
    }

    public record Block(String text, Box box, List<Line> lines) {
        public Block {
            Objects.requireNonNull(text);
            lines = List.copyOf(lines);
        }

        public String getText() {
            return text;
        }

        public Box getBoundingBox() {
            return box;
        }

        public List<Line> getLines() {
            return lines;
        }
    }
}
