// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original numeral and beam geometry; no score samples. */
public class TupletSystemOwnershipTest {
    private List<ScoreNoteEvent> detect(boolean otherSystem,boolean above) {
        byte[] gray=SeparateBeamGroupsTest.ink(true,false),digits=new byte[gray.length];
        Arrays.fill(digits,(byte)255);SeparateBeamGroupsTest.numeral(digits);
        int shift=above?0:115;
        for(int y=60;y<82;y++)for(int x=180;x<192;x++)
            if(digits[y*400+x]==0)gray[(y+shift)*400+x]=0;
        var notes=new ArrayList<ScoreNoteEvent>();
        for(float x:new float[]{156,186,216})
            notes.add(new ScoreNoteEvent(0,x/400,0,0,1,140f/240,false,0,1,2,0,1));
        var regions=new ArrayList<MeasureRegion>();regions.add(new MeasureRegion(0,1,.2f,.6f));
        if(otherSystem)regions.add(new MeasureRegion(0,1,.72f,.98f));
        return TripletRhythmDetector.apply(notes,regions,gray,400,240);
    }
    @Test public void lowerSystemsFingerNumberCannotScaleThePreviousRow() {
        assertTrue(detect(true,false).stream().allMatch(n->n.tupletDivisor()==1));
    }
    @Test public void belowStaffTripletWithoutAnotherSystemIsPreserved() {
        assertTrue(detect(false,false).stream().allMatch(n->n.tupletDivisor()==3));
    }
    @Test public void currentRowsTripletIsPreservedBesideAnotherSystem() {
        assertTrue(detect(true,true).stream().allMatch(n->n.tupletDivisor()==3));
    }
}
