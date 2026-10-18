// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.security.MessageDigest;
import java.util.*;

/** Invocation-local acquisition identity; never a new OCR schedule or a word protocol field. */
final class DirectionWordProvenance {
    private DirectionWordProvenance() {}

    static final class Scope {
        final byte[] raster;
        final int width, height;
        final long afterFullEpoch;
        final byte[] digest;
        final List<PlayingTechniqueDetector.Word> ordinary;

        Scope(
                byte[] raster,
                int width,
                int height,
                long afterFullEpoch,
                List<PlayingTechniqueDetector.Word> ordinary) {
            this.raster = raster;
            this.width = width;
            this.height = height;
            this.afterFullEpoch = afterFullEpoch;
            this.digest = hash(raster);
            this.ordinary = List.copyOf(ordinary);
        }

        boolean current(byte[] gray, int w, int h) {
            return raster != null
                    && raster == gray
                    && width == w
                    && height == h
                    && w > 0
                    && h > 0
                    && gray.length == (long) w * h
                    && Arrays.equals(digest, hash(gray));
        }
    }

    record Origin(
            PlayingTechniqueDetector.Word word,
            OcrText text,
            OcrText.Line parent,
            OcrText.Element element,
            int left,
            int top,
            int right,
            int bottom,
            int scale) {}

    record Acquisition(
            List<PlayingTechniqueDetector.Word> words, List<Origin> origins, Scope scope) {
        Acquisition {
            words = List.copyOf(words);
            origins = List.copyOf(origins);
        }
    }

    /** Existing words survive by identity unless one complete actual parent uniquely proves a cut. */
    static List<PlayingTechniqueDetector.Word> reconcile(
            List<PlayingTechniqueDetector.Word> input,
            Acquisition acquired,
            OcrText full,
            byte[] gray,
            int width,
            int height) {
        if (acquired == null
                || full == null
                || full.grouping() == OcrText.Grouping.UNKNOWN
                || !acquired.scope().current(gray, width, height)) return input;
        var removed =
                Collections.newSetFromMap(
                        new IdentityHashMap<PlayingTechniqueDetector.Word, Boolean>());
        for (var origin : acquired.origins()) {
            if (containsIdentity(input, origin.word())
                    && !containsIdentity(acquired.scope().ordinary, origin.word())
                    && uniqueOwner(origin, full, acquired.scope()) != null)
                removed.add(origin.word());
        }
        if (removed.isEmpty()) return input;
        var result = new ArrayList<PlayingTechniqueDetector.Word>();
        for (var word : input) if (!removed.contains(word)) result.add(word);
        return List.copyOf(result);
    }

    private static PlayingTechniqueDetector.Word uniqueOwner(Origin o, OcrText full, Scope s) {
        var e = o.element();
        var p = o.parent();
        var word = o.word();
        if (o.text() == null
                || o.text().grouping() == OcrText.Grouping.UNKNOWN
                || e == null
                || p == null
                || word == null
                || o.left() < 0
                || o.top() < 0
                || o.right() > s.width
                || o.bottom() > s.height
                || o.left() >= o.right()
                || o.top() >= o.bottom()
                || (o.scale() != 2 && o.scale() != 3)
                || !valid(e.box())
                || !valid(p.box())
                || !contains(p.box(), e.box())
                || !sameText(word.text(), e.text())
                || !sameText(e.text(), p.text())
                || !containsIdentity(p.elements(), e)) return null;
        boolean parentPresent = false;
        for (var block : o.text().blocks())
            if (containsIdentity(block.lines(), p)) parentPresent = true;
        if (!parentPresent) return null;
        int rw = (o.right() - o.left()) * o.scale(), rh = (o.bottom() - o.top()) * o.scale();
        if (!contains(new OcrText.Box(0, 0, rw, rh), e.box()) || !transformed(o, s)) return null;
        PlayingTechniqueDetector.Word owner = null;
        int matches = 0;
        var seenParents = new IdentityHashMap<OcrText.Line, Set<OcrText.Element>>();
        for (var block : full.blocks())
            for (var line : block.lines()) {
                var seen =
                        seenParents.computeIfAbsent(
                                line, unused -> Collections.newSetFromMap(new IdentityHashMap<>()));
                for (var element : line.elements()) {
                    if (!seen.add(element)
                            || !valid(line.box())
                            || !valid(element.box())
                            || !contains(line.box(), element.box())
                            || !contains(new OcrText.Box(0, 0, s.width, s.height), line.box())
                            || !sameText(element.text(), word.text())
                            || !sameText(line.text(), element.text())
                            || !projectionMatches(element.box(), o)) continue;
                    var owners =
                            Collections.newSetFromMap(
                                    new IdentityHashMap<PlayingTechniqueDetector.Word, Boolean>());
                    for (var ordinary : s.ordinary) {
                        var envelope = envelope(ordinary, s.width, s.height);
                        if (sameText(ordinary.text(), element.text())
                                && envelope != null
                                && contains(envelope, line.box())) owners.add(ordinary);
                    }
                    if (owners.size() != 1) return null;
                    owner = owners.iterator().next();
                    if (++matches != 1) return null;
                }
            }
        return matches == 1 ? owner : null;
    }

    private static boolean transformed(Origin o, Scope s) {
        var b = o.element().box();
        var w = o.word();
        return bits(
                        w.left(),
                        o.left() == 0
                                ? b.left / (float) (o.scale() * s.width)
                                : (o.left() + b.left / (float) o.scale()) / s.width)
                && bits(w.top(), (o.top() + b.top / (float) o.scale()) / s.height)
                && bits(
                        w.right(),
                        o.left() == 0
                                ? b.right / (float) (o.scale() * s.width)
                                : (o.left() + b.right / (float) o.scale()) / s.width)
                && bits(w.bottom(), (o.top() + b.bottom / (float) o.scale()) / s.height);
    }

    private static boolean projectionMatches(OcrText.Box full, Origin o) {
        int l = Math.max(full.left, o.left()), t = Math.max(full.top, o.top());
        int r = Math.min(full.right, o.right()), b = Math.min(full.bottom, o.bottom());
        if (l >= r
                || t >= b
                || (l == full.left && t == full.top && r == full.right && b == full.bottom))
            return false;
        var e = o.element().box();
        int scale = o.scale();
        if (e.left != (l - o.left()) * scale
                || e.top != (t - o.top()) * scale
                || e.right != (r - o.left()) * scale
                || e.bottom != (b - o.top()) * scale) return false;
        return (full.left < o.left() && e.left == 0)
                || (full.top < o.top() && e.top == 0)
                || (full.right > o.right() && e.right == (o.right() - o.left()) * scale)
                || (full.bottom > o.bottom() && e.bottom == (o.bottom() - o.top()) * scale);
    }

    /** Outward pixel support of actual normalized F32 Word bounds, without an epsilon. */
    private static OcrText.Box envelope(PlayingTechniqueDetector.Word w, int width, int height) {
        if (w == null
                || !Float.isFinite(w.left())
                || !Float.isFinite(w.top())
                || !Float.isFinite(w.right())
                || !Float.isFinite(w.bottom())
                || w.left() < 0
                || w.top() < 0
                || w.right() > 1
                || w.bottom() > 1
                || w.left() >= w.right()
                || w.top() >= w.bottom()) return null;
        return new OcrText.Box(
                (int) Math.floor(w.left() * width),
                (int) Math.floor(w.top() * height),
                (int) Math.ceil(w.right() * width),
                (int) Math.ceil(w.bottom() * height));
    }

    private static boolean bits(float a, float b) {
        return Float.floatToRawIntBits(a) == Float.floatToRawIntBits(b);
    }

    private static boolean valid(OcrText.Box b) {
        return b != null && b.left < b.right && b.top < b.bottom;
    }

    private static boolean contains(OcrText.Box a, OcrText.Box b) {
        return a.left <= b.left && a.top <= b.top && a.right >= b.right && a.bottom >= b.bottom;
    }

    private static boolean sameText(String a, String b) {
        return a != null && b != null && !a.isBlank() && normalize(a).equals(normalize(b));
    }

    private static String normalize(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static boolean containsIdentity(List<?> values, Object target) {
        for (var v : values) if (v == target) return true;
        return false;
    }

    private static byte[] hash(byte[] gray) {
        if (gray == null) return new byte[0];
        try {
            return MessageDigest.getInstance("SHA-256").digest(gray);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
