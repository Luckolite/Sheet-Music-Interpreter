// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;

/** Full private caller-path original: score identity + raw ring + labeled head + gray stem. */
public final class PrintedNaturalHarmonicDetectorOriginal {
    private static final int W = 96, H = 80;
    private static final byte HEAD = 2;

    private record Scene(byte[] gray, byte[] labels) {}

    private static ScoreNoteEvent note() {
        float x = 40f / W, y = 50f / H;
        float position = (x - .1f) / .8f;
        return new ScoreNoteEvent(
                0,
                position,
                2,
                0,
                1,
                y,
                false,
                0,
                0,
                0,
                2f,
                1,
                0,
                0,
                28,
                false,
                0,
                false,
                0,
                0,
                1,
                0,
                ScoreNoteEvent.Kind.PITCHED);
    }

    private static Scene scene(boolean stem, boolean foreign, boolean ring, int ringInk) {
        byte[] gray = new byte[W * H], labels = new byte[W * H];
        java.util.Arrays.fill(gray, (byte) 248);
        for (int y = 46; y <= 54; y++)
            for (int x = 35; x <= 45; x++)
                if (((x - 40) / 5.0) * ((x - 40) / 5.0) + ((y - 50) / 4.0) * ((y - 50) / 4.0)
                        <= 1) {
                    labels[y * W + x] = HEAD;
                    gray[y * W + x] = 20;
                }
        if (stem) for (int y = 39; y <= 50; y++) gray[y * W + 45] = 25;
        if (foreign) gray[46 * W + 35] = 25;
        if (ring)
            for (int y = 22; y <= 34; y++)
                for (int x = 37; x <= 49; x++) {
                    int d = (x - 43) * (x - 43) + (y - 28) * (y - 28);
                    if (Math.abs(d - 25) <= 5) gray[y * W + x] = (byte) ringInk;
                }
        return new Scene(gray, labels);
    }

    private static Scene withSecondRing(Scene source) {
        byte[] gray = source.gray.clone(), labels = source.labels.clone();
        for (int y = 35; y <= 47; y++)
            for (int x = 37; x <= 49; x++) {
                int d = (x - 43) * (x - 43) + (y - 41) * (y - 41);
                if (Math.abs(d - 25) <= 5) gray[y * W + x] = 80;
            }
        return new Scene(gray, labels);
    }

    private static ScorePageInterpretation score(
            List<ScoreNoteEvent> notes, List<ScoreExpressiveEvent> events) {
        return new ScorePageInterpretation(
                List.of(new MeasureRegion(.1f, .9f, 0, 1)),
                notes,
                1,
                List.of(),
                List.of(),
                List.of(new ScoreMeterChange(0, 4, 4)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                events,
                ScoreSlurSource.empty(notes.size()));
    }

    private static ScorePageInterpretation apply(
            Scene s,
            List<ScoreNoteEvent> notes,
            List<PlayingTechniqueDetector.Word> words,
            float opening) {
        var staffs = List.of(new PlayingTechniqueDetector.Staff(0, H, 10, 0, 1));
        return PrintedNaturalHarmonicDetector.apply(
                score(notes, List.of()), s.labels, s.gray, W, H, staffs, words, 0, opening);
    }

    private static void check(boolean b, String m) {
        if (!b) throw new AssertionError(m);
    }

    private static void checkBoundedPageWorkPlan() {
        var searches = new java.util.ArrayList<NaturalHarmonicRingProof.Search>();
        var identities = new java.util.ArrayList<NaturalHarmonicRingProof.NoteIdentity>();
        for (int n = 0; n < 196; n++) {
            int x = 100 + (n % 16) * 110, y = 100 + (n / 16) * 210;
            var box = new NaturalHarmonicRingProof.Box(x - 15, y - 40, x + 15, y + 40);
            searches.add(new NaturalHarmonicRingProof.Search(box, 5, 18));
            identities.add(
                    new NaturalHarmonicRingProof.NoteIdentity(
                            "page-note-" + n, n, 0, 1, n % 9, x, y, 10, 2, 2));
        }
        var plan = NaturalHarmonicRingProof.pageWorkPlan(2048, 2896, searches, identities);
        check(
                plan.unionPixels() > 0
                        && plan.unionPixels() <= 2_000_000
                        && plan.workLimit() > 2_000_000
                        && plan.workLimit() <= 64_000_000,
                "page-scale work must use bounded unique-union planning, not a fixed 2M cliff");
        var budget = new NaturalHarmonicRingProof.Budget(plan.workLimit(), 64 * 1024 * 1024);
        budget.consume(identities.size(), identities.size() * 64);
        check(
                budget.workRemaining() == plan.workLimit() - identities.size(),
                "identity materialization is charged against the derived page budget");
    }

    public static void main(String[] args) {
        checkBoundedPageWorkPlan();
        var note = note();
        var positive = apply(scene(true, false, true, 80), List.of(note), List.of(), 4);
        check(
                positive.expressiveEvents().size() == 1,
                "complete raw ring/head/stem should emit one event");
        var event = positive.expressiveEvents().get(0);
        check(
                event.kind() == ScoreExpressiveEvent.Kind.NATURAL_HARMONIC
                        && event.scope() == ScoreExpressiveEvent.Scope.NOTE
                        && event.start().isPresent()
                        && event.targetEventId()
                                .orElseThrow()
                                .equals(ScoreNaturalHarmonicNotation.target(note)),
                "emitted mark must own the exact source note");
        check(
                event.start().orElseThrow().quarterBeatOffset() > 0,
                "anchor must come from written timing, not synthetic beat zero");
        var deferred = apply(scene(true, false, true, 80), List.of(note), List.of(), Float.NaN);
        check(
                deferred.expressiveEvents().size() == 1,
                "page caller must retain a proved physical owner without inventing page meter");
        var unresolved = deferred.expressiveEvents().get(0);
        check(
                unresolved.scope() == ScoreExpressiveEvent.Scope.UNRESOLVED
                        && unresolved.start().isEmpty()
                        && ScoreNaturalHarmonicNotation.owner(unresolved, deferred.notes()) == 0,
                "late transport must preserve the exact unique note owner and unresolved clock");
        check(
                apply(scene(false, false, true, 80), List.of(note), List.of(), 4)
                        .expressiveEvents()
                        .isEmpty(),
                "ring without raw attached stem refuses");
        check(
                apply(scene(true, true, true, 80), List.of(note), List.of(), 4)
                        .expressiveEvents()
                        .isEmpty(),
                "unowned dark foreign body pixel refuses");
        check(
                apply(
                                scene(true, false, true, 80),
                                List.of(note),
                                List.of(
                                        new PlayingTechniqueDetector.Word(
                                                "o", 35f / W, 22f / H, 49f / W, 34f / H)),
                                4)
                        .expressiveEvents()
                        .isEmpty(),
                "OCR o/0 word collision refuses");
        check(
                apply(scene(true, false, false, 80), List.of(note), List.of(), 4)
                        .expressiveEvents()
                        .isEmpty(),
                "missing ring refuses");
        var competitor =
                new ScoreNoteEvent(
                        0,
                        note.positionInMeasure(),
                        3,
                        0,
                        1,
                        note.pageY(),
                        false,
                        0,
                        0,
                        0,
                        2f,
                        1,
                        0,
                        0,
                        28,
                        false,
                        0,
                        false,
                        0,
                        0,
                        1,
                        0,
                        ScoreNoteEvent.Kind.PITCHED);
        check(
                apply(scene(true, false, true, 80), List.of(note, competitor), List.of(), 4)
                        .expressiveEvents()
                        .isEmpty(),
                "competing same-column head refuses");
        check(
                apply(withSecondRing(scene(true, false, true, 80)), List.of(note), List.of(), 4)
                        .expressiveEvents()
                        .isEmpty(),
                "multiple complete rings competing for one note refuse");
        System.out.println("9 private full-caller harmonic acquisition/timing originals PASS");
    }
}
