// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.io.IOException;
import java.security.MessageDigest;

/** One OCR engine's bounded raw-evidence cache. No admission or eligibility is cached. */
final class NativeDirectionOcrCache {
    interface Reader {
        OcrText full(byte[] gray, int width, int height) throws Exception;

        OcrText crop(byte[] gray, int width, int height, PaperDynamicWord.Crop crop, int scale)
                throws Exception;
    }

    private record CropKey(int left, int top, int right, int bottom, int scale) {}

    private record Read(OcrText text, Exception failure) {}

    private static final int MAX_ELIGIBLE_SEEDS = 32;
    private final Reader reader;
    private byte[] raster, digest;
    private int width, height;
    private boolean poisoned, fullAttempted;
    private OcrText full;
    private final Map<CropKey, Read> crops = new HashMap<>();

    NativeDirectionOcrCache(Reader reader) {
        this.reader = reader;
    }

    synchronized OcrText rawCrop(byte[] gray, int w, int h, PaperDynamicWord.Crop crop, int scale)
            throws Exception {
        if (reader == null
                || !prepare(gray, w, h)
                || crop == null
                || crop.left() < 0
                || crop.top() < 0
                || crop.right() > w
                || crop.bottom() > h
                || crop.right() <= crop.left()
                || crop.bottom() <= crop.top()
                || (scale != 2 && scale != 3))
            throw new IOException("Invalid immutable proof crop");
        var key = new CropKey(crop.left(), crop.top(), crop.right(), crop.bottom(), scale);
        var cached = crops.get(key);
        if (cached == null) {
            try {
                cached = new Read(reader.crop(gray, w, h, crop, scale), null);
            } catch (Exception failed) {
                cached = new Read(null, failed);
            }
            crops.put(key, cached);
        }
        if (!unchanged(gray) || cached.text() == null)
            throw new IOException("OCR proof crop unavailable", cached.failure());
        return cached.text();
    }

    synchronized List<PlayingTechniqueDetector.Word> directions(
            byte[] gray,
            int w,
            int h,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            List<PlayingTechniqueDetector.Word> ordinary,
            List<NativeDynamicGlyphs.Literal> strict) {
        if (reader == null || !prepare(gray, w, h)) return List.of();
        if (ordinary == null) ordinary = List.of();
        if (strict == null) strict = List.of();
        boolean literalSeed = ordinary.stream().anyMatch(NativeDirectionOcrCache::literal);
        if (strict.isEmpty() && !literalSeed) return List.of();
        if (!fullAttempted) {
            fullAttempted = true;
            try {
                full = reader.full(gray, w, h);
            } catch (Exception unavailable) {
                full = null;
            }
        }
        if (full == null || full.grouping() == OcrText.Grouping.UNKNOWN || !unchanged(gray))
            return List.of();
        var result = new LinkedHashSet<PlayingTechniqueDetector.Word>();
        for (var evidence :
                NativeEnglishDirectionEvidence.collect(
                        full, 1, 0, strict, staffs, measures, notes, gray, w, h))
            result.add(evidence.word());
        var indices = new ArrayList<Integer>();
        for (int i = 0; i < ordinary.size(); i++)
            if (literal(ordinary.get(i))
                    && potential(
                            full, ordinary.get(i), staffs, measures, notes, gray, w, h, result))
                indices.add(i);
        // Never choose a convenient subset from a page with excessive possible ownership.
        if (indices.size() <= MAX_ELIGIBLE_SEEDS)
            for (int index : indices) {
                var seed = ordinary.get(index);
                try {
                    // All current original ordinary competitors are retained. Only this exact index
                    // is reverified.
                    var proof =
                            PaperDynamicLiteralReproof.verify(
                                    ordinary,
                                    index,
                                    seed,
                                    (crop, scale) -> rawCrop(gray, w, h, crop, scale),
                                    gray,
                                    w,
                                    h);
                    if (proof != null)
                        for (var evidence :
                                NativeEnglishDirectionEvidence.collectAgreed(
                                        full,
                                        1,
                                        0,
                                        List.of(proof),
                                        staffs,
                                        measures,
                                        notes,
                                        gray,
                                        w,
                                        h)) result.add(evidence.word());
                } catch (Exception unavailable) {
                    /* Missing OCR abstains on this new direction; old recognition survives. */
                }
            }
        return unchanged(gray) ? List.copyOf(result) : List.of();
    }

    /** Staff-strip OCR cannot establish the new English role, even if an old collector set a role. */
    static List<PlayingTechniqueDetector.Word> legacyStaffStripWords(
            List<PlayingTechniqueDetector.Word> words) {
        return words.stream()
                .filter(w -> w != null && !EnglishCharacterOcrContext.character(w.text()))
                .toList();
    }

    private static boolean literal(PlayingTechniqueDetector.Word w) {
        return w != null && w.text() != null && w.text().matches("ppp|pp|p|mp|mf|f|ff|fff");
    }

    /** OCR scheduling only, never qualification: final actual detector/unique ownership is re-run. */
    private static boolean potential(
            OcrText text,
            PlayingTechniqueDetector.Word seed,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            byte[] gray,
            int w,
            int h,
            Set<PlayingTechniqueDetector.Word> already) {
        if (!ScoreDynamicsDetector.containsInk(seed, gray, w, h)) return false;
        for (var block : text.blocks())
            for (var line : block.lines()) {
                if (!EnglishCharacterOcrContext.character(line.text())
                        || EnglishCharacterOcrContext.ownedByProse(text, block, line)) continue;
                var b = line.box();
                if (b == null || b.width() <= 0 || b.height() <= 0) continue;
                var word =
                        new PlayingTechniqueDetector.Word(
                                line.text(),
                                b.left / (float) w,
                                b.top / (float) h,
                                b.right / (float) w,
                                b.bottom / (float) h);
                if (already.stream().anyMatch(old -> samePrinted(old, word))
                        || !ScoreDynamicsDetector.containsInk(word, gray, w, h)) continue;
                for (var staff : staffs) {
                    float gap = staff.gap(), cy = b.exactCenterY();
                    if (gap < 4
                            || cy < staff.top() - 5.5f * gap
                            || cy > staff.top() - .25f * gap
                            || seed.top() * h < staff.bottom() + gap * .25f
                            || seed.bottom() * h > staff.bottom() + gap * 5.5f) continue;
                    for (var n : notes) {
                        if (n.staffIndex() != staff.index()
                                || n.staffCount() != staff.count()
                                || n.measureIndex() < 0
                                || n.measureIndex() >= measures.size()
                                || n.pageY() * h < staff.top() - 2 * gap
                                || n.pageY() * h > staff.bottom() + 3 * gap) continue;
                        var region = measures.get(n.measureIndex());
                        float left = b.left,
                                x =
                                        (region.left()
                                                        + n.positionInMeasure()
                                                                * (region.right() - region.left()))
                                                * w;
                        if (left >= region.left() * w - gap * .6f
                                && left <= region.right() * w
                                && Math.abs(x - left) <= gap * 2.5f
                                && Math.abs((seed.left() + seed.right()) * w * .5f - x)
                                        <= gap * 1.5f) return true;
                    }
                }
            }
        return false;
    }

    private static boolean samePrinted(
            PlayingTechniqueDetector.Word a, PlayingTechniqueDetector.Word b) {
        return Objects.equals(a.text(), b.text())
                && Float.floatToIntBits(a.left()) == Float.floatToIntBits(b.left())
                && Float.floatToIntBits(a.top()) == Float.floatToIntBits(b.top())
                && Float.floatToIntBits(a.right()) == Float.floatToIntBits(b.right())
                && Float.floatToIntBits(a.bottom()) == Float.floatToIntBits(b.bottom());
    }

    private boolean prepare(byte[] gray, int w, int h) {
        if (gray == null || w <= 0 || h <= 0 || gray.length != (long) w * h) return false;
        var current = hash(gray);
        if (raster == gray && width == w && height == h) {
            if (poisoned) return false;
            if (!Arrays.equals(digest, current)) {
                poisoned = true;
                return false;
            }
            return true;
        }
        if (!poisoned && width == w && height == h && Arrays.equals(digest, current)) {
            raster = gray;
            return true;
        }
        raster = gray;
        width = w;
        height = h;
        digest = current;
        poisoned = false;
        fullAttempted = false;
        full = null;
        crops.clear();
        return true;
    }

    private boolean unchanged(byte[] gray) {
        if (poisoned) return false;
        if (!Arrays.equals(digest, hash(gray))) {
            poisoned = true;
            return false;
        }
        return true;
    }

    private static byte[] hash(byte[] gray) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(gray);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
