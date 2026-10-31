// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic effective-key edits, independent of any application model or score. */
public class PreparedKeyTieContextTest {
    private static ScoreNoteEvent note(int measure,float position,int accidental,boolean tie) {
        return new ScoreNoteEvent(measure,position,1,0,1,.4f,tie,0,2,accidental,0)
                .withClef(ScoreNoteEvent.CLEF_TREBLE);
    }
    private static List<ScoreNoteEvent> notes() {
        return List.of(note(1,.15f,ScoreNoteEvent.ACCIDENTAL_FROM_KEY,false),note(1,.65f,1,true));
    }
    @Test public void effectiveEditedKeyControlsTheContradiction() {
        var source=notes();
        var natural=ScoreTiePitchGuard.recheckWithKeyContext(source,List.of(new ScoreKeyChange(0,0)));
        assertFalse(natural.get(1).tiedFromPrevious());
        assertEquals(note(1,.65f,1,false),natural.get(1));
        assertSame(source.get(0),natural.get(0));assertTrue(source.get(1).tiedFromPrevious());
        assertSame(source,ScoreTiePitchGuard.recheckWithKeyContext(source,List.of(new ScoreKeyChange(0,1))));
    }
    @Test public void effectiveLaterKeyOverridesTheEditedOpening() {
        var source=notes();var keys=List.of(new ScoreKeyChange(0,0),new ScoreKeyChange(1,1));
        assertSame(source,ScoreTiePitchGuard.recheckWithKeyContext(source,keys));
        assertEquals(List.of(new ScoreKeyChange(0,0),new ScoreKeyChange(1,1)),keys);
    }
    @Test public void unavailableKeyDoesNotInventAContradiction() {
        var source=notes();assertSame(source,ScoreTiePitchGuard.recheckWithKeyContext(source,List.of()));
    }
}
