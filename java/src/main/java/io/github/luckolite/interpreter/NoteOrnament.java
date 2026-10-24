package io.github.luckolite.interpreter;

/** Per-note performance metadata stored alongside articulation; never part of written rhythm. */
public final class NoteOrnament {
    public static final int NONE = 0,
            TRILL = 1 << 5,
            TURN = 2 << 5,
            INVERTED_TURN = 3 << 5,
            MORDENT = 4 << 5,
            INVERTED_MORDENT = 5 << 5,
            SLIDE = 6 << 5,
            GLISSANDO = 7 << 5;
    public static final int TYPE_MASK = 7 << 5, DELAYED = 1 << 8;

    /**
     * Type-specific qualifier: a proved straight isolated SLIDE approach. The same wire bit means
     * DELAYED only for TURN / INVERTED_TURN. Connected slides and other ornament types must not
     * interpret this alias.
     */
    public static final int STRAIGHT_APPROACH = 1 << 8;

    /** Small printed grace heads borrow playback time from their principal note. */
    public static final int GRACE = 1 << 15;

    public static final int FROM_ABOVE = 1 << 13, FROM_PREVIOUS = 1 << 14;
    private static final int UPPER_SHIFT = 9, LOWER_SHIFT = 11;
    private static final int TREMOLO_SHIFT = 16, TREMOLO_MASK = 7 << TREMOLO_SHIFT;

    /** Number of beams in the performed subdivision; the printed note keeps its duration. */
    public static int tremoloBeams(int marks) {
        int beams = (marks & TREMOLO_MASK) >>> TREMOLO_SHIFT;
        return beams <= 4 ? beams : 0;
    }

    public static int withTremolo(int marks, int beams) {
        return (marks & ~TREMOLO_MASK) | (Math.max(0, Math.min(4, beams)) << TREMOLO_SHIFT);
    }

    public static double tremoloBeats(int marks) {
        int beams = tremoloBeams(marks);
        return beams == 0 ? 0 : 1.0 / (1 << beams);
    }

    public static final int ALL =
            TREMOLO_MASK
                    | GRACE
                    | TYPE_MASK
                    | DELAYED
                    | FROM_ABOVE
                    | FROM_PREVIOUS
                    | (3 << UPPER_SHIFT)
                    | (3 << LOWER_SHIFT);

    private NoteOrnament() {}

    public static int type(int marks) {
        return marks & TYPE_MASK;
    }

    public static boolean straightApproach(int marks) {
        return type(marks) == SLIDE
                && (marks & FROM_PREVIOUS) == 0
                && (marks & STRAIGHT_APPROACH) != 0;
    }

    public static int accidental(int marks, boolean upper) {
        int value = (marks >> (upper ? UPPER_SHIFT : LOWER_SHIFT)) & 3;
        return value == 0 ? ScoreNoteEvent.ACCIDENTAL_FROM_KEY : value - 2;
    }

    public static int withAccidental(int marks, boolean upper, int accidental) {
        int shift = upper ? UPPER_SHIFT : LOWER_SHIFT;
        int value =
                accidental == ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                        ? 0
                        : Math.max(1, Math.min(3, accidental + 2));
        return (marks & ~(3 << shift)) | (value << shift);
    }

    public static String name(int marks) {
        return switch (type(marks)) {
            case TRILL -> "trill";
            case TURN -> "turn";
            case INVERTED_TURN -> "inverted_turn";
            case MORDENT -> "mordent";
            case INVERTED_MORDENT -> "inverted_mordent";
            case SLIDE -> "slide";
            case GLISSANDO -> "glissando";
            default -> "none";
        };
    }
}
