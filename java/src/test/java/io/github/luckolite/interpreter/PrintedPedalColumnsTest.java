// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

public class PrintedPedalColumnsTest {
    static ScoreNoteEvent note(float x) {
        return new ScoreNoteEvent(
                0,
                (x - 40) / 320,
                0,
                0,
                1,
                .32f,
                false,
                0,
                0,
                2,
                1,
                1,
                0,
                0,
                18,
                false,
                0,
                false,
                0,
                0,
                1,
                0,
                ScoreNoteEvent.Kind.PITCHED);
    }

    static boolean locate(List<ScoreNoteEvent> notes, int kind, boolean rail, int left, int right) {
        var gray = new byte[400 * 250];
        Arrays.fill(gray, (byte) 255);
        if (rail) for (int y = 60; y <= 100; y++) gray[y * 400 + 360] = 0;
        var score =
                new ScorePageInterpretation(List.of(new MeasureRegion(.1f, .9f, .1f, .5f)), notes);
        var mark =
                new PrintedPedalGlyphs.OwnedMark(
                        new PrintedPedalGlyphs.Mark(kind, left, 130, right, 150, .8f),
                        new PlayingTechniqueDetector.Staff(60, 100, 10, 0, 1));
        return PrintedPedalColumns.locate(mark, score, gray, 400, 250).hook().isPresent();
    }

    @org.junit.Test
    public void originalColumnControls() {
        if (!locate(List.of(note(100), note(100.4f)), 1, false, 90, 130))
            throw new AssertionError("chord jitter");
        if (locate(List.of(note(100), note(120)), 1, false, 90, 130))
            throw new AssertionError("two attacks collapsed");
        if (locate(List.of(note(160)), 1, false, 90, 130))
            throw new AssertionError("remote note borrowed");
        if (!locate(List.of(note(100)), 2, true, 337, 357)) throw new AssertionError("bar release");
        if (locate(List.of(note(100)), 2, false, 337, 357))
            throw new AssertionError("missing bar accepted");
        if (locate(List.of(note(100)), 2, true, 300, 320))
            throw new AssertionError("interior star forced to end");
        System.out.println(
                "PASS 6 original column controls: chord jitter, distinct attacks, remote note, raw bar release, missing bar, interior star");
    }
}
