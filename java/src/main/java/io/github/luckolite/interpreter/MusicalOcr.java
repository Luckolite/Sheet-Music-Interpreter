// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Optional staff-local musical OCR, using caller-owned inference and a supplied meter font.
 * The caller retains responsibility for closing its inference runtime. No models or runtime are loaded here. */
public final class MusicalOcr {
    private final PortableOcr reader;
    private final NativeDynamicGlyphs glyphs;
    private final MeterFontMatcher meterFont;
    private static final boolean TRACE = "1".equals(System.getenv("NATIVE_MUSICAL_OCR_TRACE"));

    private record MeterReading(String text, int votes) {}

    public MusicalOcr(PortableOcr.Inference inference, MeterFontMatcher meterFont)
            throws java.io.IOException {
        reader = new PortableOcr(Objects.requireNonNull(inference));
        glyphs = new NativeDynamicGlyphs();
        this.meterFont = Objects.requireNonNull(meterFont);
    }

    List<ScoreMeterChange> meters(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes)
            throws Exception {
        var readings = new TreeMap<Integer, Map<String, Integer>>();
        var decisive = new HashSet<Integer>();
        for (var change :
                MeterChangeDetector.commonTimeReadings(
                        labels, gray, width, height, measures, notes)) {
            readings.computeIfAbsent(change.measureIndex(), ignored -> new HashMap<>())
                    .merge(change.numerator() + "/" + change.denominator(), 1, Integer::sum);
            decisive.add(change.measureIndex());
        }
        var candidates =
                MeterChangeDetector.candidates(labels, gray, width, height, measures, notes);
        if (TRACE) System.err.println("meter candidates=" + candidates.size());
        for (var crop : candidates) {
            int measure = MeterChangeDetector.followingMeasure(crop, width, height, measures);
            if (measure < 0
                    || !MeterChangeDetector.precedesNotes(
                            crop, labels, gray, width, height, measure, measures, notes)) continue;
            MeterReading reading = meter(gray, width, height, crop);
            if (TRACE)
                System.err.println(
                        "meter crop=" + crop + " measure=" + measure + " reading=" + reading);
            if (reading.text().isBlank()) continue;
            readings.computeIfAbsent(measure, ignored -> new HashMap<>())
                    .merge(reading.text(), 1, Integer::sum);
            if (reading.votes() >= 2) decisive.add(measure);
        }
        var found = new ArrayList<ScoreMeterChange>();
        for (var entry : readings.entrySet()) {
            if (entry.getValue().size() != 1) continue;
            var choice = entry.getValue().entrySet().iterator().next();
            // Either repeated OCR scales or two physically separate staff copies must agree.
            if (choice.getValue() < 2 && !decisive.contains(entry.getKey())) continue;
            String[] parts = choice.getKey().split("/");
            found.add(
                    new ScoreMeterChange(
                            entry.getKey(),
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1])));
        }
        return MeterChangeDetector.filterWholeNoteOcrReadings(found, notes, measures.size());
    }

    private MeterReading meter(byte[] gray, int width, int height, MeterChangeDetector.Crop crop)
            throws Exception {
        int middle = Math.round(crop.firstLine() + crop.gap() * 2);
        int top = Math.max(0, crop.top()), bottom = Math.min(height, crop.bottom());
        if (middle <= top + 2 || middle >= bottom - 2) return new MeterReading("", 0);
        int left = Math.max(0, crop.left()), right = Math.min(width, crop.right());
        int cropWidth = right - left, cropHeight = bottom - top;
        if (cropWidth < 2 || cropHeight < 4) return new MeterReading("", 0);
        int[] cleaned = new int[cropWidth * cropHeight];
        for (int y = 0; y < cropHeight; y++)
            for (int x = 0; x < cropWidth; x++) {
                int value = gray[(top + y) * width + left + x] & 255;
                cleaned[y * cropWidth + x] = 0xff000000 | value << 16 | value << 8 | value;
            }
        int[] raw = cleaned.clone();
        MeterCropRaster.prepare(
                cleaned, cropWidth, cropHeight, top, crop.firstLine(), crop.gap(), false);
        var readings = new ArrayList<String>();
        var upperEvidence = new HashSet<String>();
        var lowerEvidence = new HashSet<String>();
        for (int scale : new int[] {3, 4, 5}) {
            String numerator = digits(cleaned, cropWidth, cropHeight, 0, middle - top, scale);
            String denominator =
                    digits(cleaned, cropWidth, cropHeight, middle - top, cropHeight, scale);
            if (!numerator.isBlank()) upperEvidence.add(numerator);
            if (!denominator.isBlank()) lowerEvidence.add(denominator);
            if (numerator.matches("[0-9]{1,2}") && denominator.matches("[0-9]{1,2}"))
                readings.add(numerator + "/" + denominator);
            if (TRACE)
                System.err.println(
                        "meter scale=" + scale + " upper=" + numerator + " lower=" + denominator);
        }
        String consensus = MeterOcrEvidence.consensus(readings);
        if (!consensus.isBlank()) return new MeterReading(consensus, readings.size());
        {
            var font =
                    meterFont.read(
                            cleaned,
                            cropWidth,
                            cropHeight,
                            middle - top,
                            Math.round(crop.firstLine()) - top,
                            crop.gap());
            if (TRACE) System.err.println("meter font=" + font);
            String supported =
                    MusicalOcrEvidence.fontMeter(
                            font.text(),
                            font.topScore(),
                            font.bottomScore(),
                            upperEvidence,
                            lowerEvidence);
            if (!supported.isBlank()) return new MeterReading(supported, 2);
        }
        String single = MeterOcrEvidence.singleReading(readings);
        if (!single.isBlank()) return new MeterReading(single, 1);
        // Invalid numerals can be a context-dependent text OCR error. Require agreeing
        // reflowed readings and a separate staff copy before using this fallback.
        if (readings.size() == 1 && !lowerEvidence.isEmpty()) {
            MeterCropRaster.prepare(
                    raw, cropWidth, cropHeight, top, crop.firstLine(), crop.gap(), true);
            String reflowed = reflowedMeter(raw, cropWidth, cropHeight, middle - top);
            if (!reflowed.isBlank()) return new MeterReading(reflowed, 1);
        }
        return new MeterReading("", 0);
    }

    private String reflowedMeter(int[] pixels, int width, int height, int middle) throws Exception {
        int partHeight = Math.max(middle, height - middle), margin = 18, slash = 30;
        int slot = width + 28, outWidth = slot * 2 + slash, outHeight = partHeight + margin * 2;
        var image =
                new java.awt.image.BufferedImage(
                        outWidth, outHeight, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, outWidth, outHeight);
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                    image.setRGB(
                            (y < middle ? 14 : slot + slash + 14) + x,
                            margin + (y < middle ? y : y - middle),
                            pixels[y * width + x]);
            graphics.setColor(java.awt.Color.BLACK);
            graphics.setStroke(
                    new java.awt.BasicStroke(
                            Math.max(3, outHeight * .045f),
                            java.awt.BasicStroke.CAP_ROUND,
                            java.awt.BasicStroke.JOIN_ROUND));
            graphics.drawLine(
                    Math.round(slot + slash * .82f),
                    Math.round(outHeight * .18f),
                    Math.round(slot + slash * .18f),
                    Math.round(outHeight * .82f));
        } finally {
            graphics.dispose();
        }
        int[] arranged = image.getRGB(0, 0, outWidth, outHeight, null, 0, outWidth);
        var readings = new ArrayList<String>();
        for (int scale : new int[] {2, 3, 4}) {
            var text = readPixels(arranged, outWidth, outHeight, 0, 0, outWidth, outHeight, scale);
            var tokens = new ArrayList<MeterOcrEvidence.Token>();
            for (var block : text.blocks())
                for (var line : block.lines())
                    for (var element : line.elements()) {
                        var box = element.box();
                        if (box != null)
                            tokens.add(
                                    new MeterOcrEvidence.Token(
                                            element.text(),
                                            box.left,
                                            box.top,
                                            box.right,
                                            box.bottom));
                    }
            readings.add(
                    MeterOcrEvidence.reflowedFraction(
                            tokens, slot * scale, (slot + slash) * scale));
        }
        return MeterOcrEvidence.consensus(readings);
    }

    private String digits(int[] pixels, int width, int height, int top, int bottom, int scale)
            throws Exception {
        OcrText text = readPixels(pixels, width, height, 0, top, width, bottom, scale);
        var values = new HashSet<String>();
        for (var block : text.blocks())
            for (var line : block.lines()) {
                String value = line.text().trim();
                if (value.matches("[0-9]{1,2}")) values.add(value);
            }
        return values.size() == 1 ? values.iterator().next() : "";
    }

    List<PlayingTechniqueDetector.Word> dynamics(
            byte[] gray, int width, int height, List<PlayingTechniqueDetector.Staff> staffs)
            throws Exception {
        var words = new ArrayList<PlayingTechniqueDetector.Word>();
        for (var staff : staffs)
            for (boolean below : new boolean[] {true, false}) {
                int top =
                        Math.max(
                                0,
                                Math.round(
                                        below
                                                ? staff.bottom() + staff.gap() * .25f
                                                : staff.top() - staff.gap() * 5.5f));
                int bottom =
                        Math.min(
                                height,
                                Math.round(
                                        below
                                                ? staff.bottom() + staff.gap() * 5.5f
                                                : staff.top() - staff.gap() * .25f));
                if (bottom <= top) continue;
                int scale = Math.max(2, Math.min(3, Math.round(30f / staff.gap())));
                OcrText text = readCrop(gray, width, height, 0, top, width, bottom, scale);
                for (var block : text.blocks())
                    for (var line : block.lines())
                        for (var element : line.elements()) {
                            String value = element.text();
                            var box = element.box();
                            if (box == null) continue;
                            var word =
                                    new PlayingTechniqueDetector.Word(
                                            value,
                                            box.left / (float) (scale * width),
                                            (top + box.top / (float) scale) / height,
                                            box.right / (float) (scale * width),
                                            (top + box.bottom / (float) scale) / height);
                            words.addAll(
                                    ScoreDynamicsDetector.ocrWords(
                                            line.text(), word, gray, width, height));
                            if (!ExpressiveDirectionText.parse(value).isEmpty()) words.add(word);
                        }
            }
        if (TRACE) System.err.println("dynamic words=" + words.size());
        var result = new ArrayList<>(glyphs.recognize(gray, width, height, staffs, words));
        for (var word : words)
            if (!ExpressiveDirectionText.parse(word.text()).isEmpty() && !result.contains(word))
                result.add(word);
        return List.copyOf(result);
    }

    /** Match Android's bounded raw/staff-cleared text passes for tr and port.
     * Ownership and raw-ink checks remain in PortableNoteOrnaments. */
    List<PlayingTechniqueDetector.Word> ornamentWords(
            byte[] gray, int width, int height, List<PlayingTechniqueDetector.Staff> staffs)
            throws Exception {
        var words = new ArrayList<PlayingTechniqueDetector.Word>();
        byte[] clean =
                NoteSlideDetector.removeStaffLines(
                        gray,
                        width,
                        height,
                        staffs.stream()
                                .map(s -> new NoteSlideDetector.Staff(s.top(), s.bottom(), s.gap()))
                                .toList());
        for (var staff : staffs)
            for (boolean inside : new boolean[] {false, true}) {
                int top =
                        Math.max(0, Math.round(staff.top() - staff.gap() * (inside ? .5f : 5.5f)));
                int bottom =
                        Math.min(
                                height,
                                Math.round(staff.bottom() + staff.gap() * (inside ? .25f : 1)));
                if (bottom <= top) continue;
                int scale = Math.max(2, Math.min(3, Math.round(30f / staff.gap())));
                for (byte[] view : new byte[][] {gray, clean}) {
                    OcrText text = readCrop(view, width, height, 0, top, width, bottom, scale);
                    for (var block : text.blocks())
                        for (var line : block.lines())
                            for (var element : line.elements()) {
                                String token = MusicalOcrEvidence.ornamentToken(element.text());
                                if (token.isEmpty()) continue;
                                var box = element.box();
                                if (box == null) continue;
                                var word =
                                        new PlayingTechniqueDetector.Word(
                                                token,
                                                box.left / (float) (scale * width),
                                                (top + box.top / (float) scale) / height,
                                                box.right / (float) (scale * width),
                                                (top + box.bottom / (float) scale) / height);
                                if (words.stream()
                                        .noneMatch(
                                                old ->
                                                        old.text().equals(word.text())
                                                                && Math.abs(
                                                                                        old.left()
                                                                                                - word
                                                                                                        .left())
                                                                                * width
                                                                        < staff.gap() * .5f
                                                                && Math.abs(old.top() - word.top())
                                                                                * height
                                                                        < staff.gap() * .5f))
                                    words.add(word);
                            }
                }
            }
        return List.copyOf(words);
    }

    /** Supplement original dynamic words with full-body, two-scale literal agreement. */
    List<PlayingTechniqueDetector.Word> dynamics(
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes)
            throws Exception {
        return supplementDynamics(
                gray,
                width,
                height,
                staffs,
                measures,
                notes,
                dynamics(gray, width, height, staffs));
    }

    /** Original whole-word claims remain authoritative after the glyph refinement pass. */
    List<PlayingTechniqueDetector.Word> supplementDynamics(
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,
            List<PlayingTechniqueDetector.Word> accepted)
            throws Exception {
        var words = new ArrayList<>(accepted);
        for (var proposal :
                ScoreDynamicsDetector.paperSymbolBoxes(
                        gray, staffs, measures, notes, width, height)) {
            if (!PaperDynamicWord.unclaimed(proposal, words)) continue;
            var crop = PaperDynamicWord.crop(proposal, width, height);
            if (crop == null) continue;
            var twice =
                    readCrop(
                            gray,
                            width,
                            height,
                            crop.left(),
                            crop.top(),
                            crop.right(),
                            crop.bottom(),
                            2);
            var thrice =
                    readCrop(
                            gray,
                            width,
                            height,
                            crop.left(),
                            crop.top(),
                            crop.right(),
                            crop.bottom(),
                            3);
            var agreed =
                    PaperDynamicWord.agree(proposal, twice, thrice, crop, width, height, words);
            if (agreed != null && ScoreDynamicsDetector.containsInk(agreed, gray, width, height))
                words.add(agreed);
        }
        return List.copyOf(words);
    }

    List<PlayingTechniqueDetector.Word> crossRowPortWords(
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes)
            throws Exception {
        var words = new ArrayList<PlayingTechniqueDetector.Word>();
        for (var r : CrossRowPortamento.regions(gray, width, height, staffs, measures, notes)) {
            var twice = readCrop(gray, width, height, r.left(), r.top(), r.right(), r.bottom(), 2);
            var thrice = readCrop(gray, width, height, r.left(), r.top(), r.right(), r.bottom(), 3);
            var word = CrossRowPortamento.consensus(twice, thrice, r, width, height);
            if (word != null) words.add(word);
        }
        return List.copyOf(words);
    }

    /** Read complete playing words in bounded, independently supported local staff strips. */
    List<PlayingTechniqueDetector.Word> techniques(
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes)
            throws Exception {
        var words = new ArrayList<PlayingTechniqueDetector.Word>();
        for (var region : TechniqueTextRegions.above(staffs, measures, notes, width, height)) {
            var first =
                    TechniqueTextRegions.readings(
                            readCrop(
                                    gray,
                                    width,
                                    height,
                                    region.left(),
                                    region.top(),
                                    region.right(),
                                    region.bottom(),
                                    1),
                            region,
                            1,
                            width,
                            height);
            if (first.isEmpty()) continue;
            var second =
                    TechniqueTextRegions.readings(
                            readCrop(
                                    gray,
                                    width,
                                    height,
                                    region.left(),
                                    region.top(),
                                    region.right(),
                                    region.bottom(),
                                    2),
                            region,
                            2,
                            width,
                            height);
            words.addAll(
                    TechniqueTextRegions.consensus(
                            first, second, region.staff().gap(), width, height));
        }
        return List.copyOf(words);
    }

    private OcrText readCrop(
            byte[] gray, int width, int height, int left, int top, int right, int bottom, int scale)
            throws Exception {
        left = Math.max(0, left);
        top = Math.max(0, top);
        right = Math.min(width, right);
        bottom = Math.min(height, bottom);
        if (right <= left || bottom <= top) return new OcrText("", List.of());
        int cropWidth = right - left, cropHeight = bottom - top;
        int[] pixels = new int[cropWidth * cropHeight];
        for (int y = 0; y < cropHeight; y++)
            for (int x = 0; x < cropWidth; x++) {
                int value = gray[(top + y) * width + left + x] & 255;
                pixels[y * cropWidth + x] = 0xff000000 | value << 16 | value << 8 | value;
            }
        return readPixels(pixels, cropWidth, cropHeight, 0, 0, cropWidth, cropHeight, scale);
    }

    private OcrText readPixels(
            int[] source,
            int width,
            int height,
            int left,
            int top,
            int right,
            int bottom,
            int scale)
            throws Exception {
        left = Math.max(0, left);
        top = Math.max(0, top);
        right = Math.min(width, right);
        bottom = Math.min(height, bottom);
        if (right <= left || bottom <= top) return new OcrText("", List.of());
        int outWidth = (right - left) * scale, outHeight = (bottom - top) * scale;
        if ((long) outWidth * outHeight > 20_000_000) return new OcrText("", List.of());
        int[] pixels = new int[outWidth * outHeight];
        for (int y = 0; y < outHeight; y++)
            for (int x = 0; x < outWidth; x++)
                pixels[y * outWidth + x] = source[(top + y / scale) * width + left + x / scale];
        return reader.read(pixels, outWidth, outHeight);
    }
}
