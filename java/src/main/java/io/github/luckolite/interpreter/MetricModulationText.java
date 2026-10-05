// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** An equality preserves pulse duration: new quarter BPM = old quarter BPM * right / left. */
public final class MetricModulationText {
    private static final String PREFIX = "metric-pulse-v1:";

    private MetricModulationText() {}

    private static final class SupportedPulses {
        private static final double[] BASES = {.125, .25, .5, 1, 2, 4};
        private static final double[] FACTORS = {1, 1.5, 1.75};
    }

    public record Pulses(double leftQuarterBeats, double rightQuarterBeats) {
        public Pulses {
            if (!supported(leftQuarterBeats) || !supported(rightQuarterBeats))
                throw new IllegalArgumentException("Unsupported metric pulse value");
        }

        public String encode() {
            return PREFIX + leftQuarterBeats + ":" + rightQuarterBeats;
        }

        public double ratio() {
            return rightQuarterBeats / leftQuarterBeats;
        }
    }

    private static boolean supported(double value) {
        if (!Double.isFinite(value)) return false;
        for (double base : SupportedPulses.BASES)
            for (double factor : SupportedPulses.FACTORS) if (value == base * factor) return true;
        return false;
    }

    public static Optional<Pulses> decode(String encoded) {
        if (encoded == null || !encoded.startsWith(PREFIX)) return Optional.empty();
        try {
            var values = encoded.substring(PREFIX.length()).split(":");
            if (values.length != 2) return Optional.empty();
            return Optional.of(
                    new Pulses(Double.parseDouble(values[0]), Double.parseDouble(values[1])));
        } catch (IllegalArgumentException error) {
            return Optional.empty();
        }
    }

    public static Optional<Pulses> parse(String printed) {
        if (printed == null) return Optional.empty();
        var parts = printed.trim().split("=");
        if (parts.length != 2) return Optional.empty();
        double left = pulse(parts[0]), right = pulse(parts[1]);
        return supported(left) && supported(right)
                ? Optional.of(new Pulses(left, right))
                : Optional.empty();
    }

    private static double pulse(String raw) {
        String text = raw.strip().toLowerCase(Locale.ROOT).replace('-', ' ');
        int dots = 0;
        if (text.startsWith("double dotted ")) {
            dots = 2;
            text = text.substring(14);
        } else if (text.startsWith("dotted ")) {
            dots = 1;
            text = text.substring(7);
        } else
            while (text.endsWith(".") || text.endsWith("·")) {
                if (++dots > 2) return Double.NaN;
                text = text.substring(0, text.length() - 1).strip();
            }
        double base =
                switch (text) {
                    case "whole", "𝅝" -> 4;
                    case "half", "𝅗𝅥" -> 2;
                    case "quarter", "♩", "𝅘𝅥" -> 1;
                    case "eighth", "♪", "𝅘𝅥𝅮" -> .5;
                    case "sixteenth", "𝅘𝅥𝅯" -> .25;
                    case "32nd", "thirty second", "𝅘𝅥𝅰" -> .125;
                    default -> Double.NaN;
                };
        return base * (dots == 0 ? 1 : dots == 1 ? 1.5 : 1.75);
    }
}
