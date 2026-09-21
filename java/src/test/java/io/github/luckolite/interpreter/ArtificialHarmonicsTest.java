// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original raster drawings, not extracted score material. */
public class ArtificialHarmonicsTest {
    private static final int W=240,H=240,X=100,Y=160,G=20;
    private final byte[] gray=new byte[W*H];
    public ArtificialHarmonicsTest(){Arrays.fill(gray,(byte)255);}
    private void shape(boolean diamond) {
        for(int y=Y-45;y<=Y-15;y++)for(int x=X-15;x<=X+15;x++) {
            double r=diamond?(Math.abs(x-X)+Math.abs(y-(Y-30)))/12d:
                    Math.sqrt(Math.pow((x-X)/12d,2)+Math.pow((y-(Y-30))/7d,2));
            if(r>=.65&&r<=1.1)gray[y*W+x]=0;
        }
        for(int y=Y-7;y<=Y+7;y++)for(int x=X-11;x<=X+11;x++)
            if(Math.pow((x-X)/11d,2)+Math.pow((y-Y)/7d,2)<=1)gray[y*W+x]=0;
        for(int y=Y-42;y<=Y;y++)for(int x=X+9;x<=X+11;x++)gray[y*W+x]=0;
    }
    private ScoreNoteEvent note(int step,float y) {
        return new ScoreNoteEvent(0,X/(float)W,step,0,1,y/H,false,0,1,
                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,0,0,0,0,ScoreNoteEvent.CLEF_TREBLE);
    }
    private List<ScoreNoteEvent> apply(List<ScoreNoteEvent> notes) {
        return ArtificialHarmonics.apply(gray,W,H,List.of(new MeasureRegion(0,1,0,1)),notes,
                List.of(new PlayingTechniqueDetector.Staff(80,160,G,0,1)));
    }
    @Test public void fourthDiamondSoundsTwoOctavesAboveStoppedNote() {
        shape(true);var n=note(0,Y);var result=apply(List.of(n));
        assertEquals(2,result.get(0).octaveShift());assertEquals(n.beamCount(),result.get(0).beamCount());
    }
    @Test public void recognizedTouchHeadDoesNotBecomeASecondAttack() {
        shape(true);var result=apply(List.of(note(0,Y),note(3,Y-30)));
        assertEquals(1,result.size());assertEquals(2,result.get(0).octaveShift());
    }
    private void downStem(boolean diamond) {
        shape(diamond);
        for(int y=Y-24;y<=Y-8;y++)for(int x=X+6;x<=X+19;x++)gray[y*W+x]=(byte)255;
        for(int y=Y-30;y<=Y+32;y++)for(int x=X-11;x<=X-9;x++)gray[y*W+x]=0;
    }
    @Test public void downStemFourthSoundsTwoOctavesAboveStoppedNote() {
        downStem(true);var result=apply(List.of(note(0,Y),note(3,Y-30)));
        assertEquals(1,result.size());assertEquals(2,result.get(0).octaveShift());
    }
    @Test public void downStemOrdinaryHollowChordIsRetained() {
        downStem(false);var notes=List.of(note(0,Y),note(3,Y-30));
        assertEquals(notes,apply(notes));
    }
    @Test public void interruptedDownStemDoesNotProveHarmonic() {
        downStem(true);
        for(int y=Y+8;y<=Y+20;y++)for(int x=X-19;x<=X-6;x++)gray[y*W+x]=(byte)255;
        var notes=List.of(note(0,Y),note(3,Y-30));assertEquals(notes,apply(notes));
    }
    @Test public void detachedFingeringAboveDownStemDoesNotTranspose() {
        downStem(true);
        for(int y=Y-20;y<=Y-8;y++)for(int x=X-19;x<=X-6;x++)gray[y*W+x]=(byte)255;
        var notes=List.of(note(0,Y));assertEquals(notes,apply(notes));
    }
    @Test public void hollowStoppedFourthNeedsStrongerEvidence() {
        downStem(true);var hollow=new ScoreNoteEvent(0,X/(float)W,0,0,1,Y/(float)H,
                false,0,0,ScoreNoteEvent.ACCIDENTAL_FROM_KEY,ScoreNoteEvent.DURATION_HALF,
                0,0,0,ScoreNoteEvent.CLEF_TREBLE);
        var notes=List.of(hollow,note(3,Y-30));assertEquals(notes,apply(notes));
    }
    @Test public void ordinaryHollowChordHeadIsNotAHarmonic() {
        shape(false);var notes=List.of(note(0,Y),note(3,Y-30));assertEquals(notes,apply(notes));
    }
    @Test public void detachedDiamondIsInsufficient() {
        shape(true);for(int y=Y-24;y<=Y-4;y++)for(int x=X+6;x<=X+19;x++)gray[y*W+x]=(byte)255;
        var notes=List.of(note(0,Y));assertEquals(notes,apply(notes));
    }
    @Test public void unrelatedChordToneIsRetained() {
        shape(true);var other=note(-4,Y+40);assertTrue(apply(List.of(note(0,Y),other)).contains(other));
    }
    @Test public void staffLineCanCrossHollowDiamond() {
        shape(true);for(int x=20;x<220;x++)gray[(Y-30)*W+x]=0;
        assertEquals(2,apply(List.of(note(0,Y))).get(0).octaveShift());
    }
    @Test public void missingRawImageDoesNotChangeNotes() {
        var notes=List.of(note(0,Y));assertEquals(notes,ArtificialHarmonics.apply(null,W,H,List.of(),notes,List.of()));
    }
    @Test public void existingOctaveShiftIsNotStacked() {
        shape(true);var notes=List.of(note(0,Y).withOctaveShift(1));assertEquals(notes,apply(notes));
    }
    @Test public void pixelsAreUnchanged() {
        shape(true);byte[] original=gray.clone();apply(List.of(note(0,Y)));assertArrayEquals(original,gray);
    }
}
