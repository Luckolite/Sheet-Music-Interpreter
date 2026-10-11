package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic bars; no commercial score geometry or content. */
public class LegacyMeterRepairTest {
    static ScorePageInterpretation score(int beats, int bars, int numerator, int denominator) {
        var measures = new ArrayList<MeasureRegion>();
        var notes = new ArrayList<ScoreNoteEvent>();
        for (int bar = 0; bar < bars; bar++) {
            measures.add(new MeasureRegion(.1f, .9f, .2f, .4f));
            for (int beat = 0; beat < beats; beat++)
                notes.add(new ScoreNoteEvent(bar, (beat + .1f) / beats, beat, 0, 1, .3f,
                        false, 0, 0, ScoreNoteEvent.ACCIDENTAL_FROM_KEY, 1));
        }
        return new ScorePageInterpretation(measures, notes, 7,
                List.of(new ScoreKeyChange(0, 2)), List.of(new ScoreTempoChange(0, 0, 90)),
                List.of(new ScoreMeterChange(0, numerator, denominator)));
    }

    @Test public void repeatedFourBeatBarsRejectFalseHalfTimeWithoutChangingNotes() {
        var original = score(4, 3, 1, 2);
        var fixed = MeterChangeDetector.repairLegacyInterpretation(original, false);
        assertTrue(fixed.meterChanges().isEmpty());
        assertEquals(original.notes(), fixed.notes());
        assertEquals(original.measures(), fixed.measures());
        assertEquals(original.firstMeasureNumber(), fixed.firstMeasureNumber());
        assertEquals(original.keyChanges(), fixed.keyChanges());
        assertEquals(original.tempoChanges(), fixed.tempoChanges());
        assertEquals(original.rests(), fixed.rests());
        assertEquals(original.techniqueChanges(), fixed.techniqueChanges());
        assertEquals(original.dynamicChanges(), fixed.dynamicChanges());
        assertEquals(original.playbackDirections(), fixed.playbackDirections());
        assertEquals(original.expressiveEvents(), fixed.expressiveEvents());
        assertEquals(List.of(new ScoreMeterChange(0, 1, 2)), original.meterChanges());
    }

    @Test public void capturedTimingIsAuthoritative() {
        var original = score(4, 3, 1, 2);
        assertSame(original, MeterChangeDetector.repairLegacyInterpretation(original, true));
    }

    @Test public void genuineHalfTimeIsPreserved() {
        var original = score(2, 3, 1, 2);
        assertSame(original, MeterChangeDetector.repairLegacyInterpretation(original, false));
    }

    @Test public void singleContraryBarCannotRewriteAMeter() {
        var original = score(4, 1, 1, 2);
        assertSame(original, MeterChangeDetector.repairLegacyInterpretation(original, false));
    }

    @Test public void ordinaryMetersAreNotInferredFromRhythmTotals() {
        var original = score(4, 3, 3, 4);
        assertSame(original, MeterChangeDetector.repairLegacyInterpretation(original, false));
    }

    @Test public void laterChangeBoundsTheEvidenceWindow() {
        var original = score(4, 3, 1, 2);
        var bounded = new ScorePageInterpretation(original.measures(), original.notes(), 7,
                original.keyChanges(), original.tempoChanges(),
                List.of(new ScoreMeterChange(0, 1, 2), new ScoreMeterChange(1, 4, 4)));
        assertSame(bounded, MeterChangeDetector.repairLegacyInterpretation(bounded, false));
    }
}
