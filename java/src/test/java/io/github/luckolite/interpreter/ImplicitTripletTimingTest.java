// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ImplicitTripletTimingTest {
    private List<ScoreNoteEvent> run(int count,int beams) {
        var notes=new ArrayList<ScoreNoteEvent>();
        for(int i=0;i<count;i++)notes.add(new ScoreNoteEvent(1,.04f+i*.90f/(count-1),i%7,0,1,.5f,
                false,0,beams,ScoreNoteEvent.ACCIDENTAL_FROM_KEY,0,1));
        return notes;
    }
    @Test public void twelveUnnumberedEighthsPlayAsFourEvenTriplets() {
        var notes=run(12,1);
        for(int i=0;i<12;i++) {
            assertEquals(i/3d,ScoreNoteTiming.beatInMeasure(notes.get(i),notes,4),.0001);
            assertEquals(1/3d,ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(i),notes,4),.0001);
        }
    }
    @Test public void twentyFourSixteenthsFillFourBeats() {
        var notes=run(24,2);assertEquals(1/6d,ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(10),notes,4),.0001);
    }
    @Test public void ordinarySixFourDoesNotBecomeTriplets() {
        var notes=run(12,1);assertEquals(.5,ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(5),notes,6),.0001);
    }
    @Test public void missingNotesAreNotNormalizedIntoTriplets() {
        var notes=run(11,1);assertNotEquals(1/3d,ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(5),notes,4),.0001);
    }
    @Test public void writtenSilenceBlocksTheInference() {
        var notes=run(12,1);notes.set(0,notes.get(0).withLeadingRest(.5f));
        assertEquals(.5,ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(5),notes,4),.0001);
    }
    @Test public void anInsetPartialBarIsNotAssumedComplete() {
        var notes=new ArrayList<ScoreNoteEvent>();
        for(var n:run(12,1))notes.add(new ScoreNoteEvent(1,.3f+n.positionInMeasure()*.65f,n.staffStep(),0,1,.5f,false,0,1,2,0,1));
        assertEquals(.5,ScoreNoteTiming.resolvedWrittenDurationBeats(notes.get(5),notes,4),.0001);
    }
}
