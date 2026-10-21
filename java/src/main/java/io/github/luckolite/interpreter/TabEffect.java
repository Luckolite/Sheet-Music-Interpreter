// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Guitar performance data in unused articulation bits; written rhythm is unchanged. */
public final class TabEffect {
    public static final int SLIDE = 1,
            HAMMER = 2,
            PULL = 3,
            BEND = 4,
            BEND_RELEASE = 5,
            DEAD = 6,
            HARMONIC = 7,
            TAP = 8,
            MICRO_BEND = 9,
            MICRO_BEND_RELEASE = 10,
            BAR = 11,
            BAR_RELEASE = 12,
            STRUM_DOWN = 13,
            STRUM_UP = 14,
            PRE_BEND = 15;
    public static final int VIBRATO = 1 << 29, PALM_MUTE = 1 << 30, ALL = 0x7ff80000;

    private TabEffect() {}

    public static int kind(int marks) {
        return (marks >>> 19) & 15;
    }

    public static int delta(int marks) {
        return ((marks >>> 23) & 63) - 32;
    }

    public static int encode(int kind, int semitones) {
        if (kind < 1
                || kind > 15
                || semitones < -24
                || semitones > 24
                || (kind == STRUM_DOWN || kind == STRUM_UP) && (semitones < 0 || semitones > 6))
            throw new IllegalArgumentException("Unsupported guitar effect");
        return (kind << 19) | ((semitones + 32) << 23);
    }

    /** New pitch kinds use half-semitone units; legacy integer encodings retain their meaning. */
    public static int encodePitch(int kind, double semitones) {
        if (!Double.isFinite(semitones)
                || Math.abs(semitones) > 12
                || Math.rint(semitones * 2) != semitones * 2)
            throw new IllegalArgumentException(
                    "Expected a half-semitone bend within twelve semitones");
        if (kind == BEND || kind == BEND_RELEASE) {
            if (Math.rint(semitones) == semitones) return encode(kind, (int) semitones);
            kind = kind == BEND ? MICRO_BEND : MICRO_BEND_RELEASE;
        }
        if (kind != MICRO_BEND
                && kind != MICRO_BEND_RELEASE
                && kind != BAR
                && kind != BAR_RELEASE
                && kind != PRE_BEND) throw new IllegalArgumentException("Expected a pitch gesture");
        return encode(kind, (int) Math.round(semitones * 2));
    }

    public static double semitones(int marks) {
        int k = kind(marks);
        if (k == 0 || k == STRUM_DOWN || k == STRUM_UP) return 0;
        return delta(marks) / (k >= MICRO_BEND && k <= BAR_RELEASE || k == PRE_BEND ? 2.0 : 1.0);
    }

    public static int strumIndex(int marks) {
        return kind(marks) == STRUM_DOWN || kind(marks) == STRUM_UP ? delta(marks) : 0;
    }

    /** A short spread changes attacks only; note releases and the written clock stay fixed. */
    public static double attackDelay(int marks, double duration) {
        return strumIndex(marks) * Math.min(.024, Math.max(0, duration) / 14);
    }

    public static String name(int marks) {
        return switch (kind(marks)) {
            case SLIDE -> "slide";
            case HAMMER -> "hammer_on";
            case PULL -> "pull_off";
            case BEND, MICRO_BEND -> "bend";
            case BEND_RELEASE, MICRO_BEND_RELEASE -> "bend_release";
            case BAR -> "whammy";
            case BAR_RELEASE -> "whammy_release";
            case STRUM_DOWN -> "strum_down";
            case STRUM_UP -> "strum_up";
            case PRE_BEND -> "pre_bend";
            case DEAD -> "dead";
            case HARMONIC -> "harmonic";
            case TAP -> "tap";
            default -> "none";
        };
    }

    public static double pitchOffset(int marks, double age, double duration) {
        double t = Math.max(0, Math.min(1, age / Math.max(.001, duration))), offset = 0;
        int k = kind(marks);
        if (k == SLIDE) offset = semitones(marks) * (1 - smooth(Math.min(1, t / .3)));
        if (k == BEND || k == MICRO_BEND || k == BAR)
            offset = semitones(marks) * smooth(Math.min(1, t / .4));
        if (k == BEND_RELEASE || k == MICRO_BEND_RELEASE || k == BAR_RELEASE)
            offset = semitones(marks) * (t < .5 ? smooth(t / .5) : 1 - smooth((t - .5) / .5));
        if (k == PRE_BEND) offset = semitones(marks);
        if ((marks & VIBRATO) != 0)
            offset += .18 * Math.sin(Math.max(0, age) * Math.PI * 10) * Math.min(1, t * 8);
        return offset;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    public static double gate(int marks) {
        return kind(marks) == DEAD ? .12 : (marks & PALM_MUTE) != 0 ? .45 : 1;
    }

    public static double gain(int marks) {
        return switch (kind(marks)) {
            case DEAD -> .25;
            case HAMMER, PULL, TAP -> .78;
            default -> 1;
        };
    }

    public static int harmonicOffset(int fret) {
        return switch (fret) {
            case 12 -> 12;
            case 7, 19 -> 19;
            case 5, 24 -> 24;
            default -> -1;
        };
    }
}
