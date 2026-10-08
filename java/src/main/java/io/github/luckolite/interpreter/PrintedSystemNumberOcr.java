// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Small system-number crops follow independently verified printed five-line groups. */
final class PrintedSystemNumberOcr {
    record Crop(int left, int top, int right, int bottom, int systemMeasure) {}

    record Raster(byte[] pixels, int width, int height) {}

    record Reading(int value, int systemMeasure) {}

    interface Reader {
        String read(byte[] gray, int width, int height) throws Exception;
    }

    private PrintedSystemNumberOcr() {}

    static List<Crop> candidates(
            byte[] labels, byte[] gray, int width, int height, List<MeasureRegion> measures) {
        if (labels == null
                || gray == null
                || width <= 0
                || height <= 0
                || (long) width * height != labels.length
                || gray.length != labels.length
                || measures == null
                || measures.isEmpty()) return List.of();
        List<Crop> result = new ArrayList<>();
        for (var seed : RegionalStaffSeeds.detect(labels, gray, width, height)) {
            var track = RegionalStaffSeeds.track(labels, gray, width, height, seed);
            if (track == null || !track.verified()) continue;
            int first = -1;
            for (int i = 0; i < measures.size(); i++) {
                var measure = measures.get(i);
                float x = (measure.left() + measure.right()) * width * .5f;
                float[] frame = track.at(x);
                float middle = frame[0] - frame[1] * 2;
                if (middle < measure.top() * height || middle > measure.bottom() * height) continue;
                if (first < 0 || measure.left() < measures.get(first).left()) first = i;
            }
            if (first < 0) continue;
            float gap = track.at(measures.get(first).left() * width)[1];
            int left = Math.max(0, Math.round(measures.get(first).left() * width - gap * 7.5f));
            int right =
                    Math.min(width, Math.round(measures.get(first).left() * width - gap * 1.6f));
            float[] frame = track.at((left + right) * .5f);
            float localTop = frame[0] - frame[1] * 4;
            int top = Math.max(0, Math.round(localTop - gap * 4.7f));
            int bottom = Math.min(height, Math.round(localTop - gap * 1.5f));
            if (right - left < 8 || bottom - top < 8) continue;
            Crop crop = new Crop(left, top, right, bottom, first);
            // A connected system has one numbering anchor above its highest staff.
            Crop previous = null;
            for (Crop other : result) if (other.systemMeasure == first) previous = other;
            if (previous == null) result.add(crop);
            else if (crop.top < previous.top) {
                result.remove(previous);
                result.add(crop);
            }
        }
        result.sort(Comparator.comparingInt(Crop::top));
        return List.copyOf(result);
    }

    static Raster raster(byte[] gray, int width, int height, Crop crop, int scale) {
        if (gray == null
                || width <= 0
                || height <= 0
                || (long) width * height != gray.length
                || crop == null
                || crop.left < 0
                || crop.top < 0
                || crop.right > width
                || crop.bottom > height
                || crop.right <= crop.left
                || crop.bottom <= crop.top
                || scale < 1
                || scale > 7) throw new IllegalArgumentException("Invalid number crop");
        int w = crop.right - crop.left, h = crop.bottom - crop.top, border = 20;
        int outWidth = w * scale + border * 2, outHeight = h * scale + border * 2;
        if ((long) outWidth * outHeight > 2_000_000)
            throw new IllegalArgumentException("Number crop too large");
        byte[] pixels = new byte[outWidth * outHeight];
        Arrays.fill(pixels, (byte) 255);
        for (int y = 0; y < h * scale; y++)
            for (int x = 0; x < w * scale; x++)
                pixels[(y + border) * outWidth + x + border] =
                        gray[(crop.top + y / scale) * width + crop.left + x / scale];
        return new Raster(pixels, outWidth, outHeight);
    }

    static int agreedNumber(List<String> readings) {
        if (readings == null) return 0;
        Map<Integer, Integer> counts = new HashMap<>();
        for (String text : readings) {
            if (text == null || !text.trim().matches("[0-9]{1,3}")) continue;
            int value = Integer.parseInt(text.trim());
            if (value > 0) counts.merge(value, 1, Integer::sum);
        }
        int best = 0, votes = 1;
        boolean tied = false;
        for (var entry : counts.entrySet()) {
            if (entry.getValue() > votes) {
                best = entry.getKey();
                votes = entry.getValue();
                tied = false;
            } else if (entry.getValue() == votes) tied = true;
        }
        return tied ? 0 : best;
    }

    static List<Reading> read(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            Reader reader)
            throws Exception {
        if (reader == null) return List.of();
        List<Reading> result = new ArrayList<>();
        for (Crop crop : candidates(labels, gray, width, height, measures)) {
            List<String> readings = new ArrayList<>();
            for (int scale : new int[] {3, 5, 7}) {
                Raster raster = raster(gray, width, height, crop, scale);
                readings.add(reader.read(raster.pixels, raster.width, raster.height));
            }
            int value = agreedNumber(readings);
            if (value != 0) result.add(new Reading(value, crop.systemMeasure));
        }
        return List.copyOf(result);
    }

    /** Independent physical system starts must agree with actual preceding written bars. */
    static int firstMeasureNumber(List<Reading> readings) {
        if (readings == null) return 0;
        Map<Integer, Map<Integer, Integer>> votes = new HashMap<>();
        for (Reading reading : readings) {
            if (reading == null
                    || reading.value <= 0
                    || reading.value > 999
                    || reading.systemMeasure < 0) continue;
            int first = reading.value - reading.systemMeasure;
            if (first < 1) continue;
            votes.computeIfAbsent(first, ignored -> new HashMap<>())
                    .put(reading.systemMeasure, reading.value);
        }
        int best = 0, support = 1;
        boolean tied = false;
        for (var entry : votes.entrySet()) {
            int count = entry.getValue().size();
            if (count > support) {
                best = entry.getKey();
                support = count;
                tied = false;
            } else if (count == support) tied = true;
        }
        return tied ? 0 : best;
    }

    static int firstMeasureNumber(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            Reader reader)
            throws Exception {
        return firstMeasureNumber(read(labels, gray, width, height, measures, reader));
    }
}
